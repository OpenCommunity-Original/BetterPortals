package com.lauriethefish.betterportals.bukkit.util;

import org.bukkit.World;

public class HeightUtil {
    public static int getMaxHeight(World world) {
        return world.getMaxHeight();
    }

    public static int getMinHeight(World world) {
        return world.getMinHeight();
    }
}
