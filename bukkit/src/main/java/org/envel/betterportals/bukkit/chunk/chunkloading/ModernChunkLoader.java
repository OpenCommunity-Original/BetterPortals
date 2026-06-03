package org.envel.betterportals.bukkit.chunk.chunkloading;

import org.envel.betterportals.bukkit.chunk.chunkpos.ChunkPosition;
import org.envel.betterportals.bukkit.util.SchedulerUtil;
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
        SchedulerUtil.runTask(() -> {
            chunk.setForceLoaded(true);
            loadedChunks.add(new ChunkPosition(chunk));
        });
    }

    @Override
    public void setForceLoaded(ChunkPosition chunk) {
        SchedulerUtil.runTask(() -> {
            chunk.getWorld().setChunkForceLoaded(chunk.x, chunk.z, true);
            loadedChunks.add(chunk);
        });
    }

    @Override
    public void setNotForceLoaded(@NotNull ChunkPosition chunk) {
        if(loadedChunks.remove(chunk)) {
            // Do it this way to avoid loading the chunk by calling getChunk
            SchedulerUtil.runTask(() -> {
                chunk.getWorld().setChunkForceLoaded(chunk.x, chunk.z, false);
            });
        }
    }

    @Override
    public boolean isForceLoaded(@NotNull ChunkPosition chunk) {
        return loadedChunks.contains(chunk);
    }   
}
