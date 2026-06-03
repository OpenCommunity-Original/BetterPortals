package org.envel.betterportals.bukkit.portal;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.envel.betterportals.api.PortalPosition;
import org.envel.betterportals.bukkit.block.IBlockMap;
import org.envel.betterportals.bukkit.chunk.chunkloading.PortalChunkLoader;
import org.envel.betterportals.bukkit.config.MiscConfig;
import org.envel.betterportals.bukkit.entity.IPortalEntityManager;
import org.envel.betterportals.bukkit.math.PortalTransformations;
import org.envel.betterportals.bukkit.math.PortalTransformationsFactory;
import org.envel.betterportals.bukkit.util.MaterialUtil;
import org.envel.betterportals.shared.logging.Logger;
import org.envel.betterportals.bukkit.util.SchedulerUtil;
import lombok.Getter;
import lombok.Setter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Portal implements IPortal, ConfigurationSerializable {
    @Getter private final UUID id;
    @Getter private final UUID ownerId;
    @Getter private String name;

    private final IPortalManager portalManager;
    private final Logger logger;

    @Getter private final PortalPosition originPos;
    @Getter private final PortalPosition destPos;
    @Getter private final Vector size;
    @Getter private final boolean isCrossServer;
    @Getter private final boolean isCustom;
    private boolean allowNonPlayerTeleportation;

    @Getter private final PortalTransformations transformations;
    @Getter private final IBlockMap viewableBlocks;

    @Getter private final IPortalEntityManager entityList;
    private final PortalChunkLoader chunkLoader;

    private int ticksSinceActivated = -1;
    private int ticksSinceViewActivated = -1;

    private boolean originBlockValidCached = true;
    private boolean destBlockValidCached = true;
    private final AtomicBoolean originChecking = new AtomicBoolean(false);
    private final AtomicBoolean destChecking = new AtomicBoolean(false);

    @Getter @Setter private double price = 0.0;
    @Getter @Setter private @Nullable String effectPreset = null;
    @Getter @Setter private boolean soundEnabled = true;

    @Inject
    public Portal(IPortalManager portalManager, IPortalEntityManager.Factory entityListFactory, IBlockMap.Factory viewableBlockArrayFactory,
                  PortalChunkLoader chunkLoader, MiscConfig miscConfig,
                  Logger logger, PortalTransformationsFactory transformationsFactory,
                  @Assisted("originPos") PortalPosition originPos, @Assisted("destPos") PortalPosition destPos,
                  @Assisted Vector size, @Assisted("isCustom") boolean isCustom,
                  @Assisted("id") UUID id, @Nullable @Assisted("ownerId") UUID ownerId, @Nullable @Assisted("name") String name, @Assisted("allowNonPlayerTeleportation") boolean allowNonPlayerTeleportation) {
        this.portalManager = portalManager;
        this.logger = logger;
        this.originPos = originPos;
        this.destPos = destPos;
        this.size = size;
        this.isCrossServer = destPos.isExternal();
        this.isCustom = isCustom;
        this.allowNonPlayerTeleportation = allowNonPlayerTeleportation;
        // We do not need to get the destination entities if viewing entities through portals is disabled, or if entity support is disabled
        this.entityList = entityListFactory.create(this, !isCrossServer && miscConfig.isEntitySupportEnabled());
        this.chunkLoader = chunkLoader;
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;

        this.transformations = transformationsFactory.create(this);
        this.viewableBlocks = viewableBlockArrayFactory.create(this);
    }

    @Override
    public void onUpdate() {
        // Remove the portal if it is invalid
        if(!isStillValid()) {
            remove(true);
        }
        entityList.update(ticksSinceActivated);

        ticksSinceActivated++;
    }

    @Override
    public void onViewUpdate() {
        viewableBlocks.update(ticksSinceViewActivated);

        ticksSinceViewActivated++;
    }

    @Override
    public void onActivate() {
        logger.finer("Portal was activated");
        chunkLoader.forceloadPortalChunks(destPos);
        ticksSinceActivated = 0;
    }

    @Override
    public void onDeactivate() {
        logger.finer("Portal was deactivated");
        chunkLoader.unforceloadPortalChunks(destPos);
        viewableBlocks.reset();
        ticksSinceActivated = -1;
    }

    @Override
    public void onViewActivate() {
        logger.finest("Portal was view-activated");
        ticksSinceViewActivated = 0;
    }

    @Override
    public void onViewDeactivate() {
        logger.finest("Portal was view-deactivated");
        ticksSinceViewActivated = -1;
    }

    @Override
    public void remove(boolean removeOtherDirection) {
        portalManager.removePortal(this);
        originPos.getLocation().getBlock().setType(Material.AIR);
        if(removeOtherDirection) {
            portalManager.removePortalsAt(destPos.getLocation());
            // Break the portal blocks at the destination for nether portals
            if(isNetherPortal()) {
                destPos.getLocation().getBlock().setType(Material.AIR);
            }
        }
    }

    @Override
    public String getPermissionPath() {
        if(isCustom) {
            if(getName() != null) {
                return ".custom." + getName();
            }   else    {
                return "";
            }
        }   else    {
            return ".nether." + originPos.getWorldName();
        }
    }

    @Override
    public boolean isRegistered() {
        return portalManager.getPortalById(id) != null;
    }

    @Override
    public void setName(@Nullable String newName) {
        if(isNetherPortal()) throw new IllegalStateException("Cannot set name of nether portal");

        name = newName;
    }

    @Override
    public boolean allowsNonPlayerTeleportation() {
        return allowNonPlayerTeleportation;
    }

    @Override
    public void setAllowsNonPlayerTeleportation(boolean allow) {
        allowNonPlayerTeleportation = allow;
    }

    private void updateValidityCache() {
        if (!SchedulerUtil.isFolia() || isCustom) {
            return;
        }

        // Check origin
        if (originChecking.compareAndSet(false, true)) {
            var loc = originPos.getLocation();
            var world = loc.getWorld();
            if (world != null) {
                SchedulerUtil.runAtLocation(loc, () -> {
                    try {
                        originBlockValidCached = world.getBlockData(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()).getMaterial() == MaterialUtil.PORTAL_MATERIAL;
                    } catch (Exception ignored) {
                    } finally {
                        originChecking.set(false);
                    }
                });
            } else {
                originChecking.set(false);
            }
        }

        // Check destination (only if not external/cross-server)
        if (!isCrossServer && destChecking.compareAndSet(false, true)) {
            var loc = destPos.getLocation();
            var world = loc.getWorld();
            if (world != null) {
                SchedulerUtil.runAtLocation(loc, () -> {
                    try {
                        destBlockValidCached = world.getBlockData(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()).getMaterial() == MaterialUtil.PORTAL_MATERIAL;
                    } catch (Exception ignored) {
                    } finally {
                        destChecking.set(false);
                    }
                });
            } else {
                destChecking.set(false);
            }
        }
    }

    private boolean isStillValid() {
        // Custom portals shouldn't remove themselves if the portal blocks are broken
        if(isCustom) {return true;}

        if (SchedulerUtil.isFolia()) {
            updateValidityCache();
            return originBlockValidCached && destBlockValidCached;
        }

        // For nether portals, the portal blocks must have the type of the portal material
        return      originPos.getLocation().getBlock().getType() == MaterialUtil.PORTAL_MATERIAL
                &&  (isCrossServer || destPos.getLocation().getBlock().getType() == MaterialUtil.PORTAL_MATERIAL);
    }

    @NotNull
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> result = new HashMap<>();
        result.put("originPos", originPos);
        result.put("destPos", destPos);
        result.put("size", size);
        result.put("anchored", isCustom);
        result.put("id", id.toString());
        result.put("allowsNonPlayerTeleportation", allowsNonPlayerTeleportation());
        if(ownerId != null) {result.put("owner", ownerId.toString());}
        if(name != null) {result.put("name", name);}
        result.put("price", price);
        if(effectPreset != null) {result.put("effectPreset", effectPreset);}
        result.put("soundEnabled", soundEnabled);

        return result;
    }

    // We have to inject this statically unfortunately, no real other choice :/
    @Inject private static Factory deserializationFactory;

    public static Portal valueOf(Map<String, Object> map) {
        // We have to null check this and replace it with a random ID since it didn't exist in all prior versions
        String idString = (String) map.get("id");
        UUID id = idString == null ? UUID.randomUUID() : UUID.fromString(idString);
        // Portals don't always have an owner
        String ownerIdString = (String) map.get("ownerId");
        UUID ownerId = ownerIdString == null ? null : UUID.fromString(ownerIdString);

        Portal portal = (Portal) deserializationFactory.create(
                (PortalPosition) map.get("originPos"),
                (PortalPosition) map.get("destPos"),
                (Vector) map.get("size"),
                (boolean) map.get("anchored"),
                id,
                ownerId,
                (String) map.get("name"),
                (boolean) map.getOrDefault("allowsNonPlayerTeleportation", true)
        );
        if (map.containsKey("price")) {
            portal.setPrice(((Number) map.get("price")).doubleValue());
        }
        if (map.containsKey("effectPreset")) {
            portal.setEffectPreset((String) map.get("effectPreset"));
        }
        if (map.containsKey("soundEnabled")) {
            portal.setSoundEnabled((boolean) map.get("soundEnabled"));
        }
        return portal;
    }
}
