package org.envel.betterportals.proxy;

import java.net.InetSocketAddress;
import java.util.UUID;

public interface IProxyConfig {
    /**
     * Gets the bind address
     * @return The address for the BP server to bind to
     */
    InetSocketAddress getBindAddress();

    /**
     * Gets the encryption key
     * @return The key used for encrypted communication with the bukkit servers
     */
    UUID getKey();
}
