package com.lauriethefish.betterportals.bukkit.util;

import org.bukkit.Bukkit;

/**
 * Small utility to fetch the Minecraft version of the server.
 */
public class VersionUtil {
    /**
     * Returns the current Minecraft version as a string (e.g., "1.21").
     * @return Current Minecraft version.
     */
    public static String getCurrentVersion() {
        return Bukkit.getServer().getMinecraftVersion();
    }
}
