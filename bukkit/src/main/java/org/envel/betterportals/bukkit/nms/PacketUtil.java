package org.envel.betterportals.bukkit.nms;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Utility functions for common packet operations using ProtocolLib.
 * Reduces duplication and improves maintenance for relative coordinates, rotations, and packet sending.
 */
public class PacketUtil {

    /**
     * Writes relative movement offsets scaled by 4096 and cast to short into the packet.
     * Used for REL_ENTITY_MOVE and REL_ENTITY_MOVE_LOOK.
     *
     * @param packet The packet container to modify
     * @param offset The relative movement vector
     */
    public static void writeRelativeOffset(@NotNull PacketContainer packet, @NotNull Vector offset) {
        StructureModifier<Short> shorts = packet.getShorts();
        shorts.write(0, (short) (offset.getX() * 4096));
        shorts.write(1, (short) (offset.getY() * 4096));
        shorts.write(2, (short) (offset.getZ() * 4096));
    }

    /**
     * Writes velocity components scaled by 8000 into the packet.
     * Used for ENTITY_VELOCITY.
     *
     * @param packet   The packet container to modify
     * @param velocity The velocity vector
     */
    public static void writeVelocity(@NotNull PacketContainer packet, @NotNull Vector velocity) {
        StructureModifier<Integer> integers = packet.getIntegers();
        integers.write(1, (int) (velocity.getX() * 8000.0D));
        integers.write(2, (int) (velocity.getY() * 8000.0D));
        integers.write(3, (int) (velocity.getZ() * 8000.0D));
    }

    /**
     * Writes double coordinates (X, Y, Z) to the first three double fields in the packet.
     * Used for entity spawns and teleports.
     *
     * @param packet   The packet container to modify
     * @param position The position vector
     */
    public static void writeDoublePosition(@NotNull PacketContainer packet, @NotNull Vector position) {
        StructureModifier<Double> doubles = packet.getDoubles();
        doubles.write(0, position.getX());
        doubles.write(1, position.getY());
        doubles.write(2, position.getZ());
    }

    /**
     * Reads double coordinates (X, Y, Z) from the first three double fields of a packet.
     *
     * @param packet The packet container to read from
     * @return The read position vector
     */
    public static Vector readDoublePosition(@NotNull PacketContainer packet) {
        StructureModifier<Double> doubles = packet.getDoubles();
        return new Vector(
                doubles.read(0),
                doubles.read(1),
                doubles.read(2)
        );
    }

    /**
     * Writes standard packet rotation bytes for teleporting/head rotation.
     *
     * @param packet The packet container to modify
     * @param yaw    The yaw angle in degrees
     * @param pitch  The pitch angle in degrees
     */
    public static void writeTeleportRotation(@NotNull PacketContainer packet, float yaw, float pitch) {
        StructureModifier<Byte> bytes = packet.getBytes();
        bytes.write(0, RotationUtil.getPacketRotationByte(yaw));
        bytes.write(1, RotationUtil.getPacketRotationByte(pitch));
    }

    /**
     * Writes look rotation bytes using clamped packet rotation integers.
     *
     * @param packet The packet container to modify
     * @param yaw    The yaw angle in degrees
     * @param pitch  The pitch angle in degrees
     */
    public static void writeLookRotation(@NotNull PacketContainer packet, float yaw, float pitch) {
        StructureModifier<Byte> bytes = packet.getBytes();
        bytes.write(0, (byte) RotationUtil.getPacketRotationInt(yaw));
        bytes.write(1, (byte) RotationUtil.getPacketRotationInt(pitch));
    }

    /**
     * Safely sends a packet to a single player.
     *
     * @param player The recipient player
     * @param packet The packet container to send
     */
    public static void sendPacket(@NotNull Player player, @NotNull PacketContainer packet) {
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to send packet to " + player.getName(), ex);
        }
    }

    /**
     * Safely sends a packet to a collection of players.
     *
     * @param players The collection of recipient players
     * @param packet  The packet container to send
     */
    public static void sendPacket(@NotNull Collection<Player> players, @NotNull PacketContainer packet) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        try {
            for (Player player : players) {
                pm.sendServerPacket(player, packet);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to send packet to players", ex);
        }
    }
}
