package org.envel.betterportals.bukkit.chunk.generation;

import org.envel.betterportals.bukkit.chunk.chunkpos.ChunkPosition;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public interface IChunkGenerationChecker {
    boolean isChunkGenerated(@NotNull World world, int x, int z);

    default boolean isChunkGenerated(@NotNull ChunkPosition chunk) {
        return isChunkGenerated(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }
}
