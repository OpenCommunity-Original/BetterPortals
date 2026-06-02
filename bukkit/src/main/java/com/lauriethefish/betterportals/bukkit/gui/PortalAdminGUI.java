package com.lauriethefish.betterportals.bukkit.gui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.lauriethefish.betterportals.bukkit.events.IEventRegistrar;
import com.lauriethefish.betterportals.bukkit.portal.IPortal;
import com.lauriethefish.betterportals.bukkit.portal.IPortalManager;
import com.lauriethefish.betterportals.bukkit.portal.effects.PortalEffectPreset;
import com.lauriethefish.betterportals.bukkit.portal.effects.PortalEffectsTask;
import io.foxserver.common.locale.LocaleAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

@Singleton
public class PortalAdminGUI implements Listener {
    private final IPortalManager portalManager;
    private final PortalEffectsTask effectsTask;
    private final LocaleAPI localeApi;
    private final Map<UUID, GUISession> activeSessions = new HashMap<>();

    private static class GUISession {
        int viewState; // 0 = LIST, 1 = EDITOR, 2 = EFFECTS
        int page;
        IPortal targetPortal; // active portal being edited if viewState is EDITOR/EFFECTS
        Inventory inventory;
        List<IPortal> portalsCache;
    }

    @Inject
    public PortalAdminGUI(IEventRegistrar eventRegistrar, IPortalManager portalManager, PortalEffectsTask effectsTask, LocaleAPI localeApi) {
        this.portalManager = portalManager;
        this.effectsTask = effectsTask;
        this.localeApi = localeApi;
        eventRegistrar.register(this);
    }

    public void open(Player player) {
        openListPage(player, 0);
    }

    private Component getTitle(Player player, String key, Object... replacements) {
        String msg = localeApi.getMessage(player, key, replacements);
        if (msg == null) return Component.text(key);
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(msg);
    }

    private void openListPage(Player player, int page) {
        List<IPortal> portals = new ArrayList<>();
        for (IPortal p : portalManager.getAllPortals()) {
            if (p.isCustom()) portals.add(p);
        }

        Component title = getTitle(player, "gui_title_list", "{page}", String.valueOf(page + 1));
        Inventory inv = Bukkit.createInventory(null, 54, title);
        fillBackground(inv, 45, 54);

        int startIdx = page * 45;
        int endIdx = Math.min(startIdx + 45, portals.size());

        int slot = 0;
        for (int i = startIdx; i < endIdx; i++) {
            inv.setItem(slot++, createPortalListItem(player, portals.get(i)));
        }

        if (page > 0) {
            inv.setItem(45, createLocalizedItem(player, Material.ARROW, "gui_button_prev", null));
        }
        if (endIdx < portals.size()) {
            inv.setItem(53, createLocalizedItem(player, Material.ARROW, "gui_button_next", null));
        }

        GUISession session = new GUISession();
        session.viewState = 0;
        session.page = page;
        session.inventory = inv;
        session.portalsCache = portals;
        activeSessions.put(player.getUniqueId(), session);

        player.openInventory(inv);
    }

    private void openEditor(Player player, IPortal portal) {
        Component title = getTitle(player, "gui_title_editor");
        Inventory inv = Bukkit.createInventory(null, 27, title);
        fillBackground(inv, 0, 27);

        inv.setItem(10, createLocalizedItem(player, Material.COMPASS, "gui_button_teleport", "gui_lore_teleport"));
        
        String allowItems = localeApi.getMessage(player, portal.allowsNonPlayerTeleportation() ? "gui_status_enabled" : "gui_status_disabled");
        inv.setItem(11, createLocalizedItem(player, Material.HOPPER, "gui_button_item_mob", "gui_lore_click_toggle", "{status}", allowItems));

        inv.setItem(12, createLocalizedItem(player, Material.RED_WOOL, "gui_button_dec_price", "gui_lore_dec_price"));
        inv.setItem(13, createLocalizedItem(player, Material.GOLD_BLOCK, "gui_button_price", "gui_lore_price", "{price}", String.format("$%.2f", portal.getPrice())));
        inv.setItem(14, createLocalizedItem(player, Material.LIME_WOOL, "gui_button_inc_price", "gui_lore_inc_price"));

        String presetName = portal.getEffectPreset() != null ? portal.getEffectPreset() : "default";
        inv.setItem(15, createLocalizedItem(player, Material.BLAZE_POWDER, "gui_button_effects", "gui_lore_effects", "{preset}", presetName));

        String soundStatus = localeApi.getMessage(player, portal.isSoundEnabled() ? "gui_status_enabled" : "gui_status_disabled");
        inv.setItem(16, createLocalizedItem(player, Material.NOTE_BLOCK, "gui_button_sound", "gui_lore_sound", "{status}", soundStatus));

        inv.setItem(17, createLocalizedItem(player, Material.BARRIER, "gui_button_delete", "gui_lore_delete"));
        inv.setItem(22, createLocalizedItem(player, Material.ARROW, "gui_button_back", null));

        GUISession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            session = new GUISession();
        }
        session.viewState = 1;
        session.targetPortal = portal;
        session.inventory = inv;
        activeSessions.put(player.getUniqueId(), session);

