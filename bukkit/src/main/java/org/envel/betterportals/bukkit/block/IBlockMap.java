package org.envel.betterportals.bukkit.block;

import com.comphenix.protocol.events.PacketContainer;
import org.envel.betterportals.api.IntVector;
import org.envel.betterportals.bukkit.config.RenderConfig;
import org.envel.betterportals.bukkit.portal.IPortal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Creates a map of the blocks around the portal within the view distance.
 * Implements skipping of blocks that are fully covered by opaque blocks.
 * Implements skipping of blocks that are the same at the origin and destination.
 */
public interface IBlockMap {
    /**
     * Updates the block array, and with the interval defined in {@link RenderConfig}
     * The first time this is called, it takes quite a bit longer than afterwards
     * @param ticksSinceActivated Ticks since the portal became viewable to one player/
     */
    void update(int ticksSinceActivated);

    /**
     * Gets the current list of viewable positions.
     * @return The current list of viewable positions and their data, or null if no list is currently available
     */
    @Nullable List<IViewableBlockInfo> getViewableStates();


    /**
     * Finds if the origin block stored at <code>position</code> is mapped as a tile entity.
     * If it is, the pre-fetched (on the main thread) packet used to set the data of this entity is returned.
     * Otherwise, <code>null</code> is returned
     * @param position The absolute position of the tile entity
     * @return The packet used to update.
     */
    @Nullable PacketContainer getOriginTileEntityPacket(@NotNull IntVector position);

    /**
     * Finds if the destination block stored at <code>position</code> is mapped as a tile entity.
     * If it is, the pre-fetched (on the main thread) packet used to set the data of this entity is returned.
     * Otherwise, <code>null</code> is returned
     * @param position The absolute position of the tile entity
     * @return The packet used to update.
     */
    @Nullable PacketContainer getDestinationTileEntityPacket(@NotNull IntVector position);


    /**
     * Clears the currently rendered array to save memory.
     * Called on portal deactivation.
     * Next time {@link IBlockMap#update(int)} is called, another initial update will be done, which takes longer.
     */
    void reset();

    interface Factory {
        IBlockMap create(IPortal portal);
    }
}
