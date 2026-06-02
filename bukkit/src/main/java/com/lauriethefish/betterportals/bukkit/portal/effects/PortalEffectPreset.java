package com.lauriethefish.betterportals.bukkit.portal.effects;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PortalEffectPreset {
    private final String name;
    private final Particle particle;
    private final int particleCount;
    private final double particleSpeed;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;

    private final Sound sound;
    private final float soundVolume;
    private final float soundPitch;
    private final int soundIntervalTicks;

    public PortalEffectPreset(@NotNull String name, @NotNull ConfigurationSection section) {
        this.name = name;
        Particle p;
        try {
            p = Particle.valueOf(section.getString("particle.type", "PORTAL").toUpperCase());
        } catch (IllegalArgumentException ex) {
            p = Particle.PORTAL;
        }
        this.particle = p;
        this.particleCount = section.getInt("particle.count", 3);
        this.particleSpeed = section.getDouble("particle.speed", 0.05);
        this.offsetX = section.getDouble("particle.offsetX", 0.5);
        this.offsetY = section.getDouble("particle.offsetY", 0.5);
        this.offsetZ = section.getDouble("particle.offsetZ", 0.5);

        Sound s = null;
        String soundStr = section.getString("sound.type");
        if (soundStr != null && !soundStr.isEmpty()) {
            try {
                s = Sound.valueOf(soundStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        this.sound = s;
        this.soundVolume = (float) section.getDouble("sound.volume", 0.5);
        this.soundPitch = (float) section.getDouble("sound.pitch", 1.0);
        this.soundIntervalTicks = section.getInt("sound.interval", 80); // 4 seconds by default
    }

    public String getName() { return name; }
    public Particle getParticle() { return particle; }
    public int getParticleCount() { return particleCount; }
    public double getParticleSpeed() { return particleSpeed; }
    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public double getOffsetZ() { return offsetZ; }

    @Nullable
    public Sound getSound() { return sound; }
    public float getSoundVolume() { return soundVolume; }
    public float getSoundPitch() { return soundPitch; }
    public int getSoundIntervalTicks() { return soundIntervalTicks; }
}
