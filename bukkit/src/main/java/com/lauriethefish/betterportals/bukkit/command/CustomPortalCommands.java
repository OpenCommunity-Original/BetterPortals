package com.lauriethefish.betterportals.bukkit.command;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.lauriethefish.betterportals.bukkit.command.framework.CommandException;
import com.lauriethefish.betterportals.bukkit.command.framework.CommandTree;
import com.lauriethefish.betterportals.bukkit.command.framework.annotations.*;
import com.lauriethefish.betterportals.bukkit.config.MessageConfig;
import com.lauriethefish.betterportals.bukkit.player.IPlayerData;
import com.lauriethefish.betterportals.bukkit.portal.IPortal;
import com.lauriethefish.betterportals.bukkit.portal.IPortalManager;
import com.lauriethefish.betterportals.bukkit.portal.selection.IPortalSelection;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Singleton
public class CustomPortalCommands {
    private static final String[] EASTER_EGG_NAMES = new String[]{"dinnerbone"};
    private static final double MODIFY_DISTANCE = 20.0;

    private final IPortalManager portalManager;
    private final MessageConfig messageConfig;
    private final IPortal.Factory portalFactory;
    private final Provider<IPortalSelection> selectionProvider;
    private final com.lauriethefish.betterportals.bukkit.gui.PortalAdminGUI adminGUI;

    @Inject
    public CustomPortalCommands(CommandTree commandTree, IPortalManager portalManager, MessageConfig messageConfig, IPortal.Factory portalFactory, Provider<IPortalSelection> selectionProvider, com.lauriethefish.betterportals.bukkit.gui.PortalAdminGUI adminGUI) {
        this.portalManager = portalManager;
        this.messageConfig = messageConfig;
        this.portalFactory = portalFactory;
        this.selectionProvider = selectionProvider;
        this.adminGUI = adminGUI;

        commandTree.registerCommands(this);
    }

    private @NotNull IPortal getClosestPortal(Player player) throws CommandException {
        IPortal portal = portalManager.findClosestPortal(player.getLocation(), MODIFY_DISTANCE);
        if(portal == null) {
            throw new CommandException(messageConfig.getErrorMessage("noPortalCloseEnough"));
        }

        return portal;
    }

    @Command
    @Path("betterportals/removebyname")
    @RequiresPermissions("betterportals.remove")
    @Description("Removes all portals with the given name")
    @Argument(name = "portalName")
    @Aliases("deletename")
    public boolean removePortalsByName(CommandSender sender, String portalName) throws CommandException {
        List<IPortal> toRemove = portalManager
                .getAllPortals()
                .stream()
                .filter(portal -> portalName.equals(portal.getName()))
                .collect(Collectors.toList());


        if(toRemove.size() == 0) {
            throw new CommandException(messageConfig.getErrorMessage("noPortalsWithName").replace("{name}", portalName));
        }   else    {
            toRemove.forEach(portalManager::removePortal);
            sender.sendMessage(messageConfig.getChatMessage("portalsRemoved"));
            return true;
        }
    }

    @Command
    @Path("betterportals/createfromcoords")
    @RequiresPermissions("betterportals.createfromcoords")
    @Description("Creates a portal from the coordinates of its corners without requiring a player")
    @Argument(name = "originWorld")
    @Argument(name = "originCorner1")
    @Argument(name = "originCorner2")
    @Argument(name = "destWorld")
    @Argument(name = "destCorner1")
    @Argument(name = "destCorner2")
    @Argument(name = "twoWay?", defaultValue = "false")
    @Argument(name = "invert?", defaultValue = "false")
    @Argument(name = "name", defaultValue = " no name")
    public boolean createFromCoordinates(CommandSender sender,
                                         World originWorld,
                                         Vector originCorner1,
                                         Vector originCorner2,
                                         World destWorld,
                                         Vector destCorner1,
                                         Vector destCorner2,
                                         String twoWayStr,
                                         String invertStr,
                                         String name
    ) throws CommandException {
        if(" no name".equals(name)) {
            name = null;
        }

        boolean twoWay = twoWayStr.equalsIgnoreCase("true") || twoWayStr.equalsIgnoreCase("twoWay") || twoWayStr.equalsIgnoreCase("dual");
        boolean invert = invertStr.equalsIgnoreCase("true") || invertStr.equalsIgnoreCase("invert");

        IPortalSelection origin = makeSelection(originWorld, originCorner1, originCorner2);
        IPortalSelection dest = makeSelection(destWorld, destCorner1, destCorner2);

        if(!origin.getPortalSize().equals(dest.getPortalSize())) {
            throw new CommandException(messageConfig.getErrorMessage("differentSizes"));
        }

        if(invert) {
            dest.invertDirection();
        }

        IPortal portal = portalFactory.create(origin.getPortalPosition(), dest.getPortalPosition(),
                origin.getPortalSize(), true, UUID.randomUUID(), null, name, true);
        portalManager.registerPortal(portal);

        // Add another portal going back if we're in two way mode
        if(twoWay) {
            IPortal reversePortal = portalFactory.create(dest.getPortalPosition(), origin.getPortalPosition(),
                    origin.getPortalSize(), true, UUID.randomUUID(), null, name, true);
            portalManager.registerPortal(reversePortal);
        }
        sender.sendMessage(messageConfig.getChatMessage("portalCreated"));

        return true;
    }

