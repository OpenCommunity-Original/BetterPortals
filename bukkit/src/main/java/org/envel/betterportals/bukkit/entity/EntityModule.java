package org.envel.betterportals.bukkit.entity;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import org.envel.betterportals.bukkit.entity.faking.*;

public class EntityModule extends AbstractModule {
    private final boolean usingNms;

    public EntityModule(boolean usingNms) {
        this.usingNms = usingNms;
    }

    @Override
    public void configure() {
        // If using direct NMS, an alternative implementation is bound
        if(!usingNms) {
            install(new FactoryModuleBuilder()
                    .implement(IEntityTracker.class, EntityTracker.class)
                    .build(IEntityTracker.Factory.class)
            );

            bind(IEntityFinder.class).to(BukkitEntityFinder.class);
        }

        bind(IEntityPacketManipulator.class).to(EntityPacketManipulator.class);

        // If using direct NMS, then we can use a no-update entity tracking manager, since updates are forwarded from NMS code
        bind(EntityTrackingManager.class).to(usingNms ? NoUpdateEntityTrackingManager.class : EventEntityTrackingManager.class);
    }
}
