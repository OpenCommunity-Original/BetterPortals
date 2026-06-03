package org.envel.betterportals.proxy.net;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.envel.betterportals.proxy.IProxyConfig;
import org.envel.betterportals.shared.logging.Logger;
import org.envel.betterportals.shared.net.encryption.CipherManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class PortalServer implements IPortalServer {
    private final Logger logger;
    private final IProxyConfig config;
    private final IClientHandler.Factory serverHandlerFactory;

    private final Set<IClientHandler> connectedServers = new HashSet<>();
    private final Map<String, IClientHandler> registeredServers = new ConcurrentHashMap<>();

    private ServerSocket serverSocket;
    private volatile boolean isRunning = false;

    @Inject
    public PortalServer(Logger logger, CipherManager cipherManager, IProxyConfig config, IClientHandler.Factory serverHandlerFactory) throws Exception    {
        this.logger = logger;
        this.config = config;
        this.serverHandlerFactory = serverHandlerFactory;
        cipherManager.init(config.getKey());
    }

    @Override
    public void startUp() {
        if(isRunning) {
            throw new IllegalStateException("Attempted to start server when it was already running");
        }
        isRunning = true;

        logger.info("Starting up portal server");
        Thread serverThread = new Thread(() -> {
            logger.fine("Hello from server thread");
            try {
                runServer();
            }   catch(IOException ex)   {
                logger.fine("Caught IO Error on server thread");
                // An IOException is thrown if another thread shuts down the connection, e.g. on plugin unload
                if(!isRunning) {return;}

                logger.warning("An IO error occurred while running the portal server");
                ex.printStackTrace();
            }   catch(Exception ex) {
                logger.warning("An error occurred while running the portal server");
                ex.printStackTrace();
            }   finally     {
                shutDown();
            }
        });
        serverThread.setName("BetterPortals-PortalServer");
        serverThread.start();
    }

    private void runServer() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.bind(config.getBindAddress());

        while(isRunning) {
            logger.fine("Awaiting new connections");
            Socket next = serverSocket.accept();
            logger.fine("Received connection from %s", next.getRemoteSocketAddress());

            IClientHandler handler = serverHandlerFactory.create(next);
            connectedServers.add(handler);
        }
    }

    @Override
    public void shutDown() {
        if(!isRunning) {return;}

        logger.info("Shutting down portal server");
        isRunning = false;
        try {
            // The socket close error will be caught since we set isRunning to false.
            serverSocket.close();
            for(IClientHandler serverHandler : new java.util.ArrayList<>(connectedServers)) {
                serverHandler.shutDown();
            }
        }   catch(IOException ex) {
            logger.warning("An IO error occurred while shutting down the portal server");
            ex.printStackTrace();
        }
    }

    @Override
    public void registerServer(@NotNull IClientHandler serverHandler, @NotNull String serverName) {
        if(!connectedServers.contains(serverHandler)) {
            throw new IllegalArgumentException("Attempted to register server that wasn't connected");
        }

        registeredServers.put(serverName, serverHandler);
    }

    @Override
    public void onServerDisconnect(@NotNull IClientHandler handler) {
        connectedServers.remove(handler);
        String serverName = handler.getServerName();
        if(serverName != null) {
            logger.finer("Server %s disconnected from the portal server", serverName);
            registeredServers.remove(serverName);
        }   else    {
            logger.finer("Unregistered server disconnected from the portal server");
        }
    }

    @Override
    public @Nullable IClientHandler getServer(@NotNull String name) {
        return registeredServers.get(name);
    }
}
