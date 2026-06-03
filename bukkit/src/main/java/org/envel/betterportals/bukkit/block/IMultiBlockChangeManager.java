package org.envel.betterportals.bukkit.block;

import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.google.inject.assistedinject.Assisted;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Abstracts the differences in sending multi block change packets in different versions.
 */
public interface IMultiBlockChangeManager {
    /**
     * Adds a new change to the map via a {@link IViewableBlockInfo}. The origin data of the block will be used.
     * @param position Position relative to the world that the player is in
     * @param newData The new block data that the player will see. Each overrider will specify a different {@link IViewableBlockInfo} subtype that allows them to fetch the data.
     */
    void addChangeOrigin(Vector position, IViewableBlockInfo newData);

    /**
     * Adds a new change to the map via a {@link IViewableBlockInfo}. The rendered destination data of this block will be used
     * @param position Position relative to the world that the player is in
     * @param newData The new block data that the player will see. Each overrider will specify a different {@link IViewableBlockInfo} subtype that allows them to fetch the data.
     */
    void addChangeDestination(Vector position, IViewableBlockInfo newData);

    /**
     * Adds a new change to the map.
     * @param position Position relative to the world that the player is in
     * @param newData The new block data that the player will see.
     */
    void addChange(Vector position, WrappedBlockData newData);

    /**
     * Sends all queued changes.
     * Does <i>not</i> clear changes.
     */
    void sendChanges();

    interface Factory {
        IMultiBlockChangeManager create(Player player, @Assisted("minChunkY") int minChunkY, @Assisted("maxChunkY") int maxChunkY);
    }
}
