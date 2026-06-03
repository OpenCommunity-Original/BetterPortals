package org.envel.betterportals.proxy.net;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.envel.betterportals.proxy.IProxy;
import org.envel.betterportals.shared.logging.Logger;
import org.envel.betterportals.shared.net.*;
import org.envel.betterportals.shared.net.encryption.EncryptedObjectStreamFactory;
import org.envel.betterportals.shared.net.encryption.IEncryptedObjectStream;
import org.envel.betterportals.shared.net.requests.Request;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.crypto.AEADBadTagException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ClientHandler implements IClientHandler {
    private final IPortalServer portalServer;
    private final Logger logger;
    private final EncryptedObjectStreamFactory encryptedObjectStreamFactory;
    private final IRequestHandler requestHandler;

    private final Socket socket;
    private IEncryptedObjectStream objectStream;

    @Getter private String serverName = null;
    @Getter private String gameVersion;

    private final IProxy proxy;

    private volatile boolean isRunning = true;

    private final AtomicInteger currentRequestId = new AtomicInteger();
    private final ConcurrentMap<Integer, Consumer<Response>> waitingRequests = new ConcurrentHashMap<>();

    @Inject
    public ClientHandler(@Assisted Socket socket, IPortalServer portalServer, Logger logger, EncryptedObjectStreamFactory encryptedObjectStreamFactory, IRequestHandler requestHandler, IProxy proxy) {
        this.socket = socket;
        this.portalServer = portalServer;
        this.logger = logger;
        this.encryptedObjectStreamFactory = encryptedObjectStreamFactory;
        this.requestHandler = requestHandler;
        this.proxy = proxy;

        new Thread(() -> {
            try {
                run();
            }   catch(IOException ex) {
                if (!isRunning) {
                    return;
                } // An IOException gets thrown if another thread shuts down this connection

                if(ex.getCause() instanceof AEADBadTagException) {
                    printEncryptionFailure();
                }   else    {
                    logger.warning("An IO error occurred while connected to %s", socket.getRemoteSocketAddress());
                    ex.printStackTrace();
                }
            }   catch(AEADBadTagException ex) {
                printEncryptionFailure();
            }   catch(Exception ex) {
                logger.warning("An error occurred while connected to %s", socket.getRemoteSocketAddress());
                ex.printStackTrace();
            }   finally     {
                disconnect();
            }
        }).start();
    }

    private void printEncryptionFailure() {
        logger.warning("Failed to initialise encryption with %s", socket.getRemoteSocketAddress());
        logger.warning("Please make sure that your encryption key is valid!");
    }

    /**
     * Reads a {@link Handshake} to get info about the server that is connecting, then sends a {@link HandshakeResponse} to tell the connecting server if the connection was successful.
     * @return If the handshake was successful
     */
    @SuppressWarnings("deprecation")
    private boolean performHandshake() throws IOException, ClassNotFoundException, GeneralSecurityException {
        logger.fine("Reading handshake . . .");
        Handshake handshake = (Handshake) objectStream.readObject();
        logger.fine("Handshake plugin version: %s. Handshake game version: %s", handshake.getPluginVersion(), handshake.getGameVersion());

        // The plugin version needs to be the same, since the protocol may have changed
        HandshakeResponse.Result result = HandshakeResponse.Result.SUCCESS;
        if(!proxy.getPluginVersion().equals(handshake.getPluginVersion())) {
            logger.warning("A server tried to register with a different plugin version (%s)", handshake.getPluginVersion());
            result = HandshakeResponse.Result.PLUGIN_VERSION_MISMATCH;
        }

        InetSocketAddress statedServerAddress = new InetSocketAddress(socket.getInetAddress(), handshake.getServerPort());

        // Find the bungeecord server that the connector is connecting from
        String serverName = handshake.getOverrideServerName();
        if(serverName != null) {
            logger.finer("Using manually stated server name %s", serverName);

            if(!proxy.serverExists(serverName)) {
                logger.warning("Server name %s was stated by a client, but no listed server existed with that name!", serverName);
                serverName = null;
            }
        }

        if(serverName == null) {
            logger.warning("Finding server info from socket address and port, this behaviour is deprecated!");
            serverName = proxy.findServer(statedServerAddress);
        }

        if(serverName == null) {
            logger.warning("A server tried to register that didn't exist on the proxy!");
            result = HandshakeResponse.Result.SERVER_NOT_REGISTERED;
        }

        HandshakeResponse response = new HandshakeResponse();
        response.setStatus(result);
        send(response);

        if(result == HandshakeResponse.Result.SUCCESS) {
            logger.fine("Successfully registered with server %s", serverName);
            logger.fine("Plugin version: %s. Game version: %s.", handshake.getPluginVersion(), handshake.getGameVersion());
            portalServer.registerServer(this, serverName);
            this.serverName = serverName;
            this.gameVersion = handshake.getGameVersion();
            return true;
        }   else    {
            return false;
        }
    }

    private void run() throws IOException, ClassNotFoundException, GeneralSecurityException    {
        objectStream = encryptedObjectStreamFactory.create(socket.getInputStream(), socket.getOutputStream());

        if(!performHandshake()) {
            return;
        }

        while(true) {
            Object next = objectStream.readObject();
            if (next instanceof DisconnectNotice) {
                logger.fine("Received disconnection notice, shutting down!");
                return;
            } else if (next instanceof Response) {
                processResponse((Response) next);
            } else if (next instanceof Request) {
                processRequest((Request) next);
            }
        }
    }

    /**
     * Sends <code>request</code> to the request handler and then sends the response with the correct ID.
     * @param request The request to process
     */
    private void processRequest(Request request) {
        // We don't just send the response directly, since it may take some time to process the request, and we need to be ready for more requests.
        int requestId = request.getId();
        requestHandler.handleRequest(request, (response) -> {
            response.setId(requestId); // Assign the correct request ID so that the client knows which request this response is for
            try {
                send(response);
            } catch (IOException | GeneralSecurityException ex) {
                logger.warning("IO Error occurred while sending a response to a request");
                ex.printStackTrace();
                disconnect();
            }
        });
    }

    /**
     * Sends <code>response</code> to the correct request in queue.
     * @param response The response to consume
     */
    private void processResponse(Response response) {
        Consumer<Response> waiter = waitingRequests.remove(response.getId());
        if(waiter == null) {
            throw new IllegalStateException("Received response for request that didn't exist");
        }

        waiter.accept(response);
    }

    @Override
    public void shutDown() {
        if(!isRunning) {return;}

        try {
            send(new DisconnectNotice());
        }   catch(IOException | GeneralSecurityException ex)   {
            logger.warning("Error occurred while sending disconnection notice to %s", socket.getRemoteSocketAddress());
        }
        disconnect();
    }

    /**
     * Closes the socket and unregisters this handler in this {@link IPortalServer}
     * Any waiting requests will receive a response with an error.
     */
    private void disconnect() {
        if(!isRunning) {return;}
        isRunning = false;

        portalServer.onServerDisconnect(this);
        try {
            socket.close();
        }   catch (IOException ex) {
            logger.warning("Error occurred while disconnecting from %s", socket.getRemoteSocketAddress());
            ex.printStackTrace();
        }

        // Send an error to all waiting requests
        Response disconnectResponse = new Response();
        disconnectResponse.setError(new RequestException("Client server connection disconnected while sending the request"));
        for(Consumer<Response> responseConsumer : waitingRequests.values()) {
            responseConsumer.accept(disconnectResponse);
        }
    }

    private synchronized void send(Object obj) throws IOException, GeneralSecurityException {
        objectStream.writeObject(obj);
    }

    private void verifyCanSendRequests() {
        if(serverName == null) {
            throw new IllegalStateException("Attempted to send request before handshake was finished");
        }
    }

    @Override
    public void sendRequest(@NotNull Request request, @NotNull Consumer<Response> onFinish) {
        verifyCanSendRequests();

        int requestId = currentRequestId.getAndIncrement();
        request.setId(requestId);
        waitingRequests.put(requestId, onFinish);

        try {
            send(request);
        }   catch(IOException | GeneralSecurityException ex)     {
            logger.warning("Client server connection disconnected while sending the request");

            disconnect();
        }
    }
}
