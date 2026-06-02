package org.envel.betterportals.bukkit.tasks;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.envel.betterportals.bukkit.block.external.IExternalBlockWatcherManager;
import org.envel.betterportals.bukkit.entity.faking.EntityTrackingManager;
import org.envel.betterportals.bukkit.net.ClientRequestHandler;
import org.envel.betterportals.bukkit.player.IPlayerData;
import org.envel.betterportals.bukkit.player.PlayerDataManager;
import org.envel.betterportals.bukkit.portal.IPortalActivityManager;
import org.envel.betterportals.shared.logging.Logger;
import org.envel.betterportals.bukkit.util.SchedulerUtil;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Invokes the players to update their portal views every tick.
 * The entry point for most plugin processing each tick.
 */
@Singleton
public class MainUpdate implements Runnable {
    private static final String ISSUES_URL = "https://github.com/OpenCommunity-Original/BetterPortals/issues";

    private final JavaPlugin pl;
    private final PlayerDataManager playerDataManager;
    private final IPortalActivityManager activityManager;
    private final EntityTrackingManager entityTrackingManager;
    private final ClientRequestHandler requestHandler;
    private final IExternalBlockWatcherManager blockWatcherManager;
    private final Logger logger;

    @Inject
    public MainUpdate(JavaPlugin pl,
            PlayerDataManager playerDataManager,
            IPortalActivityManager activityManager,
            EntityTrackingManager entityTrackingManager,
            ClientRequestHandler requestHandler,
            IExternalBlockWatcherManager blockWatcherManager, Logger logger) {
        this.pl = pl;
        this.playerDataManager = playerDataManager;
        this.activityManager = activityManager;
        this.entityTrackingManager = entityTrackingManager;
        this.requestHandler = requestHandler;
        this.blockWatcherManager = blockWatcherManager;
        this.logger = logger;
    }

    private SchedulerUtil.PortalTask updateTask;

    public void start() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        updateTask = SchedulerUtil.runTaskTimer(this, 0L, 1L);
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    @Override
    public void run() {
        try {
            if (SchedulerUtil.isFolia()) {
                playerDataManager.getPlayers().forEach(playerData -> {
                    SchedulerUtil.runForEntity(playerData.getPlayer(), playerData::onUpdate);
                });
            } else {
                playerDataManager.getPlayers().forEach(IPlayerData::onUpdate);
            }

            // Update replicated entities
            entityTrackingManager.update();

            // Deactivates and view-deactivates any unused portals that were active last
            // tick
            activityManager.postUpdate();

            requestHandler.handlePendingRequests();

            blockWatcherManager.update();

        } catch (RuntimeException ex) {
            logger.severe("A critical error occurred during main update.");
            logger.severe("Please create an issue at %s to get this fixed.", ISSUES_URL);
            ex.printStackTrace();
        }
    }
}
