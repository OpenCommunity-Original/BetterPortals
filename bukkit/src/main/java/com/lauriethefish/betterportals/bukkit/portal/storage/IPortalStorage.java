package com.lauriethefish.betterportals.bukkit.portal.storage;

import com.lauriethefish.betterportals.bukkit.config.MiscConfig;
import com.lauriethefish.betterportals.bukkit.portal.IPortalManager;
import com.lauriethefish.betterportals.shared.logging.Logger;
import com.lauriethefish.betterportals.bukkit.util.SchedulerUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

/**
 * Saves/loads portals from disk
 * TODO: Eventually add support for databases, or something else instead of just YAML
 */
public abstract class IPortalStorage implements Runnable    {
    protected Logger logger;

    private final JavaPlugin pl;
    private final MiscConfig miscConfig;

    public IPortalStorage(Logger logger, JavaPlugin pl, MiscConfig miscConfig) {
        this.logger = logger;
        this.pl = pl;
        this.miscConfig = miscConfig;
    }

    /**
     * Loads all stored portals and registers them in the {@link IPortalManager}
     * @throws IOException If reading the file failed
     */
    public abstract void loadPortals() throws IOException;

    /**
     * Saves all currently registered portals in {@link IPortalManager}
     * @throws IOException If writing the file failed
     */
    public abstract void savePortals() throws IOException;

    private SchedulerUtil.PortalTask saveTask;

    public void start() {
        stop();
        int saveInterval = miscConfig.getPortalSaveInterval();
        if(saveInterval > 0) {
            logger.fine("Starting autosave task");
            saveTask = SchedulerUtil.runTaskTimer(this, saveInterval, saveInterval);
        }   else    {
            logger.fine("Autosave is disabled");
        }
    }

    public void stop() {
        if(saveTask != null) {
            logger.fine("Stopping autosave task");
            saveTask.cancel();
            saveTask = null;
        }
    }

    @Override
    public void run() {
        try {
            logger.fine("Autosaving portals!");
            savePortals();
        }   catch(IOException ex) {
            logger.warning("Error occurred while saving the portals to portals.yml. Check your file permissions!");
            ex.printStackTrace();
        }
    }
}
