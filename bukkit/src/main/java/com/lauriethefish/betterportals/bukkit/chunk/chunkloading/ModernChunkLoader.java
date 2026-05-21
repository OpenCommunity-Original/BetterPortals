package com.lauriethefish.betterportals.bukkit.chunk.chunkloading;

import com.lauriethefish.betterportals.bukkit.chunk.chunkpos.ChunkPosition;
import com.lauriethefish.betterportals.bukkit.util.SchedulerUtil;
import org.bukkit.Chunk;
import org.jetbrains.annotations.NotNull;

import jakarta.inject.Singleton;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class ModernChunkLoader implements IChunkLoader {
    // Used to only unforceload chunks if they are *loaded by the plugin*
    private final Set<ChunkPosition> loadedChunks = ConcurrentHashMap.newKeySet();

    @Override
    public void setForceLoaded(Chunk chunk) {
        SchedulerUtil.runAtLocation(chunk.getWorld(), chunk.getX() << 4, chunk.getZ() << 4, () -> {
            chunk.setForceLoaded(true);
            loadedChunks.add(new ChunkPosition(chunk));
        });
    }

    @Override
    public void setNotForceLoaded(@NotNull ChunkPosition chunk) {
        if(loadedChunks.remove(chunk)) {
            // Do it this way to avoid loading the chunk by calling getChunk
            SchedulerUtil.runAtLocation(chunk.getWorld(), chunk.x << 4, chunk.z << 4, () -> {
                chunk.getWorld().setChunkForceLoaded(chunk.x, chunk.z, false);
            });
        }
    }

    @Override
    public boolean isForceLoaded(@NotNull ChunkPosition chunk) {
        return loadedChunks.contains(chunk);
    }   
}
