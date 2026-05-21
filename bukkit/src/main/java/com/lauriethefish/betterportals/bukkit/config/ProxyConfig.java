package com.lauriethefish.betterportals.bukkit.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.lauriethefish.betterportals.shared.logging.Logger;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.UUID;

@Singleton
public class ProxyConfig {
    private final Logger logger;

    @Getter private boolean isEnabled; // Whether or not bungeecord support will be enabled
    @Getter private InetSocketAddress address;
    @Getter private UUID encryptionKey; // Used so that portal data can't be intercepted on the network
    @Getter private int reconnectionDelay; // How long after being disconnected before attempting a reconnection (in ticks)
    @Getter private boolean warnOnMissingSelection;
    @Getter public String overrideServerName;
    @Getter public boolean keepAlive; // Whether or not the socket used will have keepAlive enabled

    @Inject
    public ProxyConfig(Logger logger) {
        this.logger = logger;
    }

    public void load(FileConfiguration config) {
        ConfigurationSection section = Objects.requireNonNull(config.getConfigurationSection("proxy"), "Proxy section missing");

        isEnabled = section.getBoolean("enableProxy");
        if(!isEnabled) {return;} // No point loading everything else if the proxy is disabled

        // Load the IP address from the proxy address and port
        String rawAddress = Objects.requireNonNull(section.getString("proxyAddress"), "Proxy address missing");
        int port = section.getInt("proxyPort");
        address = new InetSocketAddress(rawAddress, port);

        reconnectionDelay = section.getInt("reconnectionDelay");
        if(reconnectionDelay <= 0) {
            throw new IllegalArgumentException("reconnectionDelay must be greater than 0 (got " + reconnectionDelay + ")");
        }

        overrideServerName = section.getString("serverName");
        if(overrideServerName != null && overrideServerName.isEmpty()) {
            overrideServerName = null;
        }

        String legacyOverrideOption = section.getString("overrideServerName");
        if(legacyOverrideOption != null && legacyOverrideOption.isEmpty()) {
            legacyOverrideOption = null;
        }

        if(legacyOverrideOption != null && overrideServerName == null) {
            overrideServerName = legacyOverrideOption;
        }   else if(overrideServerName == null)   {
            // Previously we allowed finding the server name based on the IP of the connecting server, but this cannot be relied on
            // The server may be in a docker container, or some other kind of network that causes the IP to be different from that in the bungeecord config
            logger.warning("No server name set in the BP proxy config. It is highly recommended to set this value to the server name in the bungeecord config to avoid issues with the proxy determining which server is connecting.");
            logger.info("You can set this by adding the server name in the field called 'serverName'");
        }

        try {
            encryptionKey = UUID.fromString(Objects.requireNonNull(section.getString("key"), "Encryption key missing"));
        }   catch(IllegalArgumentException ex) {
            // Print a warning message if it fails instead of a spammy error message
            logger.warning("Failed to load encryption key from config file! Please make sure you set this to the key in the bungeecord config.");
            isEnabled = false; // Disable proxy connection - there's no valid encryption key so connection will just fail
        }

        warnOnMissingSelection = section.getBoolean("warnOnMissingSelection");
        keepAlive = section.getBoolean("keepAlive", true);
    }
}
