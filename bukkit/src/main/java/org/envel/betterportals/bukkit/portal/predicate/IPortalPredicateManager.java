package org.envel.betterportals.bukkit.portal.predicate;

import org.envel.betterportals.api.PortalPredicate;
import org.envel.betterportals.bukkit.portal.IPortal;
import org.bukkit.entity.Player;

/**
 * Handles registering checks to see if a portal can be activated or viewed by a player.
 * This registers the Predicates within this package by default.
 */
public interface IPortalPredicateManager {
    void addActivationPredicate(PortalPredicate predicate);
    boolean removeActivationPredicate(PortalPredicate predicate);
    void addViewPredicate(PortalPredicate predicate);
    boolean removeViewPredicate(PortalPredicate predicate);
    void addTeleportPredicate(PortalPredicate predicate);
    boolean removeTeleportPredicate(PortalPredicate predicate);

    boolean isActivatable(IPortal portal, Player player);
    boolean isViewable(IPortal portal, Player player);
    boolean canTeleport(IPortal portal, Player player);
}
