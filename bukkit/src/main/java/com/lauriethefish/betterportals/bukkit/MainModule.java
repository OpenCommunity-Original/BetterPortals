package com.lauriethefish.betterportals.bukkit;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.lauriethefish.betterportals.bukkit.block.BlockModule;
import com.lauriethefish.betterportals.bukkit.block.rotation.IBlockRotator;
import com.lauriethefish.betterportals.bukkit.block.rotation.ModernBlockRotator;
import com.lauriethefish.betterportals.bukkit.chunk.chunkloading.IChunkLoader;
import com.lauriethefish.betterportals.bukkit.chunk.chunkloading.ModernChunkLoader;
import com.lauriethefish.betterportals.bukkit.chunk.generation.IChunkGenerationChecker;
import com.lauriethefish.betterportals.bukkit.chunk.generation.ModernChunkGenerationChecker;
import com.lauriethefish.betterportals.bukkit.command.CommandsModule;
import com.lauriethefish.betterportals.bukkit.entity.EntityModule;
import com.lauriethefish.betterportals.bukkit.events.EventsModule;
import com.lauriethefish.betterportals.bukkit.net.NetworkModule;
import com.lauriethefish.betterportals.bukkit.player.PlayerModule;
import com.lauriethefish.betterportals.bukkit.portal.PortalModule;
import com.lauriethefish.betterportals.bukkit.tasks.BlockUpdateFinisher;
import com.lauriethefish.betterportals.bukkit.tasks.ThreadedBlockUpdateFinisher;
import com.lauriethefish.betterportals.shared.logging.Logger;
import com.lauriethefish.betterportals.shared.logging.OverrideLogger;
import com.lauriethefish.betterportals.shared.util.ReflectionUtil;
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

        bind(com.lauriethefish.betterportals.bukkit.portal.selection.SelectionVisualizer.class).asEagerSingleton();
        bind(com.lauriethefish.betterportals.bukkit.portal.effects.PortalEffectsTask.class).asEagerSingleton();
        bind(com.lauriethefish.betterportals.bukkit.gui.PortalAdminGUI.class).asEagerSingleton();
        bind(com.lauriethefish.betterportals.bukkit.economy.EconomyManager.class).asEagerSingleton();

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
        Class<?> nmsModuleClass = ReflectionUtil.findClass("com.lauriethefish.betterportals.bukkit.nms.direct.NmsOptimisationModule");
        Constructor<?> nmsModuleCtor = ReflectionUtil.findConstructor(nmsModuleClass);

        return (Module) ReflectionUtil.invokeConstructor(nmsModuleCtor);
    }
}
