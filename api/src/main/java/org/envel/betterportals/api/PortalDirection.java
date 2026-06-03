package org.envel.betterportals.api;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a portal's direction. Portals can not be diagonal, only up/down/left/right/forward/backwards.
 */
public enum PortalDirection {
    /**
     * Up direction
     */
    UP(new Vector(0.0, 1.0, 0.0), new Vector(1.0, 0.0, 0.0)),

    /**
     * Down direction
     */
    DOWN(new Vector(0.0, -1.0, 0.0), new Vector(1.0, 0.0, 0.0)),

    /**
     * North (+z) direction
     */
    NORTH(new Vector(0.0, 0.0, 1.0), new Vector(0.0, 1.0, 0.0)),

    /**
     * South (-z) direction
     */
    SOUTH(new Vector(0.0, 0.0, -1.0), new Vector(0.0, 1.0, 0.0)),

    /**
     * East (+x) direction
     */
    EAST(new Vector(1.0, 0.0, 0.0), new Vector(0.0, 1.0, 0.0)),

    /**
     * West (-x) direction
     */
    WEST(new Vector(-1.0, 0.0, 0.0), new Vector(0.0, 1.0, 0.0));

    private final Vector direction;

    private final Vector inversionRotationAxis;
    PortalDirection(Vector direction, Vector inversionRotationAxis)   {
        this.direction = direction;
        this.inversionRotationAxis = inversionRotationAxis;
    }

    /**
     * Loads a {@link PortalDirection} from a storage string
     * This will return <code>NORTH</code> if <code>EAST_WEST</code> is inputted, and <code>EAST</code> if <code>NORTH_SOUTH</code> is inputted for backwards compatibility.
     * Otherwise it is equivalent to the enum's <code>valueOf</code> method
     * @param string Portal direction string to load from
     * @return Parsed portal direction
     */
    public static PortalDirection fromStorage(String string) {
        switch(string) {
            case "EAST_WEST":
                return NORTH;
            case "NORTH_SOUTH":
                return EAST;
            default:
                return valueOf(string);
        }
    }

    /**
     * @return A normal facing out of a portal with this direction.
     */
    public Vector toVector()    {
        return direction;
    }

    /**
     * Inverting requires using the correct axis to avoid weird rotation, like the player being flipped in 2 directions instead of one.
     * @return The rotation axis to use for inversion with a matrix.
     */
    public Vector getInversionRotationAxis()    {
        return inversionRotationAxis;
    }

    /**
     * Used in areas where we need to iterate over blocks around a portal, and need it to work regardless of the direction
     * @param vec The vector to swap
     * @return The swapped vector
     */
    public @NotNull Vector swapVector(@NotNull Vector vec)    {
        switch(this)    {
            case EAST:
            case WEST:
                return new Vector(vec.getZ(), vec.getY(), vec.getX());
            case UP:
            case DOWN:
                return new Vector(vec.getX(), vec.getZ(), vec.getY());
            case NORTH:
            case SOUTH:
                return vec.clone();
        }
        throw new IllegalStateException("This should never happen");
    }

    /**
     * Used in areas where we need to iterate over blocks around a portal, and need it to work regardless of the direction
     * @param vec The integer vector to swap
     * @return The swapped vector
     */
    public @NotNull IntVector swapVector(@NotNull IntVector vec) {
        switch(this)    {
            case EAST:
            case WEST:
                return new IntVector(vec.getZ(), vec.getY(), vec.getX());
            case UP:
            case DOWN:
                return new IntVector(vec.getX(), vec.getZ(), vec.getY());
            case NORTH:
            case SOUTH:
                return vec.clone();
        }
        throw new IllegalStateException("This should never happen");
    }

    /**
     * Used in areas where we need to iterate over blocks around a portal, and need it to work regardless of the direction
     * @param loc The location to swap
     * @return The swapped location
     */
    public Location swapLocation(@NotNull Location loc)  {
        return swapVector(loc.toVector()).toLocation(loc.getWorld());
    }


    /**
     * Finds if this portal is a horizontal portal (i.e. flat)
     * @return If this portal points up or down
     */
    public boolean isHorizontal() {
        return this == UP || this == DOWN;
    }

    /**
     * @return The opposite direction to this {@link PortalDirection}. e.g. {@link PortalDirection#NORTH} returns {@link PortalDirection#SOUTH}
     */
    public @NotNull PortalDirection getOpposite()    {
        switch(this) {
            case UP:
                return DOWN;
            case DOWN:
                return UP;
            case NORTH:
                return SOUTH;
            case SOUTH:
                return NORTH;
            case EAST:
                return WEST;
            case WEST:
                return EAST;
        }
        throw new IllegalStateException("This should never happen");
    }
}