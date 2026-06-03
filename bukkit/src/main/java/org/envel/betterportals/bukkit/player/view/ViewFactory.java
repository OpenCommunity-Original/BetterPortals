package org.envel.betterportals.bukkit.player.view;

import org.envel.betterportals.bukkit.player.view.block.IPlayerBlockView;
import org.envel.betterportals.bukkit.player.view.entity.IPlayerEntityView;
import org.envel.betterportals.bukkit.portal.IPortal;
import org.bukkit.entity.Player;

public interface ViewFactory {
    IPlayerBlockView createBlockView(Player player, IPortal portal);
    IPlayerEntityView createEntityView(Player player, IPortal portal);
}
