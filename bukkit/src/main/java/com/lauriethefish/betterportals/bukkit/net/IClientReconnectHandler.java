package com.lauriethefish.betterportals.bukkit.net;

public interface IClientReconnectHandler {
    /**
     * Called if a player runs <code>/bp reconnect</code> in order to reconnect now.
     */
    void prematureReconnect();

    /**
     * Called if the portal client disconnects.
     */
    void onClientDisconnect();

    /**
     * Stops the reconnection task worker if it is currently running.
     */
    void stop();
}
