package org.envel.betterportals.bukkit.nms;

import org.envel.betterportals.shared.util.ReflectionUtil;
import org.bukkit.Bukkit;

public class CraftBukkitClassUtil {
    private static final String CRAFTBUKKIT_PACKAGE = Bukkit.getServer().getClass().getPackage().getName();

    /**
     * Finds a class in the <code>org.bukkit.craftbukkit.version</code> package
     * @param name Name of the class relative to this package, with no dot at the start.
     * @return The located class
     */
    public static Class<?> findCraftBukkitClass(String name) {
        return ReflectionUtil.findClass(CRAFTBUKKIT_PACKAGE + "." + name);
    }
}
