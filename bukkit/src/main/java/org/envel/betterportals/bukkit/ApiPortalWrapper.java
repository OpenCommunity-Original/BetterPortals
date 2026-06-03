package org.envel.betterportals.bukkit;

import org.envel.betterportals.api.BetterPortal;
import org.envel.betterportals.api.BetterPortalsAPI;
import org.envel.betterportals.api.PortalPosition;
import org.envel.betterportals.bukkit.portal.IPortalManager;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A wrapper around {@link BetterPortal} that resolves the underlying portal implementation
 * dynamically from the active {@link IPortalManager} on each call.
 * This prevents memory leaks if an API caller retains references to portals after a plugin reload.
 */
public class ApiPortalWrapper implements BetterPortal {
    private final UUID id;

    public ApiPortalWrapper(UUID id) {
        this.id = id;
    }

    private BetterPortal getDelegate() {
        BetterPortalsAPI apiInstance = BetterPortalsAPI.get();
        if (!(apiInstance instanceof API)) {
            throw new IllegalStateException("API is not fully initialized");
        }
        IPortalManager portalManager = ((API) apiInstance).getPortalManager();
        BetterPortal portal = portalManager.getPortalById(id);
        if (portal == null) {
            throw new IllegalStateException("Portal with ID " + id + " is no longer registered");
        }
        return portal;
    }

    @Override
    public @NotNull UUID getId() {
        return id;
    }

    @Override
    public @Nullable UUID getOwnerId() {
        return getDelegate().getOwnerId();
    }

    @Override
    public @Nullable String getName() {
        return getDelegate().getName();
    }

    @Override
    public void setName(@Nullable String name) {
        getDelegate().setName(name);
    }

    @Override
    public @NotNull PortalPosition getOriginPos() {
        return getDelegate().getOriginPos();
    }

    @Override
    public @NotNull PortalPosition getDestPos() {
        return getDelegate().getDestPos();
    }

    @Override
    public @NotNull Vector getSize() {
        return getDelegate().getSize();
    }

    @Override
    public boolean isCrossServer() {
        return getDelegate().isCrossServer();
    }

    @Override
    public boolean isCustom() {
        return getDelegate().isCustom();
    }

    @Override
    public void remove(boolean removeOtherDirection) {
        getDelegate().remove(removeOtherDirection);
    }
}
