package org.envel.betterportals.bukkit.block.bukkit;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import org.envel.betterportals.bukkit.block.IMultiBlockChangeManager;
import org.envel.betterportals.bukkit.block.IBlockMap;

public class BukkitBlockMapModule extends AbstractModule {

    protected void configure() {
        install(new FactoryModuleBuilder()
                .implement(IBlockMap.class, BukkitBlockMap.class)
                .build(IBlockMap.Factory.class)
        );

        install(new FactoryModuleBuilder()
            .implement(IMultiBlockChangeManager.class, ModernMultiBlockChangeManager.class)
            .build(IMultiBlockChangeManager.Factory.class)
        );
    }
}
