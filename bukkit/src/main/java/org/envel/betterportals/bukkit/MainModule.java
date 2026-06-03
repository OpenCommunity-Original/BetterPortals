package org.envel.betterportals.bukkit;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import org.envel.betterportals.bukkit.block.BlockModule;
import org.envel.betterportals.bukkit.block.rotation.IBlockRotator;
import org.envel.betterportals.bukkit.block.rotation.ModernBlockRotator;
import org.envel.betterportals.bukkit.chunk.chunkloading.IChunkLoader;
import org.envel.betterportals.bukkit.chunk.chunkloading.ModernChunkLoader;
import org.envel.betterportals.bukkit.chunk.generation.IChunkGenerationChecker;
import org.envel.betterportals.bukkit.chunk.generation.ModernChunkGenerationChecker;
import org.envel.betterportals.bukkit.command.CommandsModule;
import org.envel.betterportals.bukkit.entity.EntityModule;
import org.envel.betterportals.bukkit.events.EventsModule;
import org.envel.betterportals.bukkit.net.NetworkModule;
import org.envel.betterportals.bukkit.player.PlayerModule;
import org.envel.betterportals.bukkit.portal.PortalModule;
import org.envel.betterportals.bukkit.tasks.BlockUpdateFinisher;
import org.envel.betterportals.bukkit.tasks.ThreadedBlockUpdateFinisher;
import org.envel.betterportals.shared.logging.Logger;
import org.envel.betterportals.shared.logging.OverrideLogger;
import org.envel.betterportals.shared.util.ReflectionUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;

public class MainModule extends AbstractModule {
    /**
     * Experimental mode uses NMS implementations of some code to significantly improve performance
     * TODO: Direct NMS code has been temporarily removed, but left in on another branch
     * TODO: Enabling this on the master branch will crash
     */
    private static final boolean EXPERIMENTAL_MODE = false;

    private final BetterPortals pl;

    public MainModule(BetterPortals pl) {
        this.pl = pl;
    }

    @Override
    protected void configure() {
        bind(JavaPlugin.class).toInstance(pl);
        bind(BetterPortals.class).toInstance(pl);
        bind(Logger.class).toInstance(new OverrideLogger(pl.getLogger()));
        bind(IChunkLoader.class).to(ModernChunkLoader.class);
        bind(IBlockRotator.class).to(ModernBlockRotator.class);
        bind(IChunkGenerationChecker.class).to(ModernChunkGenerationChecker.class);

        bind(BlockUpdateFinisher.class).to(ThreadedBlockUpdateFinisher.class);

        bind(org.envel.betterportals.bukkit.portal.selection.SelectionVisualizer.class).asEagerSingleton();
        bind(org.envel.betterportals.bukkit.portal.effects.PortalEffectsTask.class).asEagerSingleton();
        bind(org.envel.betterportals.bukkit.gui.PortalAdminGUI.class).asEagerSingleton();
        bind(org.envel.betterportals.bukkit.economy.EconomyManager.class).asEagerSingleton();

        io.foxserver.common.locale.LocaleAPI localeApi = new io.foxserver.common.locale.LocaleAPI(pl, "en_US", true);
        localeApi.load();
        pl.getServer().getPluginManager().registerEvents(localeApi, pl);
        bind(io.foxserver.common.locale.LocaleAPI.class).toInstance(localeApi);

        install(new EventsModule());
        install(new CommandsModule());
        install(new PortalModule());
        install(new BlockModule(EXPERIMENTAL_MODE));
        install(new NetworkModule());
        install(new PlayerModule());
        install(new EntityModule(EXPERIMENTAL_MODE));

        if(EXPERIMENTAL_MODE) {
            install(createNmsModule());
        }
    }

    private Module createNmsModule() {
        // We create the module via reflection to avoid referencing it and the packages it references, which might not exist if this method isn't called
        Class<?> nmsModuleClass = ReflectionUtil.findClass("org.envel.betterportals.bukkit.nms.direct.NmsOptimisationModule");
        Constructor<?> nmsModuleCtor = ReflectionUtil.findConstructor(nmsModuleClass);

        return (Module) ReflectionUtil.invokeConstructor(nmsModuleCtor);
    }
}
