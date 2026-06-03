package org.envel.betterportals.bukkit.portal.predicate;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.envel.betterportals.api.BetterPortal;
import org.envel.betterportals.api.PortalPredicate;
import org.envel.betterportals.bukkit.config.MiscConfig;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Singleton
public class TeleportCooldownChecker implements PortalPredicate, org.bukkit.event.Listener {
    private final MiscConfig miscConfig;
    private final Map<UUID, Long> lastTeleportTime = new java.util.concurrent.ConcurrentHashMap<>();

    @Inject
    public TeleportCooldownChecker(MiscConfig miscConfig, org.envel.betterportals.bukkit.events.IEventRegistrar eventRegistrar) {
        this.miscConfig = miscConfig;
        eventRegistrar.register(this);
    }

    @Override
    public boolean test(@NotNull BetterPortal portal, @NotNull Player player) {
        long cooldownMs = miscConfig.getTeleportCooldown() * 1000L;
        if (cooldownMs <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        Long lastTime = lastTeleportTime.get(player.getUniqueId());
        if (lastTime != null && (now - lastTime) < cooldownMs) {
            return false;
        }

        lastTeleportTime.put(player.getUniqueId(), now);
        return true;
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        lastTeleportTime.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }
}

