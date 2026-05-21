package com.lauriethefish.betterportals.bukkit.block.bukkit;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.lauriethefish.betterportals.bukkit.nms.PacketUtil;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.lauriethefish.betterportals.bukkit.block.IMultiBlockChangeManager;
import com.lauriethefish.betterportals.bukkit.block.IViewableBlockInfo;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages sending multiple block changes to a player using chunk-section-based MULTI_BLOCK_CHANGE packets.
 * This is the modern format introduced in 1.16.2 and used up through 1.21+.
 */
public class ModernMultiBlockChangeManager implements IMultiBlockChangeManager {
    private final Player player;

    private final int minChunkY;
    private final int maxChunkY;

    // Section positions are indexed using BlockPositions in ProtocolLib
    private final HashMap<BlockPosition, Map<Vector, WrappedBlockData>> changes = new HashMap<>();

    @Inject
    public ModernMultiBlockChangeManager(@Assisted Player player, @Assisted("minChunkY") int minChunkY, @Assisted("maxChunkY") int maxChunkY) {
        this.player = player;
        this.minChunkY = minChunkY;
        this.maxChunkY = maxChunkY;
    }

    @Override
    public void addChange(Vector position, WrappedBlockData newData) {
        BlockPosition sectionPosition = new BlockPosition(
                position.getBlockX() >> 4,
                position.getBlockY() >> 4,
                position.getBlockZ() >> 4
        );

        // Create/get the list for this chunk section
        Map<Vector, WrappedBlockData> existingList = changes.computeIfAbsent(sectionPosition, k -> new HashMap<>());
        existingList.put(position, newData);
    }

    @Override
    public void addChangeOrigin(Vector position, IViewableBlockInfo newData) {
        addChange(position, ((BukkitBlockInfo) newData).getOriginData());
    }

    @Override
    public void addChangeDestination(Vector position, IViewableBlockInfo newData) {
        addChange(position, ((BukkitBlockInfo) newData).getRenderedDestData());
    }

    private short getShortLocation(Vector vec) {
        int x = vec.getBlockX() & 0xF;
        int y = vec.getBlockY() & 0xF;
        int z = vec.getBlockZ() & 0xF;

        return (short) (x << 8 | z << 4 | y);
    }

    @Override
    public void sendChanges() {
        // Each chunk position needs a different packet
        for(Map.Entry<BlockPosition, Map<Vector, WrappedBlockData>> entry : changes.entrySet()) {
            PacketContainer packet = new PacketContainer(PacketType.Play.Server.MULTI_BLOCK_CHANGE);
            int chunkY = entry.getKey().getY();
            if(chunkY > maxChunkY || chunkY < minChunkY) {
                continue;
            }

            // Write the correct chunk section
            packet.getSectionPositions().write(0, entry.getKey());

            // Add each changed block in the chunk
            int blockCount = entry.getValue().size();
            WrappedBlockData[] data = new WrappedBlockData[blockCount];
            short[] positions = new short[blockCount];
            int i = 0;
            for(Map.Entry<Vector, WrappedBlockData> blockEntry : entry.getValue().entrySet()) {
                positions[i] = getShortLocation(blockEntry.getKey());
                data[i] = blockEntry.getValue();
                i++;
            }

            packet.getBlockDataArrays().writeSafely(0, data);
            packet.getShortArrays().writeSafely(0, positions);

            PacketUtil.sendPacket(player, packet);
        }
    }
}
