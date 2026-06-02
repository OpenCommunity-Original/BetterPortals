package org.envel.betterportals.bukkit.config;

import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.envel.betterportals.api.IntVector;
import org.envel.betterportals.api.PortalPosition;
import org.envel.betterportals.shared.logging.Logger;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Singleton
@Getter
public class RenderConfig {
    private final Logger logger;

    private double minXZ;
    private double maxXZ;
    private double minY;
    private double maxY;

    private int yMultip;
    private int zMultip;
    private int totalArrayLength;

    private IntVector halfFullSize;

    private WrappedBlockData backgroundBlockData; // Default to black concrete

    private final Map<String, WrappedBlockData> worldBackgroundBlockData = new HashMap<>();

    private int[] intOffsets;

    private Vector collisionBox;
    private int blockUpdateInterval;

    private int worldSwitchWaitTime;

    @Getter private boolean portalBlocksHidden;

    private int blockStateRefreshInterval;

    private int entityMetadataUpdateInterval;

    private int lightSimulationInterval;
    private int forceLightLevel;

    @Inject
    public RenderConfig(Logger logger) {
        this.logger = logger;
    }

    private @Nullable WrappedBlockData parseBlockData(String str) {
        try {
            return WrappedBlockData.createData(Material.valueOf(str.toUpperCase(Locale.ROOT)));
        }   catch(IllegalArgumentException ex) {
            logger.warning("Unknown material for portal edge block " + str);
            logger.warning("Using default of black concrete");
            return null;
        }
    }

    public void load(FileConfiguration file) {
        maxXZ = file.getInt("portalEffectSizeXZ");
        minXZ = maxXZ * -1.0;
        maxY = file.getInt("portalEffectSizeY");
        minY = maxY * -1.0;

        if(maxXZ <= 0 || maxY <= 0) {
            throw new IllegalArgumentException("The portal effect size must be at least one");
        }

        zMultip = (int) (maxXZ - minXZ + 1);
        yMultip = zMultip * zMultip;
        totalArrayLength = yMultip * (int) (maxY - minY + 1);

        lightSimulationInterval = file.getInt("lightBlockInterval");
        forceLightLevel = file.getInt("forceLightLevel");

        halfFullSize = new IntVector((maxXZ - minXZ) / 2, (maxY - minY) / 2, (maxXZ - minXZ) / 2);

        ConfigurationSection cBoxSection = Objects.requireNonNull(file.getConfigurationSection("portalCollisionBox"), "Collision box missing");
        collisionBox = new Vector(
                cBoxSection.getDouble("x"),
                cBoxSection.getDouble("y"),
                cBoxSection.getDouble("z")
        );

        blockUpdateInterval = file.getInt("portalBlockUpdateInterval");
        if(blockUpdateInterval <= 0) {
            throw new IllegalArgumentException("Block update interval must be at least 1");
        }

        entityMetadataUpdateInterval = file.getInt("entityMetadataUpdateInterval");

        worldSwitchWaitTime = file.getInt("waitTimeAfterSwitchingWorlds"); // TODO: implement or yeet
        portalBlocksHidden = file.getBoolean("hidePortalBlocks");
        blockStateRefreshInterval = file.getInt("blockStateRefreshInterval");

        String bgBlockString = file.getString("backgroundBlock", "");

        if(bgBlockString.isEmpty()) {
            backgroundBlockData = null;
        }   else    {
            backgroundBlockData = parseBlockData(bgBlockString);
        }

        worldBackgroundBlockData.clear();
        ConfigurationSection worldBgsSection = file.getConfigurationSection("worldBackgroundBlocks");

        for(String worldName : Objects.requireNonNull(worldBgsSection).getKeys(false)) {
            String bgValue = worldBgsSection.getString(worldName);

            if(bgValue == null) {
                continue;
            }

            WrappedBlockData parsedData = parseBlockData(bgValue);
            if(parsedData != null) {
                worldBackgroundBlockData.put(worldName, parsedData);
            }
        }

        intOffsets = new int[]{
                1,
                -1,
                zMultip,
                -zMultip,
                yMultip,
                -yMultip
        };
    }

    public boolean isOutsideBounds(int x, int y, int z) {
        return x <= minXZ || x >= maxXZ || y <= minY || y >= maxY || z <= minXZ || z >= maxXZ;
    }

    public WrappedBlockData findBackgroundData(PortalPosition destPosition) {
        if(backgroundBlockData != null) {
            return backgroundBlockData;
        }

        // Try to find a background data set for this world
        WrappedBlockData worldSpecificBg = worldBackgroundBlockData.get(destPosition.getWorldName());
        if(worldSpecificBg != null) {
            return worldSpecificBg;
        }

        // External world's environment types cannot be determined
        // Users can still set them specifically via the world overrides
        if(destPosition.isExternal()) {
            return WrappedBlockData.createData(Material.BLACK_CONCRETE);
        }

        World world = destPosition.getWorld();
        assert world != null;

        Material material;
        if(world.getEnvironment() == World.Environment.NORMAL)    {
            long time = world.getTime();
            if(time > 0 && time < 12300) {
                material = Material.WHITE_CONCRETE;
            }   else {
                material = Material.BLACK_CONCRETE;
            }
        }   else if(world.getEnvironment() == World.Environment.NETHER) {
            material = Material.RED_CONCRETE;
        }   else    {
            material = Material.BLACK_CONCRETE;
        }

        return WrappedBlockData.createData(material);
    }
}
