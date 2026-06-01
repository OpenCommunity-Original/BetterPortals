package com.lauriethefish.betterportals.bukkit.player;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.lauriethefish.betterportals.bukkit.config.ProxyConfig;
import com.lauriethefish.betterportals.bukkit.events.IEventRegistrar;
import com.lauriethefish.betterportals.bukkit.net.requests.GetSelectionRequest;
import com.lauriethefish.betterportals.bukkit.portal.selection.ISelectionManager;
import com.lauriethefish.betterportals.bukkit.portal.selection.IPortalSelection;
import com.lauriethefish.betterportals.shared.logging.Logger;
import com.lauriethefish.betterportals.shared.net.requests.TeleportRequest;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Singleton
public class PlayerDataManager implements IPlayerDataManager, Listener   {
    private final Logger logger;
    private final IPlayerData.Factory playerDataFactory;
    private final Map<UUID, IPlayerData> players = new HashMap<>();
    private final ProxyConfig proxyConfig;

    private final Map<UUID, TeleportRequest> pendingTeleportOnJoin = new HashMap<>();
    private final Map<UUID, GetSelectionRequest.ExternalSelectionInfo> pendingSelectionOnJoin = new HashMap<>();
    /**
     * Used to retain selections throughout logouts.
     */
    private final Map<UUID, ISelectionManager> loggedOutPlayerSelections = new HashMap<>();

    @Inject
    public PlayerDataManager(IEventRegistrar eventRegistrar, Logger logger, IPlayerData.Factory playerDataFactory, ProxyConfig proxyConfig) {
        this.logger = logger;
        this.playerDataFactory = playerDataFactory;
        this.proxyConfig = proxyConfig;

        addExistingPlayers();
        eventRegistrar.register(this);
    }

    private void addExistingPlayers() {
        for(Player player : Bukkit.getOnlinePlayers()) {
            players.put(player.getUniqueId(), playerDataFactory.create(player));
        }
    }

    @Override
    public @NotNull Collection<IPlayerData> getPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    @Override
    public @Nullable IPlayerData getPlayerData(@NotNull Player player) {
        return players.get(player.getUniqueId());
    }
    @Override
    public void onPluginDisable() {
        players.values().forEach(IPlayerData::onPluginDisable);
        players.clear();
        pendingTeleportOnJoin.clear();
        pendingSelectionOnJoin.clear();
        loggedOutPlayerSelections.clear();
    }

    @Override
    public void onPluginReload() {
        players.values().forEach(IPlayerData::onPluginDisable);
        players.clear();
        pendingTeleportOnJoin.clear();
        pendingSelectionOnJoin.clear();
        loggedOutPlayerSelections.clear();
        addExistingPlayers();
    }

    @Override
    public void setTeleportOnJoin(TeleportRequest request) {
        pendingTeleportOnJoin.put(request.getPlayerId(), request);
    }

    @Override
    public void setExternalSelectionOnLogin(UUID uniqueId, GetSelectionRequest.ExternalSelectionInfo selection) {
        Player player = Bukkit.getPlayer(uniqueId);
        if(player != null) {
            logger.fine("Directly setting external selection for player with ID %s", uniqueId);
            IPlayerData data = players.get(uniqueId);
            if (data != null) {
                data.getSelection().setExternalSelection(selection);
            }
        }   else    { // If the player is not online yet, add it to this map so that it will be set when they log in
            logger.fine("Setting external selection to pending for player with ID %s", uniqueId);
            pendingSelectionOnJoin.put(uniqueId, selection);
        }
    }

    @Override
    public @Nullable IPortalSelection getDestinationSelectionWhenLoggedOut(UUID uniqueId) {
        Player player = Bukkit.getPlayer(uniqueId);
        if(player != null) {
            IPlayerData data = players.get(uniqueId);
            return data != null ? data.getSelection().getDestSelection() : null;
        }   else    {
            ISelectionManager selection = loggedOutPlayerSelections.get(uniqueId);
            if(selection == null) {
                if(proxyConfig.isWarnOnMissingSelection()) {
                    logger.warning("No selection found for player with unique ID %s. (selection check triggered by server switch, selection must be mirrored to the destination server). Is UUID forwarding disabled?", uniqueId);
                }
                return null;
            }

            return selection.getDestSelection();
        }
    }

    // Add/remove players upon joining and leaving
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        logger.fine("Registering player data on join for player: %s", playerId);
        IPlayerData playerData = playerDataFactory.create(event.getPlayer());
        players.put(playerId, playerData);

        TeleportRequest teleportOnJoin = pendingTeleportOnJoin.remove(playerId);
        if(teleportOnJoin != null) {
            processTeleportOnJoin(event.getPlayer(), teleportOnJoin);
        }

        ISelectionManager selectionManager = loggedOutPlayerSelections.remove(playerId);
        if(selectionManager != null) {
            logger.fine("Restoring selection on join");
            playerData.setSelection(selectionManager);
        }
        playerData.getSelection().setExternalSelection(pendingSelectionOnJoin.get(playerId));
    }

    private void processTeleportOnJoin(@NotNull Player player, @NotNull TeleportRequest request) {
        World world = Bukkit.getWorld(request.getDestWorldId());
        if(world == null) {
            world = Bukkit.getWorld(request.getDestWorldName());
        }

        Location destinationPosition = new Location(
                world,
                request.getDestX(),
                request.getDestY(),
                request.getDestZ(),
                request.getDestYaw(),
                request.getDestPitch()
        );

        Vector destinationVelocity = new Vector(
                request.getDestVelX(),
                request.getDestVelY(),
                request.getDestVelZ()
        );

        player.teleportAsync(destinationPosition);
        player.setVelocity(destinationVelocity);

        player.setFlying(request.isFlying());
        player.setGliding(request.isGliding());
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        logger.fine("Saving selection on leave");
        UUID playerId = event.getPlayer().getUniqueId();
        IPlayerData playerData = players.get(playerId);
        if(playerData == null) {
            logger.warning("Player left with no registered data. This should not happen!");
            return;
        }

        loggedOutPlayerSelections.put(playerId, playerData.getSelection());

        // Prevent memory leak by removing offline selection after 5 minutes if player hasn't rejoined
        com.lauriethefish.betterportals.bukkit.util.SchedulerUtil.runTaskLater(() -> {
            if (Bukkit.getPlayer(playerId) == null) {
                loggedOutPlayerSelections.remove(playerId);
                logger.fine("Cleaned up expired selection for offline player %s", playerId);
            }
        }, 6000L);

        logger.fine("Unregistering player data on leave for player: %s", playerId);
        players.remove(playerId);
        playerData.onLogout();
    }
}
