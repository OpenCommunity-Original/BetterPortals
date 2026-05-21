package com.lauriethefish.betterportals.bukkit.nms;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.lauriethefish.betterportals.api.IntVector;
import com.lauriethefish.betterportals.shared.util.ReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Arrays;

public class BlockDataUtil {
    private static Method GET_STATE;
    private static Method GET_ID;
    private static Method STATE_BY_ID;
    private static Method FROM_DATA;
    private static final BlockData DEFAULT_BLOCK_DATA;

    static {
        DEFAULT_BLOCK_DATA = Bukkit.createBlockData(Material.AIR);
        try {
            Class<?> craftBlockDataClass = CraftBukkitClassUtil.findCraftBukkitClass("block.data.CraftBlockData");
            GET_STATE = ReflectionUtil.findMethod(craftBlockDataClass, "getState");

            Class<?> nmsBlockClass = ReflectionUtil.findClass("net.minecraft.world.level.block.Block");
            Class<?> nmsBlockStateClass = null;
            try {
                nmsBlockStateClass = ReflectionUtil.findClass("net.minecraft.world.level.block.state.BlockState");
            } catch (Exception e) {
                try {
                    nmsBlockStateClass = ReflectionUtil.findClass("net.minecraft.world.level.block.state.IBlockData");
                } catch (Exception e2) {
                    // Fallback
                }
            }

            if (nmsBlockStateClass != null) {
                // Find getId method
                try {
                    GET_ID = ReflectionUtil.findMethod(nmsBlockClass, "getId", nmsBlockStateClass);
                } catch (Exception e) {
                    try {
                        GET_ID = ReflectionUtil.findMethod(nmsBlockClass, "getCombinedId", nmsBlockStateClass);
                    } catch (Exception e2) {
                        try {
                            GET_ID = ReflectionUtil.findMethod(nmsBlockClass, "i", nmsBlockStateClass);
                        } catch (Exception e3) {
                            // Ignored
                        }
                    }
                }

                // Find stateById method
                try {
                    STATE_BY_ID = ReflectionUtil.findMethod(nmsBlockClass, "stateById", int.class);
                } catch (Exception e) {
                    try {
                        STATE_BY_ID = ReflectionUtil.findMethod(nmsBlockClass, "getByCombinedId", int.class);
                    } catch (Exception e2) {
                        try {
                            STATE_BY_ID = ReflectionUtil.findMethod(nmsBlockClass, "a", int.class);
                        } catch (Exception e3) {
                            // Ignored
                        }
                    }
                }

                // Find fromData method
                try {
                    FROM_DATA = ReflectionUtil.findMethod(craftBlockDataClass, "fromData", nmsBlockStateClass);
                } catch (Exception ignored) {}
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Converts {@code blockData} into an integer representation that can store necessary information about the block.
     *
     * @param blockData The data to convert
     * @return The generated unique ID for the block data
     */
    public static int getCombinedId(@NotNull BlockData blockData) {
        if (GET_STATE != null && GET_ID != null) {
            try {
                Object nmsState = ReflectionUtil.invokeMethod(blockData, GET_STATE);
                return (int) ReflectionUtil.invokeMethod(null, GET_ID, nmsState);
            } catch (Exception ignored) {}
        }
        return blockData.getMaterial().ordinal();
    }

    /**
     * Converts a {@code combinedId} back into a {@link BlockData}.
     *
     * @param combinedId The ID to convert
     * @return The Bukkit BlockData
     */
    public static BlockData getByCombinedId(int combinedId) {
        if (STATE_BY_ID != null && FROM_DATA != null) {
            try {
                Object nmsState = ReflectionUtil.invokeMethod(null, STATE_BY_ID, combinedId);
                if (nmsState != null) {
                    return (BlockData) ReflectionUtil.invokeMethod(null, FROM_DATA, nmsState);
                }
            } catch (Exception ignored) {}
        }
        Material[] materials = Material.values();
        if (combinedId >= 0 && combinedId < materials.length) {
            Material mat = materials[combinedId];
            if (mat.isBlock()) {
                return Bukkit.createBlockData(mat);
            }
        }
        return DEFAULT_BLOCK_DATA;
    }

    /**
     * Finds the ProtocolLib wrapper around the tile entity data update packet for {@code tileState}.
     *
     * @param tileState The tile entity to get the packet of
     * @return The ProtocolLib wrapper, or null if not applicable
     */
    public static @Nullable PacketContainer getUpdatePacket(@NotNull BlockState tileState) {
        if (!(tileState instanceof TileState)) {
            return null;
        }

        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        PacketContainer packet = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.TILE_ENTITY_DATA);
        return packet;
    }

    /**
     * Sets the position of a PacketPlayOutTileEntityData in the packet itself.
     *
     * @param packet The packet to modify the position of
     * @param position The new position as an IntVector
     */
    public static void setTileEntityPosition(@NotNull PacketContainer packet, @NotNull IntVector position) {
        BlockPosition blockPosition = new BlockPosition(position.getX(), position.getY(), position.getZ());
        packet.getBlockPositionModifier().write(0, blockPosition);
    }
}
