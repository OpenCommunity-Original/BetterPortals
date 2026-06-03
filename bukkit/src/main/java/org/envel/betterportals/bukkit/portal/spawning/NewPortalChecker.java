package org.envel.betterportals.bukkit.portal.spawning;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.envel.betterportals.api.PortalDirection;
import org.envel.betterportals.bukkit.chunk.chunkpos.ChunkPosition;
import org.envel.betterportals.bukkit.config.PortalSpawnConfig;
import org.envel.betterportals.bukkit.config.WorldLink;
import org.envel.betterportals.bukkit.portal.IPortalManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Finds new valid portal positions in a chunk
 *
 * Valid portal spawn positions are defined here as follows:
 * <ul>
 * <li>The portal base must be solid blocks</li>
 * <li>The area above the portal base, but not the portal roof (the top of the frame), must be air blocks.</li>
 * <li>There aren't any portals within the minimum spawn distance</li>
 * </ul>
 * These rules are on the line of the portal frame, as well as the two blocks going through the portal (represented as the Z below)
 */
@Singleton
public class NewPortalChecker implements IChunkChecker  {
    private static final PortalDirection[] CHECKED_DIRECTIONS = new PortalDirection[] {
            PortalDirection.NORTH,
            PortalDirection.EAST
    };

    private final IPortalManager portalManager;
    private final PortalSpawnConfig spawnConfig;

    @Inject
    public NewPortalChecker(IPortalManager portalManager, PortalSpawnConfig spawnConfig) {
        this.portalManager = portalManager;
        this.spawnConfig = spawnConfig;
    }

    @Override
    public @Nullable PortalSpawnPosition findClosestInChunk(@NotNull ChunkPosition chunk, @NotNull PortalSpawningContext context) {
        PortalSpawnPosition currentClosest = null;
        double closestDistance = Double.POSITIVE_INFINITY;

        WorldLink link = context.getWorldLink();
        Location preferredLocation = context.getPreferredLocation();
        World world = chunk.world;

        // Compute chunk origin as block coords (avoids getBottomLeft() Location alloc in the loop)
        int baseX = chunk.x << 4;
        int baseZ = chunk.z << 4;

        // One reusable Location for distance checks — avoids new Location per iteration
        Location blockPos = new Location(world, 0, 0, 0);

        for(int y = link.getMinSpawnY(); y < link.getMaxSpawnY(); y++) {
            for(int x = 0; x < 16; x++) {
                for(int z = 0; z < 16; z++) {
                    blockPos.setX(baseX + x);
                    blockPos.setY(y);
                    blockPos.setZ(baseZ + z);

                    // Do this here to avoid the expensive check if at-all possible
                    double distance = blockPos.distance(preferredLocation);
                    if(distance >= closestDistance) {continue;}

                    // Make sure to check both directions for a valid spawn position
                    for(PortalDirection direction : CHECKED_DIRECTIONS) {
                        if(isValidPortalSpawnPosition(blockPos, direction, context.getSize())) {
                            closestDistance = distance;
                            // Clone here because PortalSpawnPosition stores the location permanently
                            currentClosest = new PortalSpawnPosition(blockPos.clone(), context.getSize(), direction);
                        }
                    }
                }
            }
        }

        return currentClosest;
    }

    public boolean isValidPortalSpawnPosition(Location location, PortalDirection direction, Vector size) {
        // Pre-compute extended size once — previously allocated new Vector() every call
        int sizeX = (int) size.getX() + 1;
        int sizeY = (int) size.getY() + 1;

        // Reusable temp location to avoid location.clone() per inner-loop iteration
        Location temp = location.clone();

        for(int z = -1; z <= 1; z++) {
            for (int x = 0; x <= sizeX; x++) {
                for (int y = 0; y <= sizeY; y++) {
                    Vector frameRelativePos = new Vector(x, y, z);
                    Vector swapped = direction.swapVector(frameRelativePos);

                    temp.setX(location.getX() + swapped.getX());
                    temp.setY(location.getY() + swapped.getY());
                    temp.setZ(location.getZ() + swapped.getZ());

                    Material type = temp.getBlock().getType();

                    boolean isFrame = x == 0 || y == 0 || x == sizeX || y == sizeY;

                    if ((!isFrame) && !type.isAir()) { // Portal block positions must be air
                        return false;
                    }
                    if (y == 0 && (!type.isSolid())) { // The floor blocks must be solid
                        return false;
                    }
                }
            }
        }

        // Make sure that there aren't any other portals too close
        boolean isFarEnoughSpaced = portalManager.findClosestPortal(location, spawnConfig.getMinimumPortalSpawnDistance()) == null;
        // Don't spawn portals outside the world border!
        boolean isInsideWorldBorder = location.getWorld().getWorldBorder().isInside(location);

        return isFarEnoughSpaced && isInsideWorldBorder;
    }
}

