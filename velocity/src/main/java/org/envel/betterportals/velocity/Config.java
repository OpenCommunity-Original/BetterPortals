package org.envel.betterportals.velocity;

import com.google.inject.Inject;
import org.envel.betterportals.proxy.IProxyConfig;
import org.envel.betterportals.shared.logging.Logger;
import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import lombok.Getter;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

@Singleton
public class Config implements IProxyConfig {
    private final Path dataDirectory;
    private final Logger logger;

    @Getter private InetSocketAddress bindAddress;
    @Getter private UUID key;

    @Inject
    public Config(@Named("dataDirectory") Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    private Path getConfigFilePath() {
        File dataFolder = dataDirectory.toFile();
        dataFolder.mkdir();

        return dataFolder.toPath().resolve("config.toml");
    }

    private Toml loadFile() throws IOException {
        Path configFilePath = getConfigFilePath();
        File configFile = configFilePath.toFile();
        if (!configFile.exists()) {
            logger.info("Saving default config . . .");
            InputStream defaultConfig = getClass().getResourceAsStream("/velocityconfig.toml");
            if(defaultConfig == null) {
                throw new IllegalStateException("Could not find default config file!");
            }

            Files.copy(defaultConfig, configFilePath);
        }

        return new Toml().read(configFile);
    }

    private void saveFile(Map<String, Object> config) throws IOException {
        TomlWriter tomlWriter = new TomlWriter();
        tomlWriter.write(config, getConfigFilePath().toFile());
    }

    public void load() throws IOException {
        Toml configFile = loadFile();
        boolean wasModified = false;

        Map<String, Object> configMap = configFile.toMap();

        if(!configMap.containsKey("logLevel")) {
            configMap.put("logLevel", "INFO");
            configMap.remove("enableDebugLogging");
            wasModified = true;
        }

        Level configuredLevel = Level.parse((String) configMap.get("logLevel"));
        logger.setLevel(configuredLevel);

        String addressStr = configFile.getString("bindAddress");
        if(addressStr == null) {
            throw new RuntimeException("Missing bind address");
        }

        int port = Math.toIntExact(configFile.getLong("serverPort"));
        if(port == 0) {
            throw new RuntimeException("Invalid bind port " + port);
        }

        try {
            key = UUID.fromString(Objects.requireNonNull((String) configMap.get("key"), "No encryption key found in the config"));
        }   catch(IllegalArgumentException ex) {
            logger.info("Generating new random encryption key");
            key = UUID.randomUUID();
            configMap.put("key", key.toString());
            wasModified = true;
        }

        bindAddress = new InetSocketAddress(addressStr, port);

        if(wasModified) {
            saveFile(configMap);
        }
    }
}
