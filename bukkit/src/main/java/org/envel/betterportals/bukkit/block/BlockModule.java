package org.envel.betterportals.bukkit.block;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import org.envel.betterportals.bukkit.block.bukkit.BukkitBlockMapModule;
import org.envel.betterportals.bukkit.block.external.BlockChangeWatcher;
import org.envel.betterportals.bukkit.block.external.ExternalBlockWatcherManager;
import org.envel.betterportals.bukkit.block.external.IBlockChangeWatcher;
import org.envel.betterportals.bukkit.block.external.IExternalBlockWatcherManager;
import org.envel.betterportals.bukkit.block.lighting.DummyLightDataManager;
import org.envel.betterportals.bukkit.block.lighting.ILightDataManager;
import org.envel.betterportals.bukkit.block.lighting.LightDataManger;
import org.envel.betterportals.bukkit.player.view.ViewFactory;
import org.envel.betterportals.bukkit.player.view.block.IPlayerBlockView;
import org.envel.betterportals.bukkit.player.view.block.PlayerBlockView;
import org.envel.betterportals.bukkit.player.view.entity.IPlayerEntityView;
import org.envel.betterportals.bukkit.player.view.entity.PlayerEntityView;

public class BlockModule extends AbstractModule {
    private final boolean usingNms;

    public BlockModule(boolean useNms) {
        this.usingNms = useNms;
    }

    @Override
    public void configure() {
        install(new FactoryModuleBuilder()
                .implement(IBlockChangeWatcher.class, BlockChangeWatcher.class)
                .build(IBlockChangeWatcher.Factory.class)
        );

        install(new FactoryModuleBuilder()
                .implement(IPlayerBlockView.class, PlayerBlockView.class)
                .implement(IPlayerEntityView.class, PlayerEntityView.class)
                .build(ViewFactory.class)
        );

        bind(IExternalBlockWatcherManager.class).to(ExternalBlockWatcherManager.class);

        try {
            Class.forName("org.bukkit.block.data.type.Light");
            bind(ILightDataManager.class).to(LightDataManger.class);
        } catch (ClassNotFoundException ignored) {
            bind(ILightDataManager.class).to(DummyLightDataManager.class);
        }

        // If using direct NMS, then alternative block map implementations are used
        if(!usingNms) {
            install(new BukkitBlockMapModule());
        }
    }
}
