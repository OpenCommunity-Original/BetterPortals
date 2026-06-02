package org.envel.betterportals.bukkit.block.fetch;

import org.envel.betterportals.api.IntVector;
import org.envel.betterportals.bukkit.portal.IPortal;
import org.envel.betterportals.bukkit.config.RenderConfig;
import org.envel.betterportals.bukkit.util.SchedulerUtil;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local portals already have their blocks accessible.
 * On Folia, we fetch the blocks asynchronously from the destination region thread to avoid cross-region thread checks.
 */
public class LocalBlockDataFetcher implements IBlockDataFetcher {
    private final IPortal portal;
    private final World destinationWorld;
    private final RenderConfig renderConfig;

    private final Map<IntVector, BlockData> cachedStates = new ConcurrentHashMap<>();
    private final AtomicBoolean isFetching = new AtomicBoolean(false);
    private volatile boolean isReady = false;

    public LocalBlockDataFetcher(IPortal portal, RenderConfig renderConfig) {
        this.portal = portal;
        this.destinationWorld = portal.getDestPos().getWorld();
        this.renderConfig = renderConfig;
    }

    @Override
    public void update() {
        if (!SchedulerUtil.isFolia()) {
            return;
        }

        if (destinationWorld != null && isFetching.compareAndSet(false, true)) {
            org.envel.betterportals.api.IntVector destIntPos = portal.getDestPos().getIntVector();
            int destX = destIntPos.getX();
            int destZ = destIntPos.getZ();

            SchedulerUtil.runAtLocation(destinationWorld, destX, destZ, () -> {
                try {
                    int maxXZ = (int) renderConfig.getMaxXZ();
                    int maxY = (int) renderConfig.getMaxY();
                    IntVector center = new IntVector(portal.getDestPos().getVector());

                    for (int x = -maxXZ; x <= maxXZ; x++) {
                        for (int z = -maxXZ; z <= maxXZ; z++) {
                            for (int y = -maxY; y <= maxY; y++) {
                                IntVector relPos = new IntVector(x, y, z);
                                IntVector absPos = center.add(relPos);
                                try {
                                    BlockData data = destinationWorld.getBlockData(absPos.getX(), absPos.getY(), absPos.getZ());
                                    cachedStates.put(absPos, data);
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                    isReady = true;
                } finally {
                    isFetching.set(false);
                }
            });
        }
    }

    @Override
    public boolean isReady() {
        if (SchedulerUtil.isFolia()) {
            return isReady;
        }
        return true;
    }

    @Override
    public @NotNull BlockData getData(@NotNull IntVector position) {
        if (SchedulerUtil.isFolia()) {
            BlockData data = cachedStates.get(position);
            if (data == null) {
                return org.bukkit.Bukkit.createBlockData(org.bukkit.Material.AIR);
            }
            return data;
        }
        return position.getBlock(destinationWorld).getBlockData();
    }
}
