package org.envel.betterportals.velocity;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.envel.betterportals.proxy.net.IClientHandler;
import org.envel.betterportals.proxy.net.IPortalServer;
import org.envel.betterportals.shared.net.RequestException;
import org.envel.betterportals.shared.net.requests.PreviousServerPutRequest;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import org.slf4j.Logger;

import jakarta.inject.Inject;

public class BetterPortals {
    private final Injector injector;
    private final Logger logger;
    private IPortalServer portalServer;

    @Inject
    public BetterPortals(MainModule mainModule, Logger logger) {
        this.injector = Guice.createInjector(mainModule);
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            injector.getInstance(Config.class).load();
        } catch (Exception ex) {
            logger.error("Failed to load config file", ex);
            return;
        }

        try {
            this.portalServer = injector.getInstance(IPortalServer.class);
            portalServer.startUp();
        }   catch(RuntimeException ex) {
            logger.error("Failed to start up portal server", ex);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if(portalServer != null) {
            try {
                portalServer.shutDown();
            }   catch(RuntimeException ex) {
                logger.error("Failed to shut down portal server", ex);
            }
        }
    }

    @Subscribe
    public void onServerSwitch(ServerConnectedEvent event) {
        if(portalServer == null) { return; }

        if(!event.getPreviousServer().isPresent()) {
            return; // We only care about changes between two servers for selection copying
        }

        IClientHandler to = portalServer.getServer(event.getServer().getServerInfo().getName());

        String previousServerName = event.getPreviousServer().get().getServerInfo().getName();
        IClientHandler from = portalServer.getServer(previousServerName);

        if(from == null) {
            logger.debug("From server was unregistered for server switch event, skipping");
            return;
        }

        if(to == null) {
            logger.debug("To server was unregistered for server switch event, skipping");
            return;
        }

        logger.debug("Sending previous server put request");
        PreviousServerPutRequest request = new PreviousServerPutRequest();
        request.setPlayerId(event.getPlayer().getUniqueId());
        request.setPreviousServer(previousServerName);

        to.sendRequest(request, (response) -> {
            try {
                response.checkForErrors();
            }   catch(RequestException ex) {
                logger.warn("Failed to set previous server for player {}", event.getPlayer().getUniqueId());
                ex.printStackTrace();
            }
        });
    }
}
