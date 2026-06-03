package org.envel.betterportals.bukkit.player;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Names;
import org.envel.betterportals.bukkit.portal.selection.ISelectionManager;
import org.envel.betterportals.bukkit.portal.selection.IPortalSelection;
import org.envel.betterportals.bukkit.portal.selection.SelectionManager;
import org.envel.betterportals.bukkit.portal.selection.PortalSelection;
import org.envel.betterportals.bukkit.player.view.IPlayerPortalView;
import org.envel.betterportals.bukkit.player.view.PlayerPortalView;
import org.envel.betterportals.bukkit.player.view.PlayerPortalViewFactory;
import org.envel.betterportals.bukkit.player.view.block.IPlayerBlockStates;
import org.envel.betterportals.bukkit.player.view.block.PlayerBlockStates;
import org.bukkit.Bukkit;

public class PlayerModule extends AbstractModule {
    @Override
    public void configure() {
        install(new FactoryModuleBuilder()
                .implement(IPlayerBlockStates.class, PlayerBlockStates.class)
                .build(IPlayerBlockStates.Factory.class)
        );
        install(new FactoryModuleBuilder()
                .implement(IPlayerData.class, PlayerData.class)
                .build(IPlayerData.Factory.class)
        );
        install(new FactoryModuleBuilder()
                .implement(IPlayerPortalView.class, PlayerPortalView.class)
                .build(PlayerPortalViewFactory.class)
        );

        // Base the distance for a hard block reset on the server's view distance
        double blockSendUpdateDistance = Bukkit.getServer().getViewDistance() * 25;
        bind(double.class).annotatedWith(Names.named("blockSendUpdateDistance")).toInstance(blockSendUpdateDistance);
        bind(IPlayerDataManager.class).to(PlayerDataManager.class).asEagerSingleton();

        bind(ISelectionManager.class).to(SelectionManager.class);
        bind(IPortalSelection.class).to(PortalSelection.class);
    }
}
