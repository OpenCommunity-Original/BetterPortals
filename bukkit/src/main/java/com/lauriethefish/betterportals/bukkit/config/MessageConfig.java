package com.lauriethefish.betterportals.bukkit.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.lauriethefish.betterportals.bukkit.command.framework.CommandException;
import com.lauriethefish.betterportals.bukkit.nms.NBTTagUtil;
import com.lauriethefish.betterportals.shared.logging.Logger;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Handles formatting text based on what's in the messages section of the config.
 */
@Singleton
@SuppressWarnings("deprecation")
public class MessageConfig {
    private static final String PORTAL_WAND_TAG = "portalWand";

    private final Logger logger;
    private final io.foxserver.common.locale.LocaleAPI localeApi;
    private final Map<String, String> messageMap = new HashMap<>();

    private String portalWandName;
    @Getter private String prefix;
    @Getter private String messageColor;

    private ItemStack portalWand = null;

    @Inject
    public MessageConfig(Logger logger, io.foxserver.common.locale.LocaleAPI localeApi) {
        this.logger = logger;
        this.localeApi = localeApi;
    }

    public void load(FileConfiguration file) {
        ConfigurationSection messagesSection = file.getConfigurationSection("chatMessages");
        if(messagesSection != null) {
            for(String key : messagesSection.getKeys(false)) {
                messageMap.put(key, translateColorCodes(messagesSection.getString(key)));
            }
            prefix = getRawMessage("prefix");
            messageColor = translateColorCodes(messagesSection.getString("messageColor"));
        }

        if(prefix == null) {
            prefix = translateColorCodes("&7[&aBetterPortals&7]&a ");
        }
        if(messageColor == null) {
            messageColor = translateColorCodes("&a");
        }

        portalWandName = translateColorCodes(Objects.requireNonNull(file.getString("portalWandName"), "Missing portalWandName"));
    }

    /**
     * Translates both the <code>&</code> color codes, and hex colours if on 1.16 spigot.
     * @param message The message to translate
     * @return The translated message with the colours
     */
    private @NotNull String translateColorCodes(@NotNull String message) {
        message = ChatColor.translateAlternateColorCodes('&', message);

        return translateHexColors(message);
    }

    /**
     * Translates hex colour codes in <code>message</code>, e.g. <code>{(#000000)}</code> is black.
     * Invalid colour codes print a warning and are removed.
     * @param message The message to translate
     * @return The translated message with the colours
     */
    private @NotNull String translateHexColors(@NotNull String message) {
        StringBuilder result = new StringBuilder();
        StringBuilder currentSegment = null;

        for(char c : message.toCharArray()) {
            // Start a new segment if we reach an opening curly bracket
            if(c == '{' && currentSegment == null) {
                currentSegment = new StringBuilder();
            }

            // Add to the current {...} segment if we are in one, otherwise we just add to the resultant string
            if(currentSegment == null) {
                result.append(c);
            }   else    {
                currentSegment.append(c);
            }

            // If we reach a closing curly bracket, we have reached the end of the current segment
            if(c == '}' && currentSegment != null) {
                String segment = currentSegment.toString();
                boolean parsingFailed = true;
                // Hex colours should be {(#000000)}, so the 2nd and 2nd to last character of the segment should be ( and ) respectively (segments include the curly brackets)
                if(segment.charAt(1) == '(' && segment.charAt(segment.length() - 2) == ')') {
                    String hexString = segment.substring(2, segment.length() - 2); // Get the #000000 part of the segment

                    try {
                        result.append(net.md_5.bungee.api.ChatColor.of(hexString));
                        parsingFailed = false;
                    }   catch(IllegalArgumentException ex) {
                        logger.warning("Failed to parse hex colour: %s", hexString);
                    }
                }

                // Just add the segment as it was if parsing fails
                if(parsingFailed) {
                    result.append(segment);
                }

                currentSegment = null;
            }
        }

        return result.toString();
    }

    /**
     * @return The wand with the NBT tags for creating portals
     */
    public @NotNull ItemStack getPortalWand() {
        if(portalWand == null) {
            portalWand = new ItemStack(Material.BLAZE_ROD);

            ItemMeta meta = portalWand.getItemMeta();
            assert meta != null;
            meta.setDisplayName(portalWandName);

            portalWand.setItemMeta(meta);
            // Portal wand checking is done with an NBT tag
            portalWand = NBTTagUtil.addMarkerTag(portalWand, PORTAL_WAND_TAG);
        }

        return portalWand;
    }

    /**
     * Checks if <code>item</code> is a portal wand
     * @param item The item to test
     * @return true if it is a valid portal wand, false otherwise
     */
    public boolean isPortalWand(ItemStack item) {
        return NBTTagUtil.hasMarkerTag(item, PORTAL_WAND_TAG);
    }

    private String formatMiniMessage(String message) {
        if (message == null || message.isEmpty()) return "";
        try {
            return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(message)
            );
        } catch (Exception e) {
            return message;
        }
    }

    /**
     * Finds a chat message with the plugin prefix.
     * @param name The name in the config
     * @return A chat message with the configured plugin prefix
     */
    public String getChatMessage(org.bukkit.command.CommandSender sender, String name) {
        Player player = (sender instanceof Player) ? (Player) sender : null;
        String raw = localeApi.getRaw(player, name);
        if (raw == null) raw = getRawMessage(name);
        if (raw == null) return "";
        return formatMiniMessage(getPrefix(player) + raw);
    }

    public String getChatMessage(String name) {
        return getChatMessage(null, name);
    }

    public String getErrorMessage(org.bukkit.command.CommandSender sender, String name) {
        Player player = (sender instanceof Player) ? (Player) sender : null;
        String raw = localeApi.getRaw(player, name);
        if (raw == null) raw = getRawMessage(name);
        if (raw == null) return "";
        return formatMiniMessage(raw);
    }

    public String getErrorMessage(String name) {
        return getErrorMessage(null, name);
    }

    public String getWarningMessage(org.bukkit.command.CommandSender sender, String name) {
        Player player = (sender instanceof Player) ? (Player) sender : null;
        String raw = localeApi.getRaw(player, name);
        if (raw == null) raw = getRawMessage(name);
        if (raw == null || raw.isEmpty()) return "";
        return formatMiniMessage("<yellow>" + raw + "</yellow>");
    }

    public String getWarningMessage(String name) {
        return getWarningMessage(null, name);
    }

    public String getPrefix(Player player) {
        String rawPrefix = localeApi.getRaw(player, "prefix");
        return rawPrefix != null ? rawPrefix : prefix;
    }

    public String getRawMessage(String name) {
        return messageMap.get(name);
    }
}
