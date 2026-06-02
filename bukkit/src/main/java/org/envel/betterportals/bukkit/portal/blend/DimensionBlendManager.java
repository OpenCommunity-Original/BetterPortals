package org.envel.betterportals.bukkit.portal.blend;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.envel.betterportals.bukkit.config.PortalSpawnConfig;
import org.envel.betterportals.bukkit.util.MaterialUtil;
import org.envel.betterportals.bukkit.util.VersionUtil;
import org.envel.betterportals.bukkit.util.SchedulerUtil;
import org.envel.betterportals.shared.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Random;

@Singleton
public class DimensionBlendManager implements IDimensionBlendManager    {
    private static final double INITIAL_CHANCE = 1.0;
    private static final Material[] BLACKLISTED_COPY_BLOCKS = new Material[] {
        Material.OBSIDIAN,
        Material.BEDROCK,
        MaterialUtil.PORTAL_MATERIAL,
        Material.AIR,
        Material.BARRIER,
        Material.DIAMOND_BLOCK,
        Material.EMERALD_BLOCK,
        Material.IRON_BLOCK
    };

    private final PortalSpawnConfig spawnConfig;
    private final Random random = new Random();
    private final Logger logger;

    @Inject
    public DimensionBlendManager(PortalSpawnConfig spawnConfig, Logger logger) {
        this.spawnConfig = spawnConfig;
        this.logger = logger;
    }

    private @NotNull Material findFillInBlock(@NotNull Location destination) {
        return switch (Objects.requireNonNull(destination.getWorld(), "World of destination location cannot be null").getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case NORMAL -> Material.STONE;
            case THE_END -> Material.END_STONE;
            default -> Material.AIR;
        };
    }

    @Override
    public void performBlend(@NotNull Location origin, @NotNull Location destination) {
        if (SchedulerUtil.isFolia()) {
            performBlendFolia(origin, destination);
            return;
        }

        logger.fine("Origin for blend: %s.", origin.toVector());
        int blockRadius = (int) (1.0 / spawnConfig.getBlendFallOff() + 4.0 + INITIAL_CHANCE);

        Material fillInBlock = findFillInBlock(destination);

        for(int z = -blockRadius; z < blockRadius; z++) {
            for(int y = -blockRadius; y < blockRadius; y++) {
                for(int x = -blockRadius; x < blockRadius; x++) {
                    Vector relativePos = new Vector(x, y, z);

                    double swapChance = calculateSwapChance(relativePos);
                    // Apply the random chance
                    if(random.nextDouble() > swapChance) {continue;}

                    Location originPos = origin.clone().add(relativePos);
                    Location destPos = destination.clone().add(applyRandomOffset(relativePos, 10.0));

                    Material originType = originPos.getBlock().getType();
                    Material destType = destPos.getBlock().getType();

                    if(!destType.isSolid()) {destType = fillInBlock;}

                    // Don't replace air or obsidian blocks so the portal doesn't get broken and we don't get blocks in the air.
                    boolean skip = false;
                    for(Material type : BLACKLISTED_COPY_BLOCKS) {
                        if(originType == type || destType == type) {
                            skip = true;
                            break;
                        }
                    }

                    if(skip) {continue;}

                    originPos.getBlock().setType(destType);
                }
            }
        }
    }

    private void performBlendFolia(@NotNull Location origin, @NotNull Location destination) {
        int blockRadius = (int) (1.0 / spawnConfig.getBlendFallOff() + 4.0 + INITIAL_CHANCE);
        Material fillInBlock = findFillInBlock(destination);

        SchedulerUtil.runAtLocation(destination, () -> {
            java.util.Map<Vector, Material> destTypes = new java.util.HashMap<>();
            for(int z = -blockRadius; z < blockRadius; z++) {
                for(int y = -blockRadius; y < blockRadius; y++) {
                    for(int x = -blockRadius; x < blockRadius; x++) {
                        Vector relativePos = new Vector(x, y, z);
                        double swapChance = calculateSwapChance(relativePos);
                        if(random.nextDouble() > swapChance) {continue;}

                        Location destPos = destination.clone().add(applyRandomOffset(relativePos, 10.0));
                        org.bukkit.World destWorld = destPos.getWorld();
                        Material destType = destWorld != null ? destWorld.getBlockData(destPos.getBlockX(), destPos.getBlockY(), destPos.getBlockZ()).getMaterial() : Material.AIR;
                        if(!destType.isSolid()) {destType = fillInBlock;}

                        destTypes.put(relativePos, destType);
                    }
                }
            }

            SchedulerUtil.runAtLocation(origin, () -> {
                for (java.util.Map.Entry<Vector, Material> entry : destTypes.entrySet()) {
                    Vector relativePos = entry.getKey();
                    Material destType = entry.getValue();

                    Location originPos = origin.clone().add(relativePos);
                    org.bukkit.World origWorld = originPos.getWorld();
                    if (origWorld == null) continue;

                    Material originType = origWorld.getBlockData(originPos.getBlockX(), originPos.getBlockY(), originPos.getBlockZ()).getMaterial();

                    boolean skip = false;
                    for(Material type : BLACKLISTED_COPY_BLOCKS) {
                        if(originType == type || destType == type) {
                            skip = true;
                            break;
                        }
                    }
                    if(skip) {continue;}

                    originPos.getBlock().setType(destType);
                }
            });
        });
    }

    /**
     * Moves each coordinate of <code>vec</code> a maximum of <code>power / 2</code> blocks higher or lower.
     * @param vec The vector to move
     * @param power The maximum deviation times two.
     * @return A new, offset vector.
     */
    private Vector applyRandomOffset(Vector vec, double power) {
        Vector other = new Vector();
        other.setX(vec.getX() + (random.nextDouble() - 0.5) * power);
        other.setY(vec.getY() + (random.nextDouble() - 0.5) * power);
        other.setZ(vec.getZ() + (random.nextDouble() - 0.5) * power);

        return other;
    }

    private double calculateSwapChance(Vector relativePos) {
        double distance = relativePos.length();
        return INITIAL_CHANCE - distance * spawnConfig.getBlendFallOff();
    }
}
