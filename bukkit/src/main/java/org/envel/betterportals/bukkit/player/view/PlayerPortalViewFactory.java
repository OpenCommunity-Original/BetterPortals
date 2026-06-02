package org.envel.betterportals.bukkit.player.view;

import org.envel.betterportals.bukkit.portal.IPortal;
import org.bukkit.entity.Player;

public interface PlayerPortalViewFactory {
    IPlayerPortalView create(Player player, IPortal viewedPortal);
}
