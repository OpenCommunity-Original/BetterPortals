package org.envel.betterportals.proxy.net;

import org.envel.betterportals.shared.net.Response;
import org.envel.betterportals.shared.net.requests.Request;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.Socket;
import java.util.function.Consumer;

/**
 * Represents each sub-server connected to the proxy that is registered with the plugin
 */
public interface IClientHandler {
    /**
     * @return The game version of the connected server, or null if the server hasn't completed the handshake.
     */
    @Nullable String getGameVersion();

    /**
     * @return The name of the server that this client is connected to
     */
    @Nullable String getServerName();

    /**
     * Safely shuts down the connection to the server by sending a disconnection notice. Called on portal server shutdown.
     * Does nothing if already disconnected
     */
    void shutDown();

    /**
     * Sends <code>request</code> to this server to be processed.
     * @param request The request to send.
     * @param onFinish Called with the response when it is received.
     */
    void sendRequest(@NotNull Request request, @NotNull Consumer<Response> onFinish);

    interface Factory {
        IClientHandler create(Socket socket);
    }
}
