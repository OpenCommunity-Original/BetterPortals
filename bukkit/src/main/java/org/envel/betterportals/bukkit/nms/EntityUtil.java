package org.envel.betterportals.bukkit.nms;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Marker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EntityUtil {
    /**
     * Gets the actual data watcher for the given entity.
     * ProtocolLib unfortunately doesn't provide any methods for getting the actual WrappedDataWatcher of an entity.
     * @param entity The entity to wrap the data watcher of
     * @return The wrapped data watcher
     */
    @NotNull
    public static WrappedDataWatcher getActualDataWatcher(@NotNull Entity entity) {
        // Utilize ProtocolLib to get the data watcher directly from the entity
        return WrappedDataWatcher.getEntityWatcher(entity);
    }

    /**
     * Getting a valid spawn packet that works correctly for a specific Entity is surprisingly difficult.
     * This method checks if the entity is a type that can be spawned and returns a packet if it can.
     * @param entity The entity to get the spawn packet of
     * @return A container with the valid packet, or null since some entities can't be spawned with a packet.
     */
    public static @Nullable PacketContainer getRawEntitySpawnPacket(@NotNull Entity entity) {
        if (entity instanceof EnderDragonPart || entity instanceof Marker) return null;

        // Use ProtocolLib to create a spawn packet for the entity
        PacketContainer spawnPacket = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);
        spawnPacket.getIntegers().write(0, entity.getEntityId());
        spawnPacket.getEntityTypeModifier().write(0, entity.getType());

        return spawnPacket;
    }
}