    private IPortalSelection makeSelection(World world, Vector corner1, Vector corner2) throws CommandException {
        IPortalSelection selection = selectionProvider.get();
        selection.setPositionA(corner1.toLocation(world));
        selection.setPositionB(corner2.toLocation(world));

        if(!selection.isValid()) {
            throw new CommandException(String.format(messageConfig.getErrorMessage("coordinatesNotInLine"),
                    corner1.getBlockX(), corner1.getBlockY(), corner1.getBlockZ(),
                    corner2.getBlockX(), corner2.getBlockY(), corner2.getBlockZ()));
        }

        return selection;
    }

    @Command
    @Path("betterportals/remove")
    @RequiresPermissions("betterportals.remove")
    @RequiresPlayer
    @Aliases({"delete", "del"})
    @Description("Removes the nearest portal within 20 blocks of the player")
    @Argument(name = "removeDestination?", defaultValue = "true")
    public boolean deleteNearest(Player player, boolean removeDestination) throws CommandException {
        IPortal portal = getClosestPortal(player);

        // If the player doesn't own the portal, and doesn't have permission to remove portals that aren't theirs, don't remove
        if(!player.hasPermission("betterportals.remove.others") && !player.getUniqueId().equals(portal.getOwnerId())) {
            throw new CommandException(messageConfig.getErrorMessage("removeNotOwnedByPlayer"));
        }

        portalManager.removePortal(portal);
        // We can't remove the destination on cross-server portals
        if(removeDestination && !portal.isCrossServer()) {
            Location destPosition = portal.getDestPos().getLocation();
            portalManager.removePortalsAt(destPosition);
        }

        player.sendMessage(messageConfig.getChatMessage("portalRemoved"));
        return true;
    }

    @Command
    @Path("betterportals/setOrigin")
    @Aliases("origin")
    @RequiresPermissions("betterportals.select")
    @RequiresPlayer
    @Description("Sets the current portal wand selection as your origin position")
    public boolean setOrigin(IPlayerData playerData) throws CommandException    {
        playerData.getSelection().trySelectOrigin();
        playerData.getPlayer().sendMessage(messageConfig.getChatMessage("originPortalSet"));
        return true;
    }

    @Command
    @Path("betterportals/setDestination")
    @Aliases({"destination", "dest"})
    @RequiresPermissions("betterportals.select")
    @RequiresPlayer
    @Description("Sets the current portal wand selection as your destination position")
    public boolean setDestination(IPlayerData playerData) throws CommandException    {
        playerData.getSelection().trySelectDestination();
        playerData.getPlayer().sendMessage(messageConfig.getChatMessage("destPortalSet"));
        return true;
    }

    @Command
    @Path("betterportals/linkPortals")
    @Aliases("link")
    @RequiresPermissions("betterportals.link")
    @RequiresPlayer
    @Description("Links the origin and destination portal together")
    @Argument(name = "twoWay?", defaultValue = "false")
    @Argument(name = "invert?", defaultValue = "false")
    public boolean linkPortals(IPlayerData playerData, String twoWayStr, String invertStr) throws CommandException  {
        boolean twoWay = twoWayStr.equalsIgnoreCase("true") || twoWayStr.equalsIgnoreCase("twoWay") || twoWayStr.equalsIgnoreCase("dual");
        boolean invert = invertStr.equalsIgnoreCase("true") || invertStr.equalsIgnoreCase("invert");

        playerData.getSelection().tryCreateFromSelection(playerData.getPlayer(), twoWay, invert);
        playerData.getPlayer().sendMessage(messageConfig.getChatMessage("portalsLinked"));
        return true;
    }

    @Command
    @Path("betterportals/linkExternalPortals")
    @Aliases("linkexternal")
    @RequiresPermissions("betterportals.linkexternal")
    @RequiresPlayer
    @Description("Links the origin selection on this server with a destination on another server")
    @Argument(name = "invert?", defaultValue = "false")
    public boolean linkExternalPortals(IPlayerData playerData, boolean invert) throws CommandException  {
        playerData.getSelection().tryCreateFromExternalSelection(playerData.getPlayer(), invert);
        playerData.getPlayer().sendMessage(messageConfig.getChatMessage("portalsLinked"));
        return true;
    }

    @Command
    @Path("betterportals/wand")
    @RequiresPermissions("betterportals.wand")
    @RequiresPlayer
    @Description("Gives you the wand for selecting portals")
    public boolean getPortalWand(Player player) {
        player.getInventory().addItem(messageConfig.getPortalWand());
        return true;
    }

    // Some of the easter eggs require a portal recreation to work properly, this handles that
    private void setName(IPortal portal, String name) {
        boolean isEgg = false;
        for(String egg : EASTER_EGG_NAMES) {
            if (egg.equalsIgnoreCase(name) || egg.equalsIgnoreCase(portal.getName())) {
                isEgg = true;
                break;
            }
        }
        // Non-easter-egg portals can just get their name set normally
        if(!isEgg) {
            portal.setName(name);
            return;
        }

        portalManager.removePortal(portal);
        IPortal replacement = portalFactory.create(
                portal.getOriginPos(),
                portal.getDestPos(),
                portal.getSize(),
                portal.isCustom(),
                portal.getId(),
                portal.getOwnerId(),
                name,
                true
        );

        portalManager.registerPortal(replacement);
    }

    @Command
    @Path("betterportals/setPortalName")
    @RequiresPermissions("betterportals.setname")
    @Argument(name = "newName")
    @Aliases("setname")
    @RequiresPlayer
    @Description("Sets the name of the nearest portal within 20 blocks")
    public boolean setName(Player player, String newName) throws CommandException   {
        IPortal portal = getClosestPortal(player);

        // If the player doesn't own the portal, and doesn't have permission to remove portals that aren't theirs, don't remove
        if(!player.hasPermission("betterportals.setname.others") && !player.getUniqueId().equals(portal.getOwnerId())) {
            throw new CommandException(messageConfig.getErrorMessage("nameNotOwnedbyPlayer"));
        }

        // Nether portals cannot be named!
        if(portal.isNetherPortal()) {
            throw new CommandException(messageConfig.getErrorMessage("nameNetherPortal"));
        }

        setName(portal, newName);
        player.sendMessage(messageConfig.getChatMessage("changedName"));
        return true;
    }

    @Command
    @Path("betterportals/getportalname")
    @RequiresPermissions("betterportals.getname")
    @RequiresPlayer
    @Aliases("getname")
    @Description("Tells you the name of the nearest portal within 20 blocks")
    public boolean getName(Player player) throws CommandException {
        IPortal portal = getClosestPortal(player);

        String name = portal.getName();
        if(name == null) {
            throw new CommandException(messageConfig.getErrorMessage("noName"));
        }

        String nameFormat = messageConfig.getChatMessage("currentName");
        nameFormat = nameFormat.replace("{name}", portal.getName());
        player.sendMessage(nameFormat);
        return true;
    }

