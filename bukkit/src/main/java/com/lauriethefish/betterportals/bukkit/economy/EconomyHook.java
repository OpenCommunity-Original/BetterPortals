package com.lauriethefish.betterportals.bukkit.economy;

import org.bukkit.entity.Player;

public interface EconomyHook {
    double getBalance(Player player);
    boolean hasMoney(Player player, double amount);
    boolean charge(Player player, double amount);
    String format(double amount);
}
