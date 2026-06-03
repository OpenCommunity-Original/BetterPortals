package org.envel.betterportals.bukkit.portal.spawning;

import org.envel.betterportals.bukkit.chunk.chunkpos.ChunkPosition;
import org.envel.betterportals.bukkit.chunk.chunkpos.SpiralChunkAreaIterator;
import org.envel.betterportals.bukkit.config.PortalSpawnConfig;
import org.envel.betterportals.shared.logging.Logger;
import org.envel.betterportals.bukkit.util.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Handles checking for existing/new spawn position across multiple ticks.
 * This is done to avoid freezing the server, since checking for a spawn position can take a couple of seconds
 * This class also contains various optimisations that improve the speed by allowing us to skip certain chunks
 */
public class AsyncPortalChecker implements Runnable {
    /**
     * Used to skip this chunk if there's no way that any block in the chunk can be any closer.
     * We approximate this by finding the distance to the chunk center, then subtracting this value.
     * Theoretically, this could skip a portal and not actually find the closest portal.
     * However, I haven't seen any get missed.
     * Ideally, the chunk skip distance would be nearly 150 - half of the the diagonal length of a chunk. However, this slows down portal spawning quite a lot
     */
    private static final double CHUNK_SKIP_DISTANCE = 45.0;

    /**
     * Radius for existing portal checking/portal spawning.
     */
    private static final double PORTAL_SEARCH_RADIUS = 128.0;

    private final Logger logger;
    private final PortalSpawningContext context;
    private final PortalSpawnConfig config;

    private final Iterator<ChunkPosition> iterator;
    private final IChunkChecker chunkChecker;
    private final Consumer<PortalSpawnPosition> onFinish;
    private SchedulerUtil.PortalTask repeatingTask;

    private PortalSpawnPosition currentClosest;
    private double closestDistance = Double.POSITIVE_INFINITY;

    private int updateCount;

    /**
     * Starts checking for portals in a spiral area
     * @param context Position/size to check for and {@link org.envel.betterportals.bukkit.config.WorldLink} to use.
     * @param chunkChecker Used to find the closest {@link PortalSpawnPosition} in each chunk.
     * @param onFinish Called with the spawn position (may be null), when this check is complete
     */
    public AsyncPortalChecker(PortalSpawningContext context, IChunkChecker chunkChecker,
                              Consumer<PortalSpawnPosition> onFinish, JavaPlugin pl, Logger logger, PortalSpawnConfig config) {
        this.logger = logger;
        this.context = context;
        this.config = config;
        this.chunkChecker = chunkChecker;
        this.onFinish = onFinish;

        // Make an iterator around the chunks of the portal search radius from our destination position
        Location spawnPos = context.getPreferredLocation();
        this.iterator = new SpiralChunkAreaIterator(
            spawnPos.clone().subtract(PORTAL_SEARCH_RADIUS, 0.0, PORTAL_SEARCH_RADIUS),
            spawnPos.clone().add(PORTAL_SEARCH_RADIUS, 0.0, PORTAL_SEARCH_RADIUS)
        );

        if (SchedulerUtil.isFolia()) {
            this.repeatingTask = null;
            List<ChunkPosition> chunksToCheck = new ArrayList<>();
            while (iterator.hasNext()) {
                chunksToCheck.add(iterator.next());
            }
            int totalChunks = chunksToCheck.size();
            if (totalChunks == 0) {
                onFinish.accept(null);
                return;
            }
            AtomicInteger completedCount = new AtomicInteger(0);
            for (ChunkPosition chunk : chunksToCheck) {
                SchedulerUtil.runAtLocation(spawnPos.getWorld(), chunk.x << 4, chunk.z << 4, () -> {
                    try {
                        double closestTheoreticalDistanceInChunk = chunk.getCenterPos().distance(context.getPreferredLocation()) - CHUNK_SKIP_DISTANCE;
                        boolean shouldCheck;
                        synchronized (this) {
                            shouldCheck = closestTheoreticalDistanceInChunk < closestDistance;
                        }
                        if (shouldCheck) {
                            PortalSpawnPosition result = chunkChecker.findClosestInChunk(chunk, context);
                            if (result != null) {
                                double distance = result.getPosition().distance(context.getPreferredLocation());
                                synchronized (this) {
                                    if (distance < closestDistance) {
                                        closestDistance = distance;
                                        currentClosest = result;
                                    }
                                }
                            }
                        }
                    } finally {
                        if (completedCount.incrementAndGet() == totalChunks) {
                            SchedulerUtil.runTask(this::onFinish);
                        }
                    }
                });
            }
        } else {
            this.repeatingTask = SchedulerUtil.runTaskTimer(this, 1, 1);
        }
    }

    @Override
    public void run() {
        updateCount += 1;

        // Using System.nanoTime() directly avoids Instant object allocations in the tight loop
        long startNs = System.nanoTime();
        long allowedNs = (long) (config.getAllowedSpawnTimePerTick() * 1_000_000L);

        while (System.nanoTime() - startNs < allowedNs) {
            if (!iterator.hasNext()) {
                onFinish();
                return;
            }

            checkChunk(iterator.next());
        }
    }

    private void onFinish() {
        logger.fine("Finished delayed portal check within %d ticks", updateCount);
        if(repeatingTask != null) {
            repeatingTask.cancel();
        }
        onFinish.accept(currentClosest);
    }

    // Checks this chunk to see if there are any valid positions in it closer than our current closest
    private void checkChunk(ChunkPosition chunk) {
        // Perform the rough check specified in the comment for CHUNK_SKIP_DISTANCE
        double closestTheoreticalDistanceInChunk = chunk.getCenterPos().distance(context.getPreferredLocation()) - CHUNK_SKIP_DISTANCE;
        if(closestTheoreticalDistanceInChunk > closestDistance) {
            return;
        }

        PortalSpawnPosition result = chunkChecker.findClosestInChunk(chunk, context);
        if(result == null) {return;} // Some chunks might not have any valid positions

        double distance = result.getPosition().distance(context.getPreferredLocation());
        if(distance < closestDistance) {
            closestDistance = distance;
            currentClosest = result;
        }
    }
}
