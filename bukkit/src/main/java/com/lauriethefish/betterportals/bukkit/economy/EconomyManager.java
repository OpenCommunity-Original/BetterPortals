package com.lauriethefish.betterportals.bukkit.economy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@Singleton
public class EconomyManager {
    private EconomyHook hook = null;
    private boolean isSetup = false;

    @Inject
    public EconomyManager() {
        setupEconomy();
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        try {
            // Instantiate VaultEconomyHook via a helper method to ensure ClassNotFoundException
            // doesn't happen when checking the class at startup unless Vault is present.
            VaultEconomyHook vaultHook = new VaultEconomyHook();
            if (vaultHook.isLoaded()) {
                hook = vaultHook;
                isSetup = true;
            }
        } catch (Throwable ignored) {
            // Ignore if Vault isn't actually loaded or doesn't have economy provider
        }
    }

    public boolean isEconomyEnabled() {
        if (!isSetup) {
            setupEconomy();
        }
        return isSetup && hook != null;
    }

    public double getBalance(Player player) {
        if (!isEconomyEnabled()) return 0.0;
        return hook.getBalance(player);
    }

    public boolean hasMoney(Player player, double amount) {
        if (!isEconomyEnabled()) return true;
        return hook.hasMoney(player, amount);
    }

    public boolean charge(Player player, double amount) {
        if (!isEconomyEnabled() || amount <= 0.0) return true;
        return hook.charge(player, amount);
    }

    public String format(double amount) {
        if (!isEconomyEnabled()) return String.format("$%.2f", amount);
        return hook.format(amount);
    }
}
