package org.envel.betterportals.bukkit.entity.faking;

import com.google.inject.Inject;
import org.envel.betterportals.bukkit.portal.IPortal;
import org.envel.betterportals.shared.logging.Logger;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles updating, creating and removing {@link EntityTracker}s based on when a player is viewing an entity, and sending animations based on events.
 */
public abstract class EntityTrackingManager {
    private final IEntityTracker.Factory entityTrackerFactory;
    protected final Map<IPortal, Map<Entity, IEntityTracker>> trackersByPortal = new HashMap<>(); // Used for separating trackers based on portal
    protected final Logger logger;

    @Inject
    public EntityTrackingManager(Logger logger, IEntityTracker.Factory entityTrackerFactory) {
        this.logger = logger;
        this.entityTrackerFactory = entityTrackerFactory;
    }

    /**
     * Replicates <code>entity</code> to <code>player</code>, as though it were projected through the portal.
     * @param entity Entity to replicate
     * @param portal Portal to replicate through
     * @param player Player to show the replicated entity to
     */
    public void setTracking(Entity entity, IPortal portal, Player player) {
        // Get the tracker from the map, adding a new one if necessary
        Map<Entity, IEntityTracker> portalMap = trackersByPortal.computeIfAbsent(portal, k -> new HashMap<>());
        IEntityTracker tracker = portalMap.computeIfAbsent(entity, k -> {
            IEntityTracker newTracker = entityTrackerFactory.create(entity, portal);
            newTrackerAdded(newTracker);

            return newTracker;
        });

        tracker.addTracking(player);
    }

    /**
     * Called when a new entity tracker is added
     * @param tracker Tracker that has just been added
     */
    protected void newTrackerAdded(IEntityTracker tracker) { }

    /**
     * Called when a tracker has no players and is about to be removed
     * @param tracker Tracker that no longer has any viewing players
     */
    protected void trackerHasNoPlayers(IEntityTracker tracker) { }

    /**
     * Removes the replicated entity from <code>player</code>'s view, and stops sending update packets.
     * @param entity Entity to no longer be replicated
     * @param portal Portal that the entity was replicated through
     * @param player Player to stop replicating the entity for
     * @param sendPackets Whether or not to actually hide the entity for the player
     */
    public void setNoLongerTracking(Entity entity, IPortal portal, Player player, boolean sendPackets) {
        Map<Entity, IEntityTracker> portalMap = trackersByPortal.get(portal);

        IEntityTracker tracker = portalMap.get(entity);
        if(tracker == null) {
            logger.fine("Attempted to remove entity tracker that didn't exist. This should never happen!");
            return;
        }
        tracker.removeTracking(player, sendPackets);

        // If no players are tracking this entity, remove it from the map
        if(tracker.getTrackingPlayerCount() == 0) {
            trackerHasNoPlayers(tracker);

            portalMap.remove(entity);
            if(portalMap.isEmpty()) {
                trackersByPortal.remove(portal);
            }
        }
    }

    /**
     * Updates all currently replicated entities
     */
    public void update() {
        trackersByPortal.values().forEach((map) -> map.values().forEach(tracker -> {
            if (org.envel.betterportals.bukkit.util.SchedulerUtil.isFolia()) {
                org.envel.betterportals.bukkit.util.SchedulerUtil.runForEntity(tracker.getEntityInfo().getEntity(), tracker::update);
            } else {
                tracker.update();
            }
        }));
    }

    /**
     * Returns the tracker of <code>entity</code> on <code>portal</code>, or null if there is none.
     * @param portal The portal to check for trackers
     * @param entity The entity being tracked
     * @return The tracker of the entity, or null if there is none.
     */
    public @Nullable IEntityTracker getTracker(IPortal portal, Entity entity) {
        Map<Entity, IEntityTracker> portalTrackers = trackersByPortal.get(portal);
        if(portalTrackers == null) {return null;}

        return portalTrackers.get(entity);
    }
}
