package org.envel.betterportals.bukkit.player.view.block;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.envel.betterportals.bukkit.block.IViewableBlockInfo;
import org.envel.betterportals.bukkit.block.IMultiBlockChangeManager;
import org.envel.betterportals.shared.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class PlayerBlockStates implements IPlayerBlockStates {
    private final Player player;
    private final IMultiBlockChangeManager.Factory multiBlockChangeManagerFactory;
    private final Logger logger;

    private final Map<Vector, IViewableBlockInfo> viewedStates = new HashMap<>();

    @Inject
    public PlayerBlockStates(@Assisted Player player, IMultiBlockChangeManager.Factory multiBlockChangeManagerFactory, Logger logger) {
        this.player = player;
        this.multiBlockChangeManagerFactory = multiBlockChangeManagerFactory;
        this.logger = logger;
    }

    @Override
    public void resetAndUpdate(int minChunkY, int maxChunkY) {
        if(viewedStates.size() == 0) {
            logger.finer("No states to reset!");
            return;
        }

        // Use a MultiBlockChangeManager to actually send the changes
        IMultiBlockChangeManager multiBlockChangeManager = multiBlockChangeManagerFactory.create(player, minChunkY, maxChunkY);

        logger.finest("Resetting %d blocks", viewedStates.size());
        for(Map.Entry<Vector, IViewableBlockInfo> entry : viewedStates.entrySet()) {
            multiBlockChangeManager.addChangeOrigin(entry.getKey(), entry.getValue());
        }
        multiBlockChangeManager.sendChanges();

        viewedStates.clear();
    }

    @Override
    public boolean setViewable(Vector position, IViewableBlockInfo block) {
        return viewedStates.put(position, block) == null;
    }

    @Override
    public boolean setNonViewable(Vector position, IViewableBlockInfo block) {
        return viewedStates.remove(position, block);
    }
}
