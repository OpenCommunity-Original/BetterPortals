package org.envel.betterportals.bukkit.portal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.envel.betterportals.bukkit.config.MiscConfig;
import org.envel.betterportals.bukkit.portal.predicate.IPortalPredicateManager;
import org.envel.betterportals.bukkit.util.StringUtil;
import org.envel.betterportals.shared.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
public class PortalManager implements IPortalManager    {
    private final Logger logger;
    private final IPortalPredicateManager predicateManager;
    private final IPortalActivityManager portalActivityManager;
    private final MiscConfig miscConfig;

    // Multiple portals can have the same origin position
    private final Map<Location, Set<IPortal>> portals = new HashMap<>();
    private final Map<UUID, IPortal> portalsById = new HashMap<>();

    @Inject
    public PortalManager(Logger logger, IPortalPredicateManager predicateManager, IPortalActivityManager portalActivityManager, MiscConfig miscConfig) {
        this.logger = logger;
        this.predicateManager = predicateManager;
        this.portalActivityManager = portalActivityManager;
        this.miscConfig = miscConfig;
    }

    @Override
    public Collection<IPortal> getAllPortals() {
        return portalsById.values();
    }

    @Override
    public Collection<IPortal> getPortalsAt(Location originLoc) {
        Set<IPortal> portalsAtLoc = portals.get(originLoc);
        return portalsAtLoc == null ? Collections.emptyList() : portalsAtLoc;
    }

    @Override
    public IPortal getPortalById(@Nullable UUID id) {
        return portalsById.get(id);
    }

    @Override
    public IPortal findClosestPortal(@NotNull Location position, double maximumDistance, Predicate<IPortal> predicate) {
        IPortal currentClosest = null;
        double currentClosestDistance = maximumDistance;
        for(Map.Entry<Location, Set<IPortal>> entry : portals.entrySet()) {

            Location portalPos = entry.getKey();
            // Avoid throwing an exception when portals not in this world are checked
            if(portalPos.getWorld() != position.getWorld()) {continue;}
            double distance = portalPos.distance(position);

            if(distance >= currentClosestDistance) {continue;}

            // Check to see if any portals here match the predicate
            for(IPortal portal : entry.getValue()) {
                if(!predicate.test(portal)) {continue;}

                currentClosest = portal;
                currentClosestDistance = distance;
                break;
            }
        }

        return currentClosest;
    }

    @Override
    public @NotNull Collection<IPortal> findActivatablePortals(@NotNull Player player) {
        return portals.values().stream() // Stream over the portal sets
                .flatMap(Set::stream) // Flatten the sets into a single stream of portals
                .filter(portal -> predicateManager.isActivatable(portal, player)) // Filter by the activatable predicate
                .collect(Collectors.toList()); // Collect the results into a List
    }


    @Override
    public void registerPortal(@NotNull IPortal portal) {
        logger.fine("Registering portal with origin position %s", portal.getOriginPos());

        Location originLoc = portal.getOriginPos().getLocation();

        // ── Anti-Dupe Portal Policy ───────────────────────────────────────────
        // Reject registration if a portal with the identical origin→dest pair
        // already exists. Without this guard, nether portal relighting (or buggy
        // spawning logic) can create multiple overlapping render sessions that
        // waste memory, CPU, and network bandwidth for no visual benefit.
        if(miscConfig.isPreventDuplicatePortals()) {
            Set<IPortal> existing = portals.get(originLoc);
            if(existing != null) {
                for(IPortal existingPortal : existing) {
                    if(existingPortal.getDestPos().equals(portal.getDestPos())) {
                        logger.fine(
                                "Anti-Dupe: rejected duplicate portal at origin %s → dest %s (id=%s)",
                                StringUtil.locationToString(originLoc),
                                portal.getDestPos(),
                                portal.getId()
                        );
                        return;
                    }
                }
            }
        }
        // ─────────────────────────────────────────────────────────────────────

        // Add a new portal array if one doesn't already exist for this location
        if(!portals.containsKey(originLoc)) {
            portals.put(originLoc, new HashSet<>());
        }
        portalsById.put(portal.getId(), portal);

        portals.get(originLoc).add(portal);
    }

    @Override
    public int removePortalsAt(@NotNull Location originLoc) {
        Set<IPortal> portalsRemoved = portals.remove(originLoc);
        if(portalsRemoved == null) {return 0;}

        // Make sure to also remove them from the ID map
        for(IPortal portal : portalsRemoved) {
            portalsById.remove(portal.getId());
        }

        logger.fine("Unregistering %d portal(s) at position %s", portalsRemoved.size(), StringUtil.locationToString(originLoc));
        return portalsRemoved.size();
    }

    @Override
    public boolean removePortal(@NotNull IPortal portal) {
        logger.fine("Unregistering portal at position %s", StringUtil.locationToString(portal.getOriginPos().getLocation()));

        Set<IPortal> portalsAtLoc = portals.get(portal.getOriginPos().getLocation());
        if(portalsAtLoc == null) {return false;}

        boolean wasRemoved = portalsAtLoc.remove(portal);
        // Remove the portal array if there are no longer any portals at this location
        if(portalsAtLoc.size() == 0) {
            portals.remove(portal.getOriginPos().getLocation());
        }
        portalsById.remove(portal.getId());
        return wasRemoved;
    }

    @Override
    public boolean removePortalById(@NotNull UUID id) {
        IPortal removed = portalsById.remove(id);
        if(removed == null) {return false;}
        removePortal(removed); // Also remove it in the location map

        return true;
    }

    @Override
    public void onReload() {
        portalActivityManager.resetActivity();
    }
}
