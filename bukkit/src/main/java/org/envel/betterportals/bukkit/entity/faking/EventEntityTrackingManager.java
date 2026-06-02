package org.envel.betterportals.bukkit.entity.faking;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.envel.betterportals.bukkit.events.IEventRegistrar;
import org.envel.betterportals.bukkit.nms.AnimationType;
import org.envel.betterportals.bukkit.portal.IPortal;
import org.envel.betterportals.shared.logging.Logger;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * An entity tracking manager which sends events to trackers in the form of {@link IEntityTracker#onPickup(EntityInfo)} and {@link IEntityTracker#onAnimation(AnimationType)}
 */
@Singleton
public class EventEntityTrackingManager extends EntityTrackingManager implements Listener {
    private final Map<Entity, List<IEntityTracker>> trackersByEntity = new HashMap<>();

    /**
     * Bukkit doesn't allow us to get the hand used from {@link PlayerAnimationEvent}, so we get it from {@link PlayerInteractEvent} then store it for later.
     */
    private final Map<Entity, EquipmentSlot> lastHandUsed = new HashMap<>();

    @Inject
    public EventEntityTrackingManager(Logger logger, IEventRegistrar eventRegistrar, IEntityTracker.Factory entityTrackerFactory) {
        super(logger, entityTrackerFactory);
        eventRegistrar.register(this);
    }

    @Override
    protected void newTrackerAdded(IEntityTracker tracker) {
        // Add this new tracker to the per-entity map
        trackersByEntity.computeIfAbsent(tracker.getEntityInfo().getEntity(), entity -> new ArrayList<>()).add(tracker);
    }

    @Override
    protected void trackerHasNoPlayers(IEntityTracker tracker) {
        // Remove the tracker from the per-entity map
        List<IEntityTracker> trackersForEntity = trackersByEntity.get(tracker.getEntityInfo().getEntity());
        trackersForEntity.remove(tracker);

        // Delete the map if no trackers exist for this entity to avoid leaking memory
        if(trackersForEntity.size() == 0) {
            trackersByEntity.remove(tracker.getEntityInfo().getEntity());
        }
    }

    /**
     * Performs <code>action</code> to each tracker of <code>entity</code>.
     * @param entity Entity to check for trackers
     * @param action Action to perform on the trackers
     */
    private void forEachTracker(Entity entity, Consumer<IEntityTracker> action) {
        Collection<IEntityTracker> trackers = trackersByEntity.get(entity);
        if(trackers == null) {return;}

        trackers.forEach(action);
    }

    /**
     * Handles making entities turn red upon being hit
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        forEachTracker(event.getEntity(), tracker -> tracker.onAnimation(AnimationType.DAMAGE));
    }

    /**
     * Handles moving the tracker's hand when the entity moves their hand
     */
    @EventHandler
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        if(event.getAnimationType() != PlayerAnimationType.ARM_SWING) {return;}

        EquipmentSlot hand = lastHandUsed.get(event.getPlayer());
        if(hand == null) {return;}

        AnimationType type = hand == EquipmentSlot.HAND ? AnimationType.MAIN_HAND : AnimationType.OFF_HAND;
        forEachTracker(event.getPlayer(), tracker -> tracker.onAnimation(type));
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();

        forEachTracker(entity, (tracker) -> {
            Map<Entity, IEntityTracker> portalTrackers = trackersByPortal.get(tracker.getPortal());
            IEntityTracker pickedUp = portalTrackers.get(event.getItem());

            if(pickedUp != null) {
                logger.fine("Sending pickup packet");
                tracker.onPickup(pickedUp.getEntityInfo());
            }   else    {
                logger.fine("Not sending pickup packet - the item isn't viewable");
            }
        });
    }

    /**
     * Workaround for missing API
     * @see EventEntityTrackingManager#lastHandUsed
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        EquipmentSlot hand = event.getHand();
        if(hand != null) {
            lastHandUsed.put(event.getPlayer(), hand);
        }
    }

    @Override
    public void update() {
        trackersByPortal.values().forEach((map) -> map.values().forEach(IEntityTracker::update));
    }

    @Override
    public @Nullable IEntityTracker getTracker(IPortal portal, Entity entity) {
        Map<Entity, IEntityTracker> portalTrackers = trackersByPortal.get(portal);
        if(portalTrackers == null) {return null;}

        return portalTrackers.get(entity);
    }
}
