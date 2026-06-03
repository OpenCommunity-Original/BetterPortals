package org.envel.betterportals.proxy;

import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.UUID;

public interface IProxy {
    /**
     * Gets the version of BetterPortals on the proxy
     * @return The version of BetterPortals currently running
     */
    String getPluginVersion();

    /**
     * Finds the server with the address <code>clientAddress</code>.
     * @param clientAddress The address to search for
     * @return The name of server with this address, or null if there is none
     */
    @Deprecated
    @Nullable String findServer(InetSocketAddress clientAddress);

    /**
     * Finds if the server with the given name exists
     * @param serverName Name of the server to check
     * @return True if a server exists with that name, false otherwise
     */
    boolean serverExists(String serverName);

    /**
     * Finds if a player exists with the given unique ID
     * @param uid The unique ID
     * @return True if a player exists with this unique ID, false otherwise
     */
    boolean playerExists(UUID uid);

    /**
     * Teleports a player to the server with the given name
     * @param uid The unique ID of the player
     * @param destinationServer The server that the player will be teleported to
     * @throws IllegalArgumentException If no player exists with the given unique ID, or no server exists with the given name
     */
    void changePlayerServer(UUID uid, String destinationServer);
}
