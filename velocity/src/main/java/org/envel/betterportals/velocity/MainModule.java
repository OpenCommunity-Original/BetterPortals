package org.envel.betterportals.velocity;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import org.envel.betterportals.proxy.IProxy;
import org.envel.betterportals.proxy.IProxyConfig;
import org.envel.betterportals.proxy.net.ProxyModule;
import org.envel.betterportals.shared.logging.Logger;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.Optional;

public class MainModule extends AbstractModule {
    private final ProxyServer proxyServer;
    private final Path dataDirectory;
    private final org.slf4j.Logger logger;

    private final String pluginVersion;

    @Inject
    public MainModule(ProxyServer proxyServer, @DataDirectory Path dataDirectory, PluginContainer pluginContainer, org.slf4j.Logger logger) {
        this.proxyServer = proxyServer;
        this.dataDirectory = dataDirectory;
        this.logger = logger;

        Optional<String> version = pluginContainer.getDescription().getVersion();
        if(!version.isPresent()) {
            throw new IllegalStateException("Plugin had no version");
        }
        this.pluginVersion = version.get();
    }

    protected void configure() {
        install(new ProxyModule());

        bind(IProxyConfig.class).to(Config.class);
        bind(ProxyServer.class).toInstance(proxyServer);
        bind(IProxy.class).to(VelocityProxy.class);
        bind(String.class).annotatedWith(Names.named("pluginVersion")).toInstance(pluginVersion);
        bind(Path.class).annotatedWith(Names.named("dataDirectory")).toInstance(dataDirectory);
        bind(Logger.class).toInstance(new VelocityLogger(logger));
    }
}