    @Command
    @Path("betterportals/getallowNonPlayerTeleportation")
    @RequiresPermissions("betterportals.getallowNonPlayerTeleportation")
    @RequiresPlayer
    @Aliases("getcanteleportmobs")
    @Description("Tells you whether or not the nearest portal within 20 blocks allows item teleportation")
    public boolean getAllowNonPlayerTeleportation(Player player) throws CommandException {
        IPortal portal = getClosestPortal(player);

        if(portal.allowsNonPlayerTeleportation()) {
            player.sendMessage(messageConfig.getChatMessage("allowsItems"));
        }   else    {
            player.sendMessage(messageConfig.getChatMessage("doesNotAllowItems"));
        }

        return true;
    }

    @Command
    @Path("betterportals/setAllowNonPlayerTeleportation")
    @RequiresPermissions("betterportals.setAllowNonPlayerTeleportation")
    @RequiresPlayer
    @Aliases("setcanteleportmobs")
    @Description("Sets whether or not the nearest portal within 20 blocks allows item teleportation")
    @Argument(name = "allow")
    public boolean setAllowNonPlayerTeleportation(Player player, boolean allowTeleportation) throws CommandException {
        IPortal portal = getClosestPortal(player);

        portal.setAllowsNonPlayerTeleportation(allowTeleportation);
        if(allowTeleportation) {
            player.sendMessage(messageConfig.getChatMessage("changedAllowsItems"));
        }   else    {
            player.sendMessage(messageConfig.getChatMessage("changedDoesNotAllowItems"));
        }

        return true;
    }

    @Command
    @Path("betterportals/setseethroughportal")
    @RequiresPermissions("betterportals.see")
    @RequiresPlayer
    @Aliases("setenablebpview")
    @Description("Sets whether or not the current player is able to see what's on the other side of a portal.")
    @Argument(name = "seethroughportal")
    public boolean setSeeThroughPortal(IPlayerData playerData, boolean seeThroughPortal) {
        Player player = playerData.getPlayer();
        if (seeThroughPortal) {
            playerData.getPermanentData().set("seeThroughPortal", true);
            playerData.savePermanentData();
            player.sendMessage(messageConfig.getChatMessage("seeThroughPortalEnabled"));
        }

        else {
            playerData.getPermanentData().set("seeThroughPortal", false);
            playerData.savePermanentData();
            player.sendMessage(messageConfig.getChatMessage("seeThroughPortalDisabled"));
        }

        return true;
    }

    @Command
    @Path("betterportals/toggleseethroughportal")
    @RequiresPermissions("BetterPortals.see")
    @RequiresPlayer
    @Aliases("togglevanillaview")
    @Description("Toggles whether or not the current player is able to see what's on the other side of a portal.")
    public boolean toggleSeeThroughPortal(IPlayerData playerData) {
        setSeeThroughPortal(playerData, !playerData.getPermanentData().getBoolean("seeThroughPortal"));

        return true;
    }

    @Command
    @Path("betterportals/menu")
    @Aliases({"list", "admin", "gui"})
    @RequiresPermissions("betterportals.select")
    @RequiresPlayer
    @Description("Opens the Portal Admin GUI menu")
    public boolean openAdminMenu(Player player) {
        adminGUI.open(player);
        return true;
    }

    @Command
    @Path("betterportals/setprice")
    @RequiresPermissions("betterportals.setname")
    @RequiresPlayer
    @Argument(name = "price")
    @Description("Sets the price of the closest portal")
    public boolean setPortalPrice(Player player, double price) throws CommandException {
        IPortal portal = getClosestPortal(player);
        if(portal.isNetherPortal()) {
            throw new CommandException(messageConfig.getErrorMessage("nameNetherPortal"));
        }
        portal.setPrice(price);
        player.sendMessage(org.bukkit.ChatColor.GREEN + "Set closest portal's price to " + price);
        return true;
    }

    @Command
    @Path("betterportals/setpreset")
    @RequiresPermissions("betterportals.setname")
    @RequiresPlayer
    @Argument(name = "preset")
    @Description("Sets the effect preset of the closest portal")
    public boolean setPortalPreset(Player player, String preset) throws CommandException {
        IPortal portal = getClosestPortal(player);
        if(portal.isNetherPortal()) {
            throw new CommandException(messageConfig.getErrorMessage("nameNetherPortal"));
        }
        portal.setEffectPreset(preset);
        player.sendMessage(org.bukkit.ChatColor.GREEN + "Set closest portal's effect preset to " + preset);
        return true;
    }
}
