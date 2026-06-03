package org.envel.betterportals.bukkit.entity;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Represents an implementation to find entities near a point
 * This has both an NMS and bukkit implementation
 */
public interface IEntityFinder {
    /**
     * Finds the nearby entities in a given area, in a box, and collects them.
     * @param existing An existing collection to be reused to avoid re-allocating memory. This can be null, and the implementor doesn't have to use it
     * @param location The location to find the entities from
     * @param xRadius X radius from the location
     * @param yRadius Y radius from the location
     * @param zRadius Z radius from the location
     * @return A collection of the nearby entities
     */
    Collection<Entity> getNearbyEntities(@Nullable Collection<Entity> existing, Location location, double xRadius, double yRadius, double zRadius);

    /**
     * Finds the nearby entities in a given area, in a box, and then sends them through a consumer.
     * This should be preferred if a collection of the entities is not required
     * @param location The location to find the entities from
     * @param xRadius X radius from the location
     * @param yRadius Y radius from the location
     * @param zRadius Z radius from the location
     * @param consumer Consumer to send the found entities through
     */
    void getNearbyEntities(Location location, double xRadius, double yRadius, double zRadius, Consumer<Entity> consumer);
}
