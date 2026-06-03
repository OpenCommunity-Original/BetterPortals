package org.envel.betterportals.bukkit.portal;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import org.envel.betterportals.bukkit.entity.IPortalEntityManager;
import org.envel.betterportals.bukkit.entity.PortalEntityManager;
import org.envel.betterportals.bukkit.math.PortalTransformationsFactory;
import org.envel.betterportals.bukkit.portal.blend.DimensionBlendManager;
import org.envel.betterportals.bukkit.portal.blend.IDimensionBlendManager;
import org.envel.betterportals.bukkit.portal.predicate.IPortalPredicateManager;
import org.envel.betterportals.bukkit.portal.predicate.PortalPredicateManager;
import org.envel.betterportals.bukkit.portal.spawning.IPortalSpawner;
import org.envel.betterportals.bukkit.portal.spawning.PortalSpawner;
import org.envel.betterportals.bukkit.portal.storage.IPortalStorage;
import org.envel.betterportals.bukkit.portal.storage.YamlPortalStorage;

public class PortalModule extends AbstractModule {
    @Override
    public void configure() {
        install(new FactoryModuleBuilder()
                .implement(IPortal.class, Portal.class)
                .build(IPortal.Factory.class)
        );
        install(new FactoryModuleBuilder()
                .implement(IPortalEntityManager.class, PortalEntityManager.class)
                .build(IPortalEntityManager.Factory.class)
        );
        install(new FactoryModuleBuilder().build(PortalTransformationsFactory.class));

        bind(IPortalPredicateManager.class).to(PortalPredicateManager.class);
        bind(IPortalStorage.class).to(YamlPortalStorage.class);

        bind(IPortalManager.class).to(PortalManager.class);
        bind(IPortalActivityManager.class).to(PortalActivityManager.class);

        bind(IPortalSpawner.class).to(PortalSpawner.class);
        bind(IDimensionBlendManager.class).to(DimensionBlendManager.class);


        // Portals need a PortalFactory for their deserialization
        // Not really another way we can really get it over there, so gotta use static injection :/
        requestStaticInjection(Portal.class);
    }
}