        player.openInventory(inv);
    }

    private void openEffectsMenu(Player player, IPortal portal) {
        Component title = getTitle(player, "gui_title_effects");
        Inventory inv = Bukkit.createInventory(null, 27, title);
        fillBackground(inv, 0, 27);

        int slot = 10;
        for (String presetName : effectsTask.getPresetNames()) {
            if (slot > 16) break;
            PortalEffectPreset preset = effectsTask.getPreset(presetName);
            if (preset == null) continue;
            inv.setItem(slot++, createPresetItem(player, portal, preset));
        }

        inv.setItem(22, createLocalizedItem(player, Material.ARROW, "gui_button_back_to_editor", null));

        GUISession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            session = new GUISession();
        }
        session.viewState = 2;
        session.targetPortal = portal;
        session.inventory = inv;
        activeSessions.put(player.getUniqueId(), session);

        player.openInventory(inv);
    }

    private void fillBackground(Inventory inv, int start, int end) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            pane.setItemMeta(meta);
        }
        for (int i = start; i < end; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, pane);
            }
        }
    }

    private ItemStack createPortalListItem(Player player, IPortal portal) {
        ItemStack item = new ItemStack(Material.END_PORTAL_FRAME);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = portal.getName() != null ? portal.getName() : localeApi.getMessage(player, "gui_list_default_portal_name");
            if (name == null) name = "Custom Portal";
            meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gold>" + name + "</gold>"));

            String idStr = portal.getId().toString().substring(0, 8);
            String originStr = formatLocation(portal.getOriginPos().getLocation());
            String priceStr = String.format("$%.2f", portal.getPrice());
            String presetStr = portal.getEffectPreset() != null ? portal.getEffectPreset() : "default";

            String loreStr = localeApi.getMessage(player, "gui_list_item_lore",
                    "{id}", idStr,
                    "{origin}", originStr,
                    "{price}", priceStr,
                    "{preset}", presetStr
            );
            if (loreStr != null) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreStr.split("\n")) {
                    lore.add(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(line));
                }
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPresetItem(Player player, IPortal portal, PortalEffectPreset preset) {
        String activePresetName = portal.getEffectPreset();
        if (activePresetName == null) activePresetName = "default";
        boolean isActive = activePresetName.equalsIgnoreCase(preset.getName());

        ItemStack item = new ItemStack(isActive ? Material.BLAZE_POWDER : Material.GUNPOWDER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String nameFormat = localeApi.getMessage(player, isActive ? "gui_effects_active" : "gui_effects_inactive", "{name}", preset.getName());
            if (nameFormat != null) {
                meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(nameFormat));
            }

            String particleStr = preset.getParticle().name();
            String countStr = String.valueOf(preset.getParticleCount());
            String speedStr = String.format("%.2f", preset.getParticleSpeed());
            String soundStr = preset.getSound() != null ? preset.getSound().name() : "None";
            String volumeStr = String.format("%.2f", preset.getSoundVolume());
            String pitchStr = String.format("%.2f", preset.getSoundPitch());
            String intervalStr = String.valueOf(preset.getSoundIntervalTicks());
            String statusText = localeApi.getMessage(player, isActive ? "gui_effects_selected" : "gui_effects_click_select");

            String loreStr = localeApi.getMessage(player, "gui_effects_lore",
                    "{particle}", particleStr,
                    "{count}", countStr,
                    "{speed}", speedStr,
                    "{sound}", soundStr,
                    "{volume}", volumeStr,
                    "{pitch}", pitchStr,
                    "{interval}", intervalStr,
                    "{status_text}", statusText
            );

            if (loreStr != null) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreStr.split("\n")) {
                    lore.add(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(line));
                }
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createLocalizedItem(Player player, Material material, String nameKey, String loreKey, Object... replacements) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String nameStr = localeApi.getMessage(player, nameKey, replacements);
            if (nameStr != null) {
                meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(nameStr));
            }
            if (loreKey != null) {
                String loreStr = localeApi.getMessage(player, loreKey, replacements);
                if (loreStr != null) {
                    List<Component> lore = new ArrayList<>();
                    for (String line : loreStr.split("\n")) {
                        lore.add(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(line));
                    }
                    meta.lore(lore);
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatLocation(org.bukkit.Location loc) {
        if (loc == null) return "External Server";
        return String.format("%s, %d, %d, %d", loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        GUISession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0) return;

        if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
            if (session.viewState == 1) {
                handleEditorClick(player, session, slot, event.isRightClick());
            } else if (session.viewState == 2) {
                handleEffectsClick(player, session, slot);
            } else {
                handleListClick(player, session, slot);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        GUISession session = activeSessions.get(player.getUniqueId());
        if (session != null && event.getInventory().equals(session.inventory)) {
            activeSessions.remove(player.getUniqueId());
        }
    }

    private void handleListClick(Player player, GUISession session, int slot) {
        if (slot == 45 && session.page > 0) {
            openListPage(player, session.page - 1);
            return;
        }
        if (slot == 53) {
            openListPage(player, session.page + 1);
            return;
        }
        if (slot < 45) {
            int idx = session.page * 45 + slot;
            if (idx >= session.portalsCache.size()) return;
            openEditor(player, session.portalsCache.get(idx));
        }
    }

    private void handleEditorClick(Player player, GUISession session, int slot, boolean isRight) {
        IPortal portal = session.targetPortal;
        if (portal == null) return;

        switch (slot) {
            case 10: // Teleport
                player.closeInventory();
                player.teleportAsync(portal.getOriginPos().getLocation());
                break;
            case 11: // Toggle items
                portal.setAllowsNonPlayerTeleportation(!portal.allowsNonPlayerTeleportation());
                openEditor(player, portal);
                break;
            case 12: // Decrease price
                double newPriceDec = Math.max(0.0, portal.getPrice() - (isRight ? 10.0 : 1.0));
                portal.setPrice(newPriceDec);
                openEditor(player, portal);
                break;
            case 13: // Reset price
                portal.setPrice(0.0);
                openEditor(player, portal);
                break;
            case 14: // Increase price
                double newPriceInc = portal.getPrice() + (isRight ? 10.0 : 1.0);
                portal.setPrice(newPriceInc);
                openEditor(player, portal);
                break;
            case 15: // Open Effects selector menu
                openEffectsMenu(player, portal);
                break;
            case 16: // Toggle sound
                portal.setSoundEnabled(!portal.isSoundEnabled());
                openEditor(player, portal);
                break;
            case 17: // Delete
                portalManager.removePortal(portal);
                openListPage(player, session.page);
                break;
            case 22: // Back
                openListPage(player, session.page);
                break;
        }
    }

    private void handleEffectsClick(Player player, GUISession session, int slot) {
        IPortal portal = session.targetPortal;
        if (portal == null) return;

        if (slot == 22) { // Back to editor
            openEditor(player, portal);
            return;
        }

        if (slot >= 10 && slot < 17) {
            List<String> presetNames = new ArrayList<>(effectsTask.getPresetNames());
            int idx = slot - 10;
            if (idx >= 0 && idx < presetNames.size()) {
                String selectedPreset = presetNames.get(idx);
                portal.setEffectPreset(selectedPreset);
                openEffectsMenu(player, portal);
            }
        }
    }
}
