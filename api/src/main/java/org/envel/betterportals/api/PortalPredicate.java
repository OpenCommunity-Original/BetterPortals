package org.envel.betterportals.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Used to allow/disallow viewing/using/activating portals depending on various factors.
 */
public interface PortalPredicate {
    /**
     * Determines if the given player is permitted to use this portal for whatever this predicate is registered for (viewing or teleportation)
     * @param portal The portal to test if allowed
     * @param player The player attempting to use the portal
     * @return If they are allowed to view/teleport through the portal
     */
    boolean test(@NotNull BetterPortal portal, @NotNull Player player);
}
