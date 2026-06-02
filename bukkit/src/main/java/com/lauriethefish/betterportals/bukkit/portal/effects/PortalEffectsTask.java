package com.lauriethefish.betterportals.bukkit.portal.effects;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.lauriethefish.betterportals.bukkit.portal.IPortal;
import com.lauriethefish.betterportals.bukkit.portal.IPortalManager;
import com.lauriethefish.betterportals.bukkit.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

@Singleton
public class PortalEffectsTask implements Runnable {
    private final JavaPlugin plugin;
    private final IPortalManager portalManager;
    private final Map<String, PortalEffectPreset> presets = new HashMap<>();
    private SchedulerUtil.PortalTask task;
    private long tickCount = 0;

    public java.util.Collection<String> getPresetNames() {
        return presets.keySet();
    }

    public PortalEffectPreset getPreset(String name) {
        return presets.get(name.toLowerCase());
    }

    @Inject
    public PortalEffectsTask(JavaPlugin plugin, IPortalManager portalManager) {
        this.plugin = plugin;
        this.portalManager = portalManager;
        loadPresets();
    }

    public void loadPresets() {
        presets.clear();
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("portalEffects");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection subSection = section.getConfigurationSection(key);
                if (subSection != null) {
                    presets.put(key.toLowerCase(), new PortalEffectPreset(key, subSection));
                }
            }
        }
        // Add a default fallback preset
        if (!presets.containsKey("default")) {
            config.addDefault("portalEffects.default.particle.type", "PORTAL");
            config.addDefault("portalEffects.default.particle.count", 3);
            config.addDefault("portalEffects.default.particle.speed", 0.05);
            config.addDefault("portalEffects.default.sound.type", "BLOCK_PORTAL_AMBIENT");
            config.addDefault("portalEffects.default.sound.volume", 0.15);
            config.addDefault("portalEffects.default.sound.pitch", 1.0);
            config.addDefault("portalEffects.default.sound.interval", 80);
            plugin.saveConfig();
            
            ConfigurationSection defSection = config.getConfigurationSection("portalEffects.default");
            if (defSection != null) {
                presets.put("default", new PortalEffectPreset("default", defSection));
            }
        }
    }

    public void start() {
        if (task != null) {
            task.cancel();
        }
        task = SchedulerUtil.runTaskTimer(this, 0L, 10L); // check every 10 ticks
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public void run() {
        tickCount += 10;
        for (IPortal portal : portalManager.getAllPortals()) {
            // Find appropriate preset
            String presetName = portal.getEffectPreset();
            if (presetName == null) {
                presetName = "default";
            }
            PortalEffectPreset preset = presets.get(presetName.toLowerCase());
            if (preset == null) {
                preset = presets.get("default");
            }
            if (preset == null) continue;

            Location loc = portal.getOriginPos().getLocation();
            World world = loc.getWorld();
            if (world == null) continue;

            // Spawning particles
            world.spawnParticle(
                    preset.getParticle(),
                    loc.getX(), loc.getY(), loc.getZ(),
                    preset.getParticleCount(),
                    preset.getOffsetX(), preset.getOffsetY(), preset.getOffsetZ(),
                    preset.getParticleSpeed()
            );

            // Spawning sound at intervals
            if (portal.isSoundEnabled() && preset.getSound() != null && tickCount % preset.getSoundIntervalTicks() == 0) {
                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distanceSquared(loc) < 2500) { // 50 blocks
                        player.playSound(loc, preset.getSound(), preset.getSoundVolume(), preset.getSoundPitch());
                    }
                }
            }
        }
    }
}
