package org.envel.betterportals.bukkit.entity.faking;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.*;
import com.google.inject.Singleton;
import org.envel.betterportals.bukkit.math.MathUtil;
import org.envel.betterportals.bukkit.nms.AnimationType;
import org.envel.betterportals.bukkit.nms.EntityUtil;
import org.envel.betterportals.bukkit.nms.PacketUtil;
import org.envel.betterportals.bukkit.nms.RotationUtil;
import org.envel.betterportals.bukkit.util.VersionUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;


/**
 * Deals with sending all of the packets for entity processing
 * Warning: a fair bit of this class is me complaining about mojang
 */
@Singleton
public class EntityPacketManipulator implements IEntityPacketManipulator {

    @Override
    public void showEntity(EntityInfo tracker, Collection<Player> players) {
        // Generate the packet that NMS would normally use to spawn the entity
        PacketContainer spawnPacket = EntityUtil.getRawEntitySpawnPacket(tracker.getEntity());
        if(spawnPacket == null) {return;}

        if(spawnPacket.getUUIDs().size() > 0) {
            spawnPacket.getUUIDs().write(0, tracker.getEntityUniqueId());
        }

        // Use the rendered entity ID
        spawnPacket.getIntegers().write(0, tracker.getEntityId());

        // Translate to the correct rendered position
        Vector actualPos = tracker.getEntity().getLocation().toVector();
        if(tracker.getEntity() instanceof Hanging) {
            actualPos = MathUtil.moveToCenterOfBlock(actualPos);
        }

        Vector renderedPos = tracker.getTranslation().transform(actualPos);
        PacketUtil.writeDoublePosition(spawnPacket, renderedPos);
        setSpawnRotation(spawnPacket, tracker);

        PacketUtil.sendPacket(players, spawnPacket);

        // Living Entities also require us to handle entity equipment
        if(tracker.getEntity() instanceof LivingEntity) {
            EntityEquipmentWatcher equipmentWatcher = new EntityEquipmentWatcher((LivingEntity) tracker.getEntity());
            Map<EnumWrappers.ItemSlot, ItemStack> changes = equipmentWatcher.checkForChanges();
            if(changes.size() > 0) {
                sendEntityEquipment(tracker, changes, players);
            }
        }

        sendMetadata(tracker, players);
    }

    // Positional helper calls are delegated to PacketUtil

    // Every packet handles spawn rotation differently for whatever reason
    // This method will set the correct field(s) for whatever packet type
    @SuppressWarnings("deprecation")
    private void setSpawnRotation(PacketContainer packet, EntityInfo entityInfo) {
        // Calculate the correct byte yaw, pitch, and head rotation for the entity
        Location renderedPos = entityInfo.findRenderedLocation();

        int yaw = RotationUtil.getPacketRotationInt(renderedPos.getYaw());
        int pitch = RotationUtil.getPacketRotationInt(renderedPos.getPitch());

        PacketType packetType = packet.getType();
        if(packetType == PacketType.Play.Server.SPAWN_ENTITY)  {
            // Hanging entities use block faces for their rotation
            if(entityInfo.getEntity() instanceof Hanging) {
                // Minecraft deals with this as the ID of the direction as an int for item frames
                EnumWrappers.Direction currentDirection = RotationUtil.getDirection(packet.getIntegers().read(4));
                if(currentDirection != null) {
                    EnumWrappers.Direction rotated = RotationUtil.rotateBy(currentDirection, entityInfo.getRotation());
                    // Make sure to catch this - it should never happen unless something's gone very wrong
                    if (rotated == null) {
                        throw new IllegalStateException("Portal attempted to rotate a hanging entity to an invalid block direction");
                    }
                    packet.getIntegers().write(4, RotationUtil.getId(rotated));
                }
            }

            // Set the modified pitch and yaw
            packet.getBytes().write(0, (byte) pitch);
            packet.getBytes().write(1, (byte) yaw);
        }   else if(packetType == PacketType.Play.Server.NAMED_ENTITY_SPAWN) {
            packet.getBytes().write(0, (byte) yaw);
            packet.getBytes().write(1, (byte) pitch);
        }
    }

