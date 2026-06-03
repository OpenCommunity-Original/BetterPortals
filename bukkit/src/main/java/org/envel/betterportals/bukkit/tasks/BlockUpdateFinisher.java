package org.envel.betterportals.bukkit.tasks;

import org.envel.betterportals.bukkit.player.view.block.PlayerBlockView;
import org.envel.betterportals.shared.logging.Logger;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Handles doing the final processing for portal block updates on another thread
 * Doing tons of raycasts to find which blocks are visible is moderately expensive, so happens on another thread
 */
public abstract class BlockUpdateFinisher {
    private enum BlockViewUpdateType {
        REGULAR, // Update blocks
        REFRESH, // Update and refresh all blocks
        RESET // Reset all blocks
    }

    private static class BlockViewUpdateInfo {
        PlayerBlockView blockView;
        BlockViewUpdateType type;
        public BlockViewUpdateInfo(PlayerBlockView blockView, BlockViewUpdateType type) {
            this.blockView = blockView;
            this.type = type;
        }

        // Equals only requires the BlockViewUpdateInfo to be equal - used to check if an update is already pending
        @Override
        public boolean equals(Object other) {
            if(!(other instanceof BlockViewUpdateInfo)) {return false;}

            return blockView == ((BlockViewUpdateInfo) other).blockView;
        }
    }

    private final BlockingQueue<BlockViewUpdateInfo> updateQueue = new LinkedBlockingQueue<>();
    protected final Logger logger;

    private volatile boolean hasStopped = false;

    protected BlockUpdateFinisher(Logger logger) {
        this.logger = logger;
    }

    private void processUpdate(BlockViewUpdateInfo next) {
        if(next.type == BlockViewUpdateType.RESET) {
            logger.fine("Running scheduled reset");
            next.blockView.finishReset();
        }   else    {
            next.blockView.finishUpdate(next.type == BlockViewUpdateType.REFRESH);
        }
    }

    protected void processUpdatesContinually()  {
        try {
            while (!hasStopped) {
                BlockViewUpdateInfo next = updateQueue.take();
                processUpdate(next);
            }
        }   catch(InterruptedException ignored) { }
    }

    protected void finishPendingUpdates() {
        while(true) {
            BlockViewUpdateInfo next = updateQueue.poll();
            if(next == null) {return;}

            processUpdate(next);
        }
    }

    /**
     * Starts the task/thread that is being used
     */
    public void start() {
        hasStopped = false;
    }

    /**
     * Stops the task/thread that is being used
     */
    public void stop() {
        hasStopped = true;
        updateQueue.clear();
    }

    /**
     * Schedules the update for <code>blockView</code> to happen on another thread.
     * @param blockView The block view to be updated
     * @param refresh Whether to resend all block states regardless of if they were already sent
     */
    public void scheduleUpdate(PlayerBlockView blockView, boolean refresh) {
        BlockViewUpdateInfo updateInfo = new BlockViewUpdateInfo(blockView, refresh ? BlockViewUpdateType.REFRESH : BlockViewUpdateType.REGULAR);

        if(updateQueue.contains(updateInfo)) {
            logger.fine("Block update was scheduled when previous update had not finished. Server is running behind!");
            return;
        }

        try {
            updateQueue.put(updateInfo);
        }   catch(InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Schedules a block reset which will call {@link PlayerBlockView#finishReset()} when ran.
     * This will remove any block updates currently queued for this view
     * @param blockView The block view to reset
     */
    public void scheduleReset(PlayerBlockView blockView) {
        try {
            BlockViewUpdateInfo updateInfo = new BlockViewUpdateInfo(blockView, BlockViewUpdateType.RESET);

            // Remove any updates pending to update the given view
            updateQueue.remove(updateInfo);

            // Now we can schedule our reset
            updateQueue.put(updateInfo);
        }   catch(InterruptedException ex) {
            ex.printStackTrace();
        }
    }
}
