package com.lauriethefish.betterportals.bukkit.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultEconomyHook implements EconomyHook {
    private Economy econ;

    public VaultEconomyHook() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            econ = rsp.getProvider();
        }
    }

    public boolean isLoaded() {
        return econ != null;
    }

    @Override
    public double getBalance(Player player) {
        return econ != null ? econ.getBalance(player) : 0.0;
    }

    @Override
    public boolean hasMoney(Player player, double amount) {
        return econ != null ? econ.has(player, amount) : true;
    }

    @Override
    public boolean charge(Player player, double amount) {
        if (econ == null) return true;
        return econ.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public String format(double amount) {
        return econ != null ? econ.format(amount) : String.format("$%.2f", amount);
    }
}
