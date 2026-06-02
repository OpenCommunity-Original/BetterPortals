package org.envel.betterportals.bungee;

import com.google.inject.Inject;
import org.envel.betterportals.proxy.net.IClientHandler;
import org.envel.betterportals.proxy.net.IPortalServer;
import org.envel.betterportals.shared.logging.Logger;
import org.envel.betterportals.shared.net.RequestException;
import org.envel.betterportals.shared.net.requests.PreviousServerPutRequest;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

public class ServerSwitch implements Listener {
    private final IPortalServer portalServer;
    private final Logger logger;

    @Inject
    public ServerSwitch(IPortalServer portalServer, Logger logger, Plugin pl) {
        this.portalServer = portalServer;
        this.logger = logger;
        pl.getProxy().getPluginManager().registerListener(pl, this);
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        logger.finer("Found server switch event for user %s", event.getPlayer().getUniqueId());
        if(event.getFrom() == null) {
            logger.finer("From server is null, skipping");
            return;
        }

        IClientHandler from = portalServer.getServer(event.getFrom().getName());
        IClientHandler to = portalServer.getServer(event.getPlayer().getServer().getInfo().getName());

        if(from == null) {
            logger.finer("From server was unregistered for server switch event, skipping");
            return;
        }

        if(to == null) {
            logger.finer("To server was unregistered for server switch event, skipping");
            return;
        }

        logger.finer("Sending previous server put request");
        PreviousServerPutRequest request = new PreviousServerPutRequest();
        request.setPlayerId(event.getPlayer().getUniqueId());
        request.setPreviousServer(event.getFrom().getName());

        to.sendRequest(request, (response) -> {
            try {
                logger.finer("Sent and received response");
                response.checkForErrors();
            }   catch(RequestException ex) {
                logger.warning("Failed to set previous server for player %s", event.getPlayer().getUniqueId());
                ex.printStackTrace();
            }
        });
    }
}