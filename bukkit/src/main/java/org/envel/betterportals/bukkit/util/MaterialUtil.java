package org.envel.betterportals.bukkit.util;

import com.comphenix.protocol.wrappers.WrappedBlockData;
import org.envel.betterportals.bukkit.nms.CraftBukkitClassUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * The materials/data of certain blocks that we need change based on version.
 * This small wrapper allows us to avoid the issues.
 */
public class MaterialUtil {
    public static final Material PORTAL_MATERIAL = Material.NETHER_PORTAL;
    public static final WrappedBlockData PORTAL_EDGE_DATA = WrappedBlockData.createData(Material.BLACK_CONCRETE);

    private static final String[] TILE_ENTITY_MATERIALS_STRING = new String[]{
            "DISPENSER", "SPAWNER", "CHEST", "FURNACE", "JUKEBOX", "ENCHANTING_TABLE", "ENDER_CHEST", "COMMAND_BLOCK",
            "BEACON", "TRAPPED_CHEST", "DAYLIGHT_DETECTOR", "HOPPER", "DROPPER", "REPEATING_COMMAND_BLOCK",
            "CHAIN_COMMAND_BLOCK", "WHITE_SHULKER_BOX", "ORANGE_SHULKER_BOX", "MAGENTA_SHULKER_BOX",
            "LIGHT_BLUE_SHULKER_BOX", "YELLOW_SHULKER_BOX", "LIME_SHULKER_BOX", "PINK_SHULKER_BOX", "GRAY_SHULKER_BOX",
            "LIGHT_GRAY_SHULKER_BOX", "CYAN_SHULKER_BOX", "PURPLE_SHULKER_BOX", "BLUE_SHULKER_BOX", "BROWN_SHULKER_BOX",
            "GREEN_SHULKER_BOX", "RED_SHULKER_BOX", "BLACK_SHULKER_BOX", "SHULKER_BOX", "CONDUIT", "COMPARATOR",
            "STRUCTURE_BLOCK", "JIGSAW", "OAK_SIGN", "SPRUCE_SIGN", "BIRCH_SIGN", "JUNGLE_SIGN", "ACACIA_SIGN",
            "DARK_OAK_SIGN", "CRIMSON_SIGN", "WARPED_SIGN", "SIGN_POST", "LEGACY_BED_BLOCK", "WHITE_BED", "ORANGE_BED",
            "MAGENTA_BED", "LIGHT_BLUE_BED", "YELLOW_BED", "LIME_BED", "PINK_BED", "GRAY_BED", "LIGHT_GRAY_BED",
            "CYAN_BED", "PURPLE_BED", "BLUE_BED", "BROWN_BED", "GREEN_BED", "RED_BED", "BLACK_BED", "BREWING_STAND",
            "SKELETON_SKULL", "WITHER_SKELETON_SKULL", "PLAYER_HEAD", "ZOMBIE_HEAD", "CREEPER_HEAD", "DRAGON_HEAD",
            "WHITE_BANNER", "ORANGE_BANNER", "MAGENTA_BANNER", "LIGHT_BLUE_BANNER", "YELLOW_BANNER", "LIME_BANNER",
            "PINK_BANNER", "GRAY_BANNER", "LIGHT_GRAY_BANNER", "CYAN_BANNER", "PURPLE_BANNER", "BLUE_BANNER",
            "BROWN_BANNER", "GREEN_BANNER", "RED_BANNER", "BLACK_BANNER", "BARREL", "SMOKER", "BLAST_FURNACE",
            "LECTERN", "BELL", "CAMPFIRE", "SOUL_CAMPFIRE", "BEE_NEST", "BEEHIVE", "OAK_WALL_SIGN", "SPRUCE_WALL_SIGN",
            "BIRCH_WALL_SIGN", "ACACIA_WALL_SIGN", "JUNGLE_WALL_SIGN", "DARK_OAK_WALL_SIGN", "WALL_SIGN", "END_PORTAL",
            "SKELETON_WALL_SKULL", "WITHER_SKELETON_WALL_SKULL", "ZOMBIE_WALL_HEAD", "PLAYER_WALL_HEAD",
            "CREEPER_WALL_HEAD", "DRAGON_WALL_HEAD", "WHITE_WALL_BANNER", "ORANGE_WALL_BANNER", "MAGENTA_WALL_BANNER",
            "LIGHT_BLUE_WALL_BANNER", "YELLOW_WALL_BANNER", "LIME_WALL_BANNER", "PINK_WALL_BANNER", "GRAY_WALL_BANNER",
            "LIGHT_GRAY_WALL_BANNER", "CYAN_WALL_BANNER", "PURPLE_WALL_BANNER", "BLUE_WALL_BANNER", "BROWN_WALL_BANNER",
            "GREEN_WALL_BANNER", "RED_WALL_BANNER", "BLACK_WALL_BANNER", "END_GATEWAY", "CRIMSON_WALL_SIGN",
            "WARPED_WALL_SIGN"
    };

    private static final Set<Material> TILE_ENTITY_MATERIALS = new HashSet<>();
    private static boolean initialized = false;

    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        if (Bukkit.getServer() != null) {
            try {
                Class<?> entityBlockClass = Class.forName("net.minecraft.world.level.block.EntityBlock");
                Class<?> nmsBlockStateClass = Class.forName("net.minecraft.world.level.block.state.BlockState");
                Method getBlockMethod = nmsBlockStateClass.getMethod("getBlock");
                Class<?> craftBlockDataClass = CraftBukkitClassUtil.findCraftBukkitClass("block.data.CraftBlockData");
                Method getStateMethod = craftBlockDataClass.getMethod("getState");

                for (Material mat : Material.values()) {
                    if (mat.isBlock()) {
                        try {
                            BlockData blockData = Bukkit.createBlockData(mat);
                            Object nmsState = getStateMethod.invoke(blockData);
                            Object nmsBlock = getBlockMethod.invoke(nmsState);
                            if (entityBlockClass.isInstance(nmsBlock)) {
                                TILE_ENTITY_MATERIALS.add(mat);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }

        if (TILE_ENTITY_MATERIALS.isEmpty()) {
            for (String str : TILE_ENTITY_MATERIALS_STRING) {
                try {
                    TILE_ENTITY_MATERIALS.add(Material.valueOf(str));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    /**
     * Checks if a block with type <code>material</code> would be a tile entity.
     * This is used for optimisation, because calling {@link org.bukkit.block.Block#getState}
     * and then checking if it's an instance of TileState is expensive, so this reduces it to a hashmap lookup.
     * @param material The material to test
     * @return Whether or not it is the type of a tile entity
     */
    public static boolean isTileEntity(Material material) {
        if (material == null) return false;
        if (!initialized) {
            initialize();
        }
        return TILE_ENTITY_MATERIALS.contains(material);
    }
}
