package org.envel.betterportals.bukkit.chunk.chunkloading;

import org.envel.betterportals.api.PortalPosition;
import org.jetbrains.annotations.NotNull;

public interface IPortalChunkLoader {
    void forceloadPortalChunks(@NotNull PortalPosition destPosition);
    void unforceloadPortalChunks(@NotNull PortalPosition destPosition);
}
