package org.envel.betterportals.bukkit.player.view.block;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.envel.betterportals.bukkit.block.IViewableBlockInfo;
import org.envel.betterportals.bukkit.block.IMultiBlockChangeManager;
import org.envel.betterportals.bukkit.portal.IPortal;
import org.envel.betterportals.shared.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerBlockStates implements IPlayerBlockStates {
    private final Player player;
    private final IMultiBlockChangeManager.Factory multiBlockChangeManagerFactory;
    private final Logger logger;
    private final IPortal portal; // Assisted from the view context

    // Global registry: Player UUID -> (Block Position -> (Portal -> Block Info))
    private static final Map<UUID, Map<Vector, Map<IPortal, IViewableBlockInfo>>> globalViewedStates = new ConcurrentHashMap<>();

    @Inject
    public PlayerBlockStates(@Assisted Player player, @Assisted IPortal portal, IMultiBlockChangeManager.Factory multiBlockChangeManagerFactory, Logger logger) {
        this.player = player;
        this.portal = portal;
        this.multiBlockChangeManagerFactory = multiBlockChangeManagerFactory;
        this.logger = logger;
    }

    private Map<Vector, Map<IPortal, IViewableBlockInfo>> getPlayerStates() {
        return globalViewedStates.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
    }

    @Override
    public void resetAndUpdate(int minChunkY, int maxChunkY) {
        Map<Vector, Map<IPortal, IViewableBlockInfo>> playerStates = getPlayerStates();
        if (playerStates.isEmpty()) {
            return;
        }

        IMultiBlockChangeManager multiBlockChangeManager = multiBlockChangeManagerFactory.create(player, minChunkY, maxChunkY);
        int resetCount = 0;

        for (Map.Entry<Vector, Map<IPortal, IViewableBlockInfo>> entry : playerStates.entrySet()) {
            Vector pos = entry.getKey();
            Map<IPortal, IViewableBlockInfo> portalMap = entry.getValue();
            
            // Remove this portal's claim on the block
            IViewableBlockInfo removedInfo = portalMap.remove(portal);
            if (removedInfo != null) {
                resetCount++;
                // If no other portal is rendering this block, restore the original block
                if (portalMap.isEmpty()) {
                    multiBlockChangeManager.addChangeOrigin(pos, removedInfo);
                    playerStates.remove(pos);
                } else {
                    // Otherwise, render the block state from the next available portal
                    IViewableBlockInfo nextBlock = portalMap.values().iterator().next();
                    multiBlockChangeManager.addChangeDestination(pos, nextBlock);
                }
            }
        }

        if (resetCount > 0) {
            logger.finest("Resetting %d blocks for portal", resetCount);
            if (org.envel.betterportals.bukkit.util.SchedulerUtil.isFolia()) {
                org.envel.betterportals.bukkit.util.SchedulerUtil.runForEntity(player, multiBlockChangeManager::sendChanges);
            } else {
                multiBlockChangeManager.sendChanges();
            }
        }

        // Clean up memory if player has no active portal states
        if (playerStates.isEmpty()) {
            globalViewedStates.remove(player.getUniqueId());
        }
    }

    @Override
    public boolean setViewable(Vector position, IViewableBlockInfo block) {
        Map<Vector, Map<IPortal, IViewableBlockInfo>> playerStates = getPlayerStates();
        Map<IPortal, IViewableBlockInfo> portalMap = playerStates.computeIfAbsent(position, k -> new ConcurrentHashMap<>());
        
        IViewableBlockInfo old = portalMap.put(portal, block);
        
        // Return true only if this block was NOT previously visible through ANY portal
        // (to determine if we need to send a packet)
        return old == null && portalMap.size() == 1;
    }

    @Override
    public boolean setNonViewable(Vector position, IViewableBlockInfo block) {
        Map<Vector, Map<IPortal, IViewableBlockInfo>> playerStates = getPlayerStates();
        Map<IPortal, IViewableBlockInfo> portalMap = playerStates.get(position);
        if (portalMap == null) {
            return false;
        }

        boolean removed = portalMap.remove(portal, block);
        if (removed && portalMap.isEmpty()) {
            playerStates.remove(position);
            return true; // We removed the last portal claiming this block -> needs update to restore origin
        }
        return false; // Still visible through other portals
    }

    // Clean up registry on logout to prevent memory leaks
    public static void clearPlayer(UUID uuid) {
        globalViewedStates.remove(uuid);
    }
}
