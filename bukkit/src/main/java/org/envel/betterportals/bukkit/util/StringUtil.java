package org.envel.betterportals.bukkit.util;


import org.bukkit.Location;

/**
 * Small utility with some less verbose <code>toString</code> implementations for various classes.
 */
public class StringUtil {
    /**
     * Formats a location to a string, displaying only the block of each coordinate and the world name (i.e. the floored coordinate)
     * @param location The location to format
     * @return The formatted location string
     */
    public static String blockLocationToString(Location location) {
        return String.format("(%d, %d, %d, %s)", location.getBlockX(), location.getBlockY(), location.getBlockZ(), location.getWorld() == null ? "null" : location.getWorld().getName());
    }

    /**
     * Formats a location to a string, displaying each coordinate to 2 decimal places, and the world name
     * @param location The location to format
     * @return The formatted location string
     */
    public static String locationToString(Location location) {
        return String.format("(%.02f, %.02f, %.02f, %s)", location.getX(), location.getY(), location.getZ(), location.getWorld() == null ? "null" : location.getWorld().getName());
    }
}
