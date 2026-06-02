package org.envel.betterportals.bukkit.portal.selection;


import com.google.inject.Inject;
import org.envel.betterportals.bukkit.command.framework.CommandException;
import org.envel.betterportals.bukkit.config.MessageConfig;
import org.envel.betterportals.bukkit.net.requests.GetSelectionRequest;
import org.envel.betterportals.bukkit.portal.IPortal;
import org.envel.betterportals.bukkit.portal.IPortalManager;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;

public class SelectionManager implements ISelectionManager {
    private final MessageConfig messageConfig;
    @Getter private final IPortalSelection currentlySelecting;
    private final IPortal.Factory portalFactory;
    private final IPortalManager portalManager;

    @Getter private IPortalSelection originSelection;
    @Getter private IPortalSelection destSelection;

    @Getter @Setter private GetSelectionRequest.ExternalSelectionInfo externalSelection;

    @Inject
    public SelectionManager(MessageConfig messageConfig, IPortalSelection currentlySelecting, IPortal.Factory portalFactory, IPortalManager portalManager) {
        this.messageConfig = messageConfig;
        this.currentlySelecting = currentlySelecting;
        this.portalFactory = portalFactory;
        this.portalManager = portalManager;

    }

    @Override
    public void trySelectOrigin() throws CommandException   {
        verifyCurrentSelection();
        originSelection = currentlySelecting.clone();
    }

    @Override
    public void trySelectDestination() throws CommandException  {
        verifyCurrentSelection();
        destSelection = currentlySelecting.clone();
    }

    private void verifyCurrentSelection() throws CommandException {
        if(!currentlySelecting.isValid()) {
            throw new CommandException(messageConfig.getErrorMessage("invalidSelection"));
        }
    }

    @Override
    public void tryCreateFromSelection(Player player, boolean twoWay, boolean invert) throws CommandException {
        if(originSelection == null || destSelection == null) {
            throw new CommandException(messageConfig.getErrorMessage("mustSelectBothSides"));
        }

        // Portals must have the same origin and destination size
        if(!originSelection.getPortalSize().equals(destSelection.getPortalSize())) {
            throw new CommandException(messageConfig.getErrorMessage("differentSizes"));
        }

        // This could invert either the destination or origin selection - there's no difference
        if(invert) {
            destSelection.invertDirection();
        }

        IPortal portal = portalFactory.create(originSelection.getPortalPosition(), destSelection.getPortalPosition(),
                                                            originSelection.getPortalSize(), true, UUID.randomUUID(), player.getUniqueId(), null, true);
        portalManager.registerPortal(portal);

        // Add another portal going back if we're in two way mode
        if(twoWay) {
            IPortal reversePortal = portalFactory.create(destSelection.getPortalPosition(), originSelection.getPortalPosition(),
                    originSelection.getPortalSize(), true, UUID.randomUUID(), player.getUniqueId(), null, true);
            portalManager.registerPortal(reversePortal);
        }
        originSelection = null;
        destSelection = null;
    }

    @Override
    public void tryCreateFromExternalSelection(Player player, boolean invert) throws CommandException {
        if(originSelection == null || externalSelection == null) {
            throw new CommandException(messageConfig.getErrorMessage("mustSelectBothSides"));
        }

        Vector externalSize = new Vector(externalSelection.getSizeX(), externalSelection.getSizeY(), 0.0);

        if(invert) {
            originSelection.invertDirection();
        }

        if(!originSelection.getPortalSize().equals(externalSize)) {
            throw new CommandException(messageConfig.getErrorMessage("differentSizes"));
        }

        // Currently you have to manually create a portal in the other direction
        IPortal portal = portalFactory.create(originSelection.getPortalPosition(), externalSelection.getPosition(),
                    originSelection.getPortalSize(), true, UUID.randomUUID(), player.getUniqueId(), null, true);
        portalManager.registerPortal(portal);
    }

    private long lastActivityTime = System.currentTimeMillis();

    @Override
    public long getLastActivityTime() {
        return lastActivityTime;
    }

    @Override
    public void recordActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }
}
