package org.envel.betterportals.bukkit.portal.predicate;

import com.google.inject.Inject;
import org.envel.betterportals.api.BetterPortal;
import org.envel.betterportals.api.PortalPredicate;
import org.envel.betterportals.bukkit.economy.EconomyManager;
import org.envel.betterportals.bukkit.portal.IPortal;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyChargeChecker implements PortalPredicate {
    private final EconomyManager economyManager;
    private final Map<UUID, Long> messageCooldowns = new HashMap<>();

    @Inject
    public EconomyChargeChecker(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @Override
    public boolean test(@NotNull BetterPortal portal, @NotNull Player player) {
        IPortal iPortal = (IPortal) portal;
        double price = iPortal.getPrice();
        if (price <= 0.0) return true;

        if (!economyManager.isEconomyEnabled()) {
            return true;
        }

        if (economyManager.hasMoney(player, price)) {
            if (economyManager.charge(player, price)) {
                player.sendMessage(ChatColor.GREEN + "Charged " + economyManager.format(price) + " to use this portal.");
                return true;
            }
        }

        // Handle cooldown on message to prevent spamming
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        if (now - messageCooldowns.getOrDefault(playerId, 0L) > 3000) {
            player.sendMessage(ChatColor.RED + "You need " + economyManager.format(price) + " to pass through this portal! (Your balance: " + economyManager.format(economyManager.getBalance(player)) + ")");
            messageCooldowns.put(playerId, now);
        }

        return false;
    }
}
