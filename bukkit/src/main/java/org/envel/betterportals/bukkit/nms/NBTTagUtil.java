package org.envel.betterportals.bukkit.nms;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Used to add simple marker tags to items, for example, the portal wand
 */
public class NBTTagUtil {
    private static final NamespacedKey MARKER_KEY = new NamespacedKey("betterportals", "marker");
    private static final String MARKER_VALUE = "marked";

    /**
     * Adds a marker tag to <code>item</code>.
     * @param item Item to add the tag to
     * @param name Name of the tag
     * @return A new {@link ItemStack} with the tag.
     */
    @NotNull
    public static ItemStack addMarkerTag(@NotNull ItemStack item, @NotNull String name) {
        ItemStack newItem = item.clone();

        var meta = newItem.getItemMeta();
        if (meta == null) {
            return newItem;
        }

        // Add the marker to the Persistent Data Container
        meta.getPersistentDataContainer().set(MARKER_KEY, PersistentDataType.STRING, MARKER_VALUE + "_" + name);
        newItem.setItemMeta(meta);

        return newItem;
    }

    /**
     * Checks if <code>item</code> has a marker tag with <code>name</code>.
     * @param item The item to check
     * @param name The name of the marker tag
     * @return Whether it has the tag
     */
    public static boolean hasMarkerTag(@NotNull ItemStack item, @NotNull String name) {
        var meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        // Check if the PDC contains the marker with the expected name
        String value = meta.getPersistentDataContainer().get(MARKER_KEY, PersistentDataType.STRING);
        return value != null && value.equals(MARKER_VALUE + "_" + name);
    }
}
