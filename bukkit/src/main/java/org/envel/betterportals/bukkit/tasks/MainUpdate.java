package org.envel.betterportals.bukkit.tasks;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.bukkit.Bukkit;
import org.envel.betterportals.bukkit.block.external.IExternalBlockWatcherManager;
import org.envel.betterportals.bukkit.entity.faking.EntityTrackingManager;
import org.envel.betterportals.bukkit.net.ClientRequestHandler;
import org.envel.betterportals.bukkit.player.IPlayerData;
import org.envel.betterportals.bukkit.player.PlayerDataManager;
import org.envel.betterportals.bukkit.portal.IPortalActivityManager;
import org.envel.betterportals.bukkit.config.MiscConfig;
import org.envel.betterportals.shared.logging.Logger;
import org.envel.betterportals.bukkit.util.SchedulerUtil;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Invokes the players to update their portal views every tick.
 * The entry point for most plugin processing each tick.
 */
@Singleton
public class MainUpdate implements Runnable {
    private static final String ISSUES_URL = "https://discord.gg/wTVNTJsBUr";

    private final JavaPlugin pl;
    private final PlayerDataManager playerDataManager;
    private final IPortalActivityManager activityManager;
    private final EntityTrackingManager entityTrackingManager;
    private final ClientRequestHandler requestHandler;
    private final IExternalBlockWatcherManager blockWatcherManager;
    private final MiscConfig miscConfig;
    private final Logger logger;

    // TPS check cache — recomputed every TPS_CACHE_TICKS ticks to avoid calling getTPS() per-player
    private static final int TPS_CACHE_TICKS = 5;
    private int tpsCacheAge = TPS_CACHE_TICKS; // force refresh on first tick
    private boolean tpsTooLow = false;
    // Cached once at start() to avoid calling the getter every tick
    private double minTpsThreshold;
    private boolean minTpsEnabled;

    @Inject
    public MainUpdate(JavaPlugin pl,
            PlayerDataManager playerDataManager,
            IPortalActivityManager activityManager,
            EntityTrackingManager entityTrackingManager,
            ClientRequestHandler requestHandler,
            IExternalBlockWatcherManager blockWatcherManager, MiscConfig miscConfig, Logger logger) {
        this.pl = pl;
        this.playerDataManager = playerDataManager;
        this.activityManager = activityManager;
        this.entityTrackingManager = entityTrackingManager;
        this.requestHandler = requestHandler;
        this.blockWatcherManager = blockWatcherManager;
        this.miscConfig = miscConfig;
        this.logger = logger;
    }

    private SchedulerUtil.PortalTask updateTask;

    public void start() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        // Cache the threshold value — avoids getter call on every tick
        minTpsThreshold = miscConfig.getMinTpsForRendering();
        minTpsEnabled = minTpsThreshold > 0.0;
        tpsCacheAge = TPS_CACHE_TICKS; // force refresh on next tick
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
            // Compute skipRendering once per tick (TPS check cached every TPS_CACHE_TICKS ticks)
            boolean skipRendering = isTpsTooLowCached();

            if (SchedulerUtil.isFolia()) {
                playerDataManager.getPlayers().forEach(playerData -> {
                    SchedulerUtil.runForEntity(playerData.getPlayer(), () -> playerData.onUpdate(skipRendering));
                });
            } else {
                playerDataManager.getPlayers().forEach(pd -> pd.onUpdate(skipRendering));
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

    /**
     * Returns whether the current TPS is below the configured minimum.
     * Result is cached for {@link #TPS_CACHE_TICKS} ticks to avoid calling
     * {@link Bukkit#getTPS()} (an NMS call) once per player per tick.
     */
    private boolean isTpsTooLowCached() {
        if (!minTpsEnabled) return false;

        if (++tpsCacheAge >= TPS_CACHE_TICKS) {
            tpsCacheAge = 0;
            double[] tps = Bukkit.getTPS();
            tpsTooLow = tps.length > 0 && tps[0] < minTpsThreshold;
        }
        return tpsTooLow;
    }
}
