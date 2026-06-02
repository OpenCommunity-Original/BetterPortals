package org.envel.betterportals.bukkit.portal.predicate;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.envel.betterportals.api.BetterPortal;
import org.envel.betterportals.api.PortalPredicate;
import org.envel.betterportals.bukkit.net.IPortalClient;
import org.envel.betterportals.bukkit.net.requests.CheckDestinationValidityRequest;
import org.envel.betterportals.bukkit.util.VersionUtil;
import org.envel.betterportals.shared.logging.Logger;
import org.envel.betterportals.shared.net.RequestException;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class CrossServerDestinationChecker implements PortalPredicate {
    /**
     * Time, in seconds, between each check that the cross-server portal has a valid destination
     */
    private static final int VALIDITY_CHECK_INTERVAL = 1;

    private final Logger logger;
    private final IPortalClient portalClient;

    private static class CacheEntry {
        final boolean validity;
        final Instant lastChecked;

        CacheEntry(boolean validity, Instant lastChecked) {
            this.validity = validity;
            this.lastChecked = lastChecked;
        }
    }

    private final Map<BetterPortal, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Set<BetterPortal> ongoingRequest = ConcurrentHashMap.newKeySet();

    private boolean wasConnectedLastTick = true;
    private Instant lastPruneTime = Instant.now();

    @Inject
    public CrossServerDestinationChecker(Logger logger, IPortalClient portalClient) {
        this.logger = logger;
        this.portalClient = portalClient;
    }

    @Override
    public boolean test(@NotNull BetterPortal portal, @NotNull Player player) {
        if(!portal.isCrossServer()) {
            return true;
        }

        if (Duration.between(lastPruneTime, Instant.now()).getSeconds() > 10) {
            pruneCache();
        }

        if(!portalClient.canReceiveRequests()) {
            if(wasConnectedLastTick) {
                wasConnectedLastTick = false;
                logger.warning("Cross server portals deactivating - disconnected from the proxy");
            }
            return false;
        }

        if(!wasConnectedLastTick) {
            logger.info("Cross-server portals reactivating! - proxy is connected");
            wasConnectedLastTick = true;
        }

        Boolean cachedValidityValue = checkCache(portal);
        if(cachedValidityValue != null) {
            return cachedValidityValue;
        }   else    {
            // If it has been more than VALIDITY_CHECK_INTERVAL seconds since the last check, or the validity has not been cached yet, send a request to check it
            runValidityCheck(portal);

            // If the previous validity check returned successful, don't temporarily deactivate the portal while waiting to receive the response
            CacheEntry entry = cache.get(portal);
            return entry != null && entry.validity;
        }
    }

    /**
     * Checks if there is a recent enough cached validity value for <code>portal</code>.
     * @param portal The portal to check the cache for
     * @return The cached validity value, or null if there is none or the value is out of date.
     */
    private @Nullable Boolean checkCache(@NotNull BetterPortal portal) {
        CacheEntry entry = cache.get(portal);
        if(entry == null) {return null;}
        double secondsElapsed = Duration.between(entry.lastChecked, Instant.now()).getSeconds();

        if(secondsElapsed >= VALIDITY_CHECK_INTERVAL) {
            return null;
        }   else    {
            return entry.validity;
        }
    }

    /**
     * Sends a request to the proxy to check that the portal can be activated
     * @param portal The portal to check the validity of
     */
    private void runValidityCheck(@NotNull BetterPortal portal) {
        if(ongoingRequest.contains(portal)) {
            return;
        }
        ongoingRequest.add(portal);

        logger.finest("Checking validity of portal %s", portal.getId());
        CheckDestinationValidityRequest request = new CheckDestinationValidityRequest();
        request.setOriginGameVersion(VersionUtil.getCurrentVersion());
        request.setDestinationWorldId(portal.getDestPos().getWorldId());
        request.setDestinationWorldName(portal.getDestPos().getWorldName());

        portalClient.sendRequestToServer(request, portal.getDestPos().getServerName(), (response) -> {
            try {
                response.checkForErrors();
                putValidityValue(portal, true);
                logger.finest("Destination validity OK!");
            }   catch(RequestException ex) {
                // Avoid spamming validity messages by only logging when the validity changes to invalid
                CacheEntry entry = cache.get(portal);
                if(entry == null || entry.validity) {
                    logger.warning("Not activating cross server portal - destination is invalid: %s", ex.getMessage());
                }
                putValidityValue(portal, false);
            }
            ongoingRequest.remove(portal);
        });
    }

    /**
     * Caches <code>newValue</code> at the current time.
     * @param portal The portal to cache the validity for
     * @param newValue The new validity value
     */
    private void putValidityValue(@NotNull BetterPortal portal, boolean newValue) {
        cache.put(portal, new CacheEntry(newValue, Instant.now()));
    }

    public void clear() {
        cache.clear();
        ongoingRequest.clear();
        wasConnectedLastTick = true;
    }

    private void pruneCache() {
        Instant now = Instant.now();
        if (Duration.between(lastPruneTime, now).getSeconds() > 10) {
            lastPruneTime = now;
            Instant threshold = now.minus(Duration.ofSeconds(30));
            cache.entrySet().removeIf(entry -> {
                if (entry.getValue().lastChecked.isBefore(threshold)) {
                    ongoingRequest.remove(entry.getKey());
                    return true;
                }
                return false;
            });
        }
    }
}