    @Override
    public void hideEntity(EntityInfo tracker, Collection<Player> players) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        packet.getIntLists().write(0, Collections.singletonList(tracker.getEntityId()));
        PacketUtil.sendPacket(players, packet);
    }

    @Override
    public void sendEntityMove(EntityInfo tracker, Vector offset, Collection<Player> players) {
        offset = tracker.getRotation().transform(offset);

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.REL_ENTITY_MOVE);
        packet.getIntegers().write(0, tracker.getEntityId());

        PacketUtil.writeRelativeOffset(packet, offset);
        packet.getBooleans().write(0, tracker.getEntity().isOnGround());

        PacketUtil.sendPacket(players, packet);
    }

    @Override
    public void sendEntityMoveLook(EntityInfo tracker, Vector offset, Collection<Player> players) {
        Location entityPos = tracker.findRenderedLocation();
        offset = tracker.getRotation().transform(offset); // Make sure to transform the given offset so that it's correct for the rendered position

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.REL_ENTITY_MOVE_LOOK);
        packet.getIntegers().write(0, tracker.getEntityId());

        PacketUtil.writeLookRotation(packet, entityPos.getYaw(), entityPos.getPitch());
        PacketUtil.writeRelativeOffset(packet, offset);
        packet.getBooleans().write(0, tracker.getEntity().isOnGround());

        PacketUtil.sendPacket(players, packet);
    }


    @Override
    public void sendEntityLook(EntityInfo tracker, Collection<Player> players) {
        // Transform the entity rotation to the origin of the portal
        Location entityPos = tracker.findRenderedLocation();

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_LOOK);
        packet.getIntegers().write(0, tracker.getEntityId());

        PacketUtil.writeLookRotation(packet, entityPos.getYaw(), entityPos.getPitch());
        packet.getBooleans().write(0, tracker.getEntity().isOnGround());

        PacketUtil.sendPacket(players, packet);
    }

    @Override
    public void sendEntityTeleport(EntityInfo tracker, Collection<Player> players) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT);
        packet.getIntegers().write(0, tracker.getEntityId());

        Location entityPos = tracker.findRenderedLocation();

        PacketUtil.writeDoublePosition(packet, entityPos.toVector());
        PacketUtil.writeTeleportRotation(packet, entityPos.getYaw(), entityPos.getPitch());

        packet.getBooleans().write(0, tracker.getEntity().isOnGround());
        PacketUtil.sendPacket(players, packet);
    }

    @Override
    public void sendEntityHeadRotation(EntityInfo tracker, Collection<Player> players) {
        // Entity yaw is actually head rotation in Bukkit
        Location renderedPos = tracker.findRenderedLocation();

        byte headRotation = RotationUtil.getPacketRotationByte(renderedPos.getYaw());

        PacketContainer packet;
        try {
            Object nmsEntity = tracker.getEntity().getClass().getMethod("getHandle").invoke(tracker.getEntity());
            Class<?> nmsEntityClass = Class.forName("net.minecraft.world.entity.Entity");
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket");
            java.lang.reflect.Constructor<?> constructor = packetClass.getConstructor(nmsEntityClass, byte.class);
            Object nmsPacket = constructor.newInstance(nmsEntity, headRotation);

            packet = new PacketContainer(PacketType.Play.Server.ENTITY_HEAD_ROTATION, nmsPacket);
            packet.getIntegers().write(0, tracker.getEntityId());
        } catch (Throwable ex) {
            packet = new PacketContainer(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
            packet.getIntegers().write(0, tracker.getEntityId());
            packet.getBytes().write(0, headRotation);
        }

        PacketUtil.sendPacket(players, packet);
    }

    @Override
    public void sendMount(EntityInfo tracker, Collection<EntityInfo> riding, Collection<Player> players) {
        int[] ridingIds = new int[riding.size()];
        int i = 0;
        for(EntityInfo ridingTracker : riding) {
            ridingIds[i] = ridingTracker.getEntityId();
            i++;
        }

        PacketContainer packet;
        try {
            Object nmsEntity = tracker.getEntity().getClass().getMethod("getHandle").invoke(tracker.getEntity());
            Class<?> nmsEntityClass = Class.forName("net.minecraft.world.entity.Entity");
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPassengersPacket");
            java.lang.reflect.Constructor<?> constructor = packetClass.getConstructor(nmsEntityClass);
            Object nmsPacket = constructor.newInstance(nmsEntity);

            packet = new PacketContainer(PacketType.Play.Server.MOUNT, nmsPacket);
            packet.getIntegers().write(0, tracker.getEntityId());
            packet.getIntegerArrays().write(0, ridingIds);
        } catch (Throwable ex) {
            packet = new PacketContainer(PacketType.Play.Server.MOUNT);
            packet.getIntegers().write(0, tracker.getEntityId());
            packet.getIntegerArrays().write(0, ridingIds);
        }

        PacketUtil.sendPacket(players, packet);
    }

    @Override
    public void sendEntityEquipment(EntityInfo tracker, Map<EnumWrappers.ItemSlot, ItemStack> changes, Collection<Player> players) {
        // Why minecraft, why not just use a map...
        List<Pair<EnumWrappers.ItemSlot, ItemStack>> wrappedChanges = new ArrayList<>();
        changes.forEach((slot, item) -> wrappedChanges.add(new Pair<>(slot, item == null ? new ItemStack(Material.AIR) : item)));

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_EQUIPMENT);
        packet.getIntegers().write(0, tracker.getEntityId());
        packet.getSlotStackPairLists().write(0, wrappedChanges);
        PacketUtil.sendPacket(players, packet);
    }

    @Override
    public void sendMetadata(EntityInfo tracker, Collection<Player> players) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);

        packet.getIntegers().write(0, tracker.getEntityId());

        WrappedDataWatcher dataWatcher = EntityUtil.getActualDataWatcher(tracker.getEntity()); // Get the entity's actual data watcher

        // Convert the data watcher to a list of WrappedDataValues
        List<WrappedDataValue> wrappedDataValueList = dataWatcher.getWatchableObjects().stream()
                .filter(Objects::nonNull)
                .map(entry -> {
                    WrappedDataWatcher.WrappedDataWatcherObject dataWatcherObject = entry.getWatcherObject();
                    return new WrappedDataValue(
                            dataWatcherObject.getIndex(),
                            dataWatcherObject.getSerializer(),
                            entry.getRawValue());
                })
                .toList();

        packet.getDataValueCollectionModifier().write(0, wrappedDataValueList); // Write the data values to the packet

        PacketUtil.sendPacket(players, packet); // Send the packet to the specified players
    }


    @Override
    public void sendEntityVelocity(EntityInfo tracker, Vector newVelocity, Collection<Player> players) {
        // Rotate the velocity back to the origin of the portal
        Vector entityVelocity = tracker.getRotation().transform(newVelocity);
        // Avoid integer overflows by limiting these values to 3.9
        entityVelocity = MathUtil.min(entityVelocity, new Vector(3.9, 3.9, 3.9));
        entityVelocity = MathUtil.max(entityVelocity, new Vector(-3.9, -3.9, -3.9));

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_VELOCITY);
        packet.getIntegers().write(0, tracker.getEntityId());
        PacketUtil.writeVelocity(packet, entityVelocity);

        PacketUtil.sendPacket(players, packet);
    }

    @Override
    public void sendEntityAnimation(EntityInfo tracker, Collection<Player> players, AnimationType animationType) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ANIMATION);

        StructureModifier<Integer> integers = packet.getIntegers();
        integers.write(0, tracker.getEntityId());
        integers.write(1, animationType.getNmsId());

        PacketUtil.sendPacket(players, packet);
    }

    @Override
    public void sendEntityPickupItem(EntityInfo tracker, EntityInfo pickedUp, Collection<Player> players) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.COLLECT);

        StructureModifier<Integer> integers = packet.getIntegers();
        integers.write(0, pickedUp.getEntityId());
        integers.write(1, tracker.getEntityId());
        integers.write(2, ((Item) pickedUp.getEntity()).getItemStack().getAmount());

        PacketUtil.sendPacket(players, packet);
    }

    private PlayerInfoData generatePlayerInfoData(EntityInfo tracker) {
        // We make a new profile for our fake user, with our fake UUID and the original name
        WrappedGameProfile profile = new WrappedGameProfile(tracker.getEntityUniqueId(), tracker.getEntity().getName());

        Player trackingPlayer = (Player) tracker.getEntity();
        WrappedGameProfile playerProfile = WrappedGameProfile.fromPlayer(trackingPlayer);

        // We remove the existing textures (if any) and add the skin of the original player
        try {
            Object profileHandle = profile.getHandle();
            Object playerProfileHandle = playerProfile.getHandle();

            Object properties = profileHandle.getClass().getMethod("getProperties").invoke(profileHandle);
            Object playerProperties = playerProfileHandle.getClass().getMethod("getProperties").invoke(playerProfileHandle);

            properties.getClass().getMethod("removeAll", Object.class).invoke(properties, "textures");

            java.util.Collection<?> textures = (java.util.Collection<?>) playerProperties.getClass().getMethod("get", Object.class).invoke(playerProperties, "textures");
            properties.getClass().getMethod("putAll", Object.class, java.lang.Iterable.class).invoke(properties, "textures", textures);
        } catch (Exception ex) {
            profile.getProperties().removeAll("textures");
            profile.getProperties().putAll("textures", playerProfile.getProperties().get("textures"));
        }

        return new PlayerInfoData(
                profile,
                trackingPlayer.getPing(),
                EnumWrappers.NativeGameMode.fromBukkit(trackingPlayer.getGameMode()),
                WrappedChatComponent.fromText(trackingPlayer.getName())
        );
    }

    @Override
    public void sendAddPlayerProfile(EntityInfo tracker, Collection<Player> players) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.PLAYER_INFO);
        packet.getPlayerInfoActions().write(0, Set.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER));

        List<PlayerInfoData> playerInfoDataList = new ArrayList<>();
        playerInfoDataList.add(generatePlayerInfoData(tracker));
        packet.getPlayerInfoDataLists().write(1, playerInfoDataList);
        PacketUtil.sendPacket(players, packet);
    }

    @Override
    public void sendRemovePlayerProfile(EntityInfo tracker, Collection<Player> players) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.PLAYER_INFO_REMOVE);
        packet.getUUIDLists().write(0, Collections.singletonList(tracker.getEntityUniqueId()));
        PacketUtil.sendPacket(players, packet);
    }
}
