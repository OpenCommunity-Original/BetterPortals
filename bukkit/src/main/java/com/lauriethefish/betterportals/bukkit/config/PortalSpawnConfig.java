package com.lauriethefish.betterportals.bukkit.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.lauriethefish.betterportals.shared.logging.Logger;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Singleton
public class PortalSpawnConfig {
    /**
     * Minimum space from bottom of the world and top of the world that portals will be generated when using the default world link
     */
    private static final int MAIN_Y_PADDING = 5;

    /**
     * Rescaling factor from the nether to the overworld in the default world link
     */
    private static final double MAIN_RESCALE_FACTOR = 8.0;

    private final Logger logger;

    private Map<World, WorldLink> worldLinks;
    private Set<World> disabledWorlds;

    @Getter private Vector maxPortalSize; // Maximum size of natural/nether portals
    @Getter private int minimumPortalSpawnDistance; // How close portals will spawn to each other

    // Copies some blocks randomly from the destination to the origin of a portal when lit
    // Creates a cool sort of "creeping" effect for the player.
    @Getter private boolean dimensionBlendEnabled;
    @Getter private double blendFallOff;

    @Getter private double allowedSpawnTimePerTick;

    @Inject
    public PortalSpawnConfig(Logger logger) {
        this.logger = logger;
    }

    public void load(FileConfiguration file) {
        worldLinks = new HashMap<>();
        disabledWorlds = new HashSet<>();

        ConfigurationSection dimBlendSection = Objects.requireNonNull(file.getConfigurationSection("dimensionBlend"));
        dimensionBlendEnabled = dimBlendSection.getBoolean("enable");
        blendFallOff = dimBlendSection.getDouble("fallOffRate");

        ConfigurationSection worldLinksSection = Objects.requireNonNull(file.getConfigurationSection("worldConnections"), "World connections section missing");

        for (String s : worldLinksSection.getKeys(false)) {
            WorldLink newLink = new WorldLink(Objects.requireNonNull(worldLinksSection.getConfigurationSection(s)));
            if (!newLink.isValid()) {
                logger.warning("An invalid worldConnection was found in the config, please check that your world names are correct.");
                if (newLink.getOriginWorld() == null) {
                    logger.warning("No world with name \"%s\" exists (for the origin)", newLink.getOriginWorldName());
                }
                if(newLink.getDestinationWorld() == null) {
                    logger.warning("No world with name \"%s\" exists (for the destination)", newLink.getDestWorldName());
                }

                continue;
            }

            worldLinks.put(newLink.getOriginWorld(), newLink);
        }

        boolean useDefaultWorldLinks = file.getBoolean("enableDefaultWorldConnections");
        if(useDefaultWorldLinks) {
            generateDefaultLinks();
        }

        List<String> disabledWorldsString = file.getStringList("disabledWorlds");
        for(String worldString : disabledWorldsString)  {
            World world = Bukkit.getWorld(worldString);
            disabledWorlds.add(world);
        }

        ConfigurationSection maxSizeSection = Objects.requireNonNull(file.getConfigurationSection("maxPortalSize"), "Maximum portal size section missing");
        maxPortalSize = new Vector(
                maxSizeSection.getInt("x"),
                maxSizeSection.getInt("y"),
                0.0
        );

        minimumPortalSpawnDistance = file.getInt("minimumPortalSpawnDistance");
        if(minimumPortalSpawnDistance < 0) {
            throw new IllegalArgumentException("minimumPortalSpawnDistance must be at least 0 (got " + minimumPortalSpawnDistance + ")");
        }
        allowedSpawnTimePerTick = file.getDouble("allowedSpawnTimePerTick");
        if(allowedSpawnTimePerTick <= 0.0) {
            throw new IllegalArgumentException("allowedSpawnTimePerTick must be greater than 0.0 (got " + allowedSpawnTimePerTick + ")");
        }
    }

    private void generateDefaultLinks() {
        World mainWorld = Bukkit.getWorlds().get(0);
        World netherWorld = Bukkit.getWorld(mainWorld.getName() + "_nether");

        if(netherWorld == null) {
            logger.warning("Cannot add default world links - no nether world exists");
            return;
        }

        if(mainWorld.getEnvironment() != World.Environment.NORMAL) {
            logger.warning("Cannot add default world links - first world is not overworld");
            return;
        }

        if(netherWorld.getEnvironment() != World.Environment.NETHER) {
            logger.warning("Cannot add default world links - _nether world is not actually nether");
        }

        worldLinks.put(mainWorld, new WorldLink(mainWorld, netherWorld, 1 / MAIN_RESCALE_FACTOR, MAIN_Y_PADDING));
        worldLinks.put(netherWorld, new WorldLink(netherWorld, mainWorld, MAIN_RESCALE_FACTOR, MAIN_Y_PADDING));
    }

    public boolean isWorldDisabled(World world) {
        return disabledWorlds.contains(world);
    }

    public @Nullable WorldLink getWorldLink(@NotNull World originWorld) {
        return worldLinks.get(originWorld);
    }
}
