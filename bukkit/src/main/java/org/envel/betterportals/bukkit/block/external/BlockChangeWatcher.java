package org.envel.betterportals.bukkit.block.external;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.envel.betterportals.api.IntVector;
import org.envel.betterportals.bukkit.math.Matrix;
import org.envel.betterportals.bukkit.net.requests.GetBlockDataChangesRequest;
import org.envel.betterportals.bukkit.nms.BlockDataUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class BlockChangeWatcher implements IBlockChangeWatcher  {
    private final IntVector center;
    private final Matrix rotationMatrix;
    private World world;

    private final int xAndZRadius;
    private final int yRadius;

    private final Map<IntVector, BlockData> previousData = new HashMap<>();

    @Inject
    public BlockChangeWatcher(@Assisted GetBlockDataChangesRequest request) {
        this.center = request.getPosition();
        this.rotationMatrix = request.getRotateOriginToDest();
        this.xAndZRadius = request.getXAndZRadius();
        this.yRadius = request.getYRadius();
        this.world = Bukkit.getWorld(request.getWorldId());
        if(world == null) {
            this.world = Bukkit.getWorld(request.getWorldName());
        }
    }

    public @NotNull Map<IntVector, Integer> checkForChanges() {
        Map<IntVector, Integer> result = new HashMap<>();

        for(int x = -xAndZRadius; x <= xAndZRadius ; x++) {
            for(int z = -xAndZRadius; z <= xAndZRadius; z++) {
                for(int y = -yRadius; y <= yRadius; y++) {
                    IntVector blockPos = rotationMatrix.transform(new IntVector(x, y, z)).add(center);

                    BlockData data = blockPos.getBlock(world).getBlockData();
                    BlockData oldData = previousData.get(blockPos);

                    if(!data.equals(oldData)) {
                        result.put(blockPos, BlockDataUtil.getCombinedId(data));
                        previousData.put(blockPos, data);
                    }
                }
            }
        }

        return result;
    }
}
