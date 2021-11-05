package com.lauriethefish.betterportals.bukkit.entity.faking;

import com.lauriethefish.betterportals.bukkit.events.IEventRegistrar;
import com.lauriethefish.betterportals.bukkit.nms.AnimationType;
import com.lauriethefish.betterportals.shared.logging.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A simpler entity tracking manager, which does not send the update events ({@link IEntityTracker#onPickup(EntityInfo)} and {@link IEntityTracker#onAnimation(AnimationType)}) to each entity tracker.
 * This entity tracking manager also will not update the trackers via ({@link IEntityTracker#update()})
 */
@Singleton
public class NoUpdateEntityTrackingManager extends EntityTrackingManager {
    @Inject
    public NoUpdateEntityTrackingManager(Logger logger, IEntityTracker.Factory entityTrackerFactory) {
        super(logger, entityTrackerFactory);
    }

    @Override
    public void update() {
        // Deliberately not calling super.update since this tracking manager will not update the trackers
    }
}