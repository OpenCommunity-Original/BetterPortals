package org.envel.betterportals.bukkit.player;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.envel.betterportals.bukkit.BetterPortals;
import org.envel.betterportals.bukkit.config.MiscConfig;
import org.envel.betterportals.bukkit.portal.selection.ISelectionManager;
import org.envel.betterportals.bukkit.player.view.IPlayerPortalView;
import org.envel.betterportals.bukkit.player.view.PlayerPortalViewFactory;
import org.envel.betterportals.bukkit.portal.IPortal;
import org.envel.betterportals.bukkit.portal.IPortalActivityManager;
import org.envel.betterportals.bukkit.portal.IPortalManager;
import org.envel.betterportals.bukkit.portal.predicate.IPortalPredicateManager;
import org.envel.betterportals.shared.logging.Logger;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerData implements IPlayerData  {
    @Getter private final Player player;
    @Getter private final YamlConfiguration permanentData;
    @Getter @Setter private ISelectionManager selection;

    private final BetterPortals pl;
    private final Logger logger;
    private final IPortalManager portalManager;
    private final IPortalPredicateManager portalPredicateManager;
    private final IPortalActivityManager portalActivityManager;
    private final MiscConfig miscConfig;

    private final PlayerPortalViewFactory playerPortalViewFactory;

    // A concurrent map is used so that removing a portal while updating portals doesn't cause an error
    private final Map<IPortal, IPlayerPortalView> portalViews = new ConcurrentHashMap<>();

    private boolean viewsFrozen;

    @Inject
    public PlayerData(@Assisted Player player, ISelectionManager selection, IPortalManager portalManager, IPortalPredicateManager portalPredicateManager, BetterPortals pl, Logger logger, IPortalActivityManager portalActivityManager, PlayerPortalViewFactory playerPortalViewFactory, MiscConfig miscConfig) {
        this.player = player;
        this.selection = selection;
        this.portalManager = portalManager;
        this.portalPredicateManager = portalPredicateManager;
        this.pl = pl;
        this.logger = logger;
        this.portalActivityManager = portalActivityManager;
        this.playerPortalViewFactory = playerPortalViewFactory;
        this.miscConfig = miscConfig;

        permanentData = loadPermanentDataYml();
    }

    @Override
    public @NotNull Collection<IPortal> getViewedPortals() {
        return Collections.unmodifiableCollection(portalViews.keySet());
    }

    // Updates the player's current view through their portals
    private void updatePortalViews(Collection<IPortal> nowViewablePortals) {
        for(Map.Entry<IPortal, IPlayerPortalView> entry : portalViews.entrySet()) {
            // If this existing view through a portal is for a portal that is now non-viewable, remove it
            // The second check is here since the player could've been teleported through the portal during this update, and might still be on nowViewablePortals
            if(!nowViewablePortals.contains(entry.getKey()) || player.getWorld() != entry.getKey().getOriginPos().getWorld()) {
                logger.finer("Portal no longer being viewed by player %s", player.getUniqueId());
                setNotViewing(entry.getKey());
                continue;
            }

            // Calling this will call the portals update method if it has not been called already this tick
            portalActivityManager.onPortalViewedThisTick(entry.getKey());

            entry.getValue().update();
        }
    }

    private Collection<IPortal> updateViewablePortals() {
        List<IPortal> nowViewablePortals = new ArrayList<>();

        // TODO: when an API is created, allow plugins to add their own predicates
        Collection<IPortal> activatablePortals = portalManager.findActivatablePortals(player);

        // For the portals that we can activate, find out which ones can be viewed by the player
        for(IPortal portal : activatablePortals) {
            portalActivityManager.onPortalActivatedThisTick(portal);
            if(!portalPredicateManager.isViewable(portal, player)) {continue;}

            nowViewablePortals.add(portal);
        }

        // ── Fair Use Policy ──────────────────────────────────────────────────
        // If a per-player cap is configured, sort candidates by distance (ascending)
        // and keep only the closest N portals. This prevents a single player from
        // triggering expensive block/entity mirroring for many portals at once.
        int limit = miscConfig.getMaxPortalsPerPlayer();
        if(limit > 0 && nowViewablePortals.size() > limit) {
            Location playerLoc = player.getLocation();
            nowViewablePortals.sort(Comparator.comparingDouble(
                    p -> p.getOriginPos().getLocation().distanceSquared(playerLoc)
            ));
            nowViewablePortals = nowViewablePortals.subList(0, limit);
        }
        // ─────────────────────────────────────────────────────────────────────

        // Register views for portals that weren't viewed last update
        for(IPortal portal : nowViewablePortals) {
            if(!portalViews.containsKey(portal)) {
                setViewing(portal);
                logger.finer("Portal now being viewed by player %s", player.getUniqueId());
            }
        }

        return nowViewablePortals;
    }

    @Override
    public void onUpdate(boolean skipRendering) {
        if (skipRendering) {
            // TPS too low — skip all portal view processing for this tick.
            // Do NOT pass emptyList to updatePortalViews as that would remove all active views.
            return;
        }

        Collection<IPortal> nowViewablePortals = updateViewablePortals();

        if (!viewsFrozen) {
            updatePortalViews(nowViewablePortals);
        }
    }

    private void deactivateViews(boolean loggingOut) {
        for(IPlayerPortalView view : portalViews.values()) {
            view.onDeactivate(loggingOut);
        }
        portalViews.clear();
    }

    @Override
    public void onPluginDisable() {
        deactivateViews(false);
    }

    @Override
    public void onLogout() {
        deactivateViews(true);
    }


    @Override
    @SuppressWarnings("deprecation")
    public void savePermanentData() {

        File dataFolder = new File(pl.getDataFolder(), "playerData");

        if (!dataFolder.exists()) //noinspection ResultOfMethodCallIgnored
            dataFolder.mkdirs();

        File permanentDataFile = new File(dataFolder, player.getUniqueId() + ".yml");

        try {
            if (!permanentDataFile.exists()) //noinspection ResultOfMethodCallIgnored
                permanentDataFile.createNewFile();

            permanentData.options().copyHeader(true);
            permanentData.save(permanentDataFile);

        } catch (IOException ex) {
            logger.severe("Unable to save " + player.getName() + "'s permanent player data! \n" +
                    ex.getMessage()); //Do nothing, as we cannot save.
        }


    }

    @Override
    public void freezePortalViews() {
        viewsFrozen = true;
    }

    private void setViewing(IPortal portal) {
        portalViews.put(portal, playerPortalViewFactory.create(player, portal));
    }

    private void setNotViewing(IPortal portal) {
        portalViews.remove(portal).onDeactivate(false);
    }

    private YamlConfiguration loadPermanentDataYml() {

        File dataFolder = new File(pl.getDataFolder(), "playerData");

        if (!dataFolder.exists()) //noinspection ResultOfMethodCallIgnored
            dataFolder.mkdirs();

        File permanentDataFile = new File(dataFolder, player.getUniqueId() + ".yml");

        try {
            if (!permanentDataFile.exists()) //noinspection ResultOfMethodCallIgnored
                permanentDataFile.createNewFile();

            YamlConfiguration permanentDataYml = createDefaultDataFile(permanentDataFile);
            permanentDataYml.save(permanentDataFile);
            return permanentDataYml;

        } catch (IOException ex) {
            logger.severe("Unable to load " + player.getName() + "'s permanent player data! " +
                    "Default data will be used. \n" + ex.getMessage());

            return createDefaultDataFile(permanentDataFile); //We don't save anything, and this is kept in memory.

        }

    }

    private YamlConfiguration createDefaultDataFile(File permanentDataFile) {
        YamlConfiguration permanentDataYml = YamlConfiguration.loadConfiguration(permanentDataFile);
        permanentDataYml.addDefault("seeThroughPortal", true);
        permanentDataYml.options().copyDefaults(true);
        return permanentDataYml;
    }
}
