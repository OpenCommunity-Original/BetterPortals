package org.envel.betterportals;

/**
 * References entry points of different parts of the plugin to exclude them from minimisation
 */
public class MinimisationEntryPoint {
    private static final Class<?> BUKKIT = org.envel.betterportals.bukkit.BetterPortals.class;
    private static final Class<?> BUNGEE = org.envel.betterportals.bungee.BetterPortals.class;
    private static final Class<?> VELOCITY = org.envel.betterportals.velocity.BetterPortals.class;
}
