package org.envel.betterportals.bukkit.command.framework;

import org.envel.betterportals.bukkit.config.MessageConfig;
import org.envel.betterportals.bukkit.util.ArrayUtil;
import org.envel.betterportals.shared.logging.Logger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.*;

public class ParentCommand implements ICommand  {
    private final Logger logger;
    private final MessageConfig messageConfig;
    private final io.foxserver.common.locale.LocaleAPI localeApi;
    private final Map<String, ICommand> subCommands = new HashMap<>();
    // Aliases are also stored in the command array, but they're put here so we can identify them as aliases
    private final Set<String> aliases = new HashSet<>();
    // If this is true, the command will just exit silently if the subcommand does not exist instead of printing a help screen
    private final boolean isRoot;

    ParentCommand(Logger logger, MessageConfig messageConfig, io.foxserver.common.locale.LocaleAPI localeApi, boolean isRoot) {
        this.logger = logger;
        this.messageConfig = messageConfig;
        this.localeApi = localeApi;
        this.isRoot = isRoot;
    }

    ParentCommand(Logger logger, MessageConfig messageConfig, io.foxserver.common.locale.LocaleAPI localeApi) {
        this(logger, messageConfig, localeApi, false);
    }

    @Override
    public boolean execute(CommandSender sender, String pathToCall, String[] args) throws CommandException {
        // If the user didn't specify a subcommand, or they asked for help, or the subcommand was invalid, display the help screen
        if(args.length == 0 || args[0].equalsIgnoreCase("help") ||!subCommands.containsKey(args[0].toLowerCase())) {
            if(!isRoot) {
                int page = 1;
                if(args.length > 0 && args[0].equalsIgnoreCase("help") && args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {}
                }
                displayHelp(sender, pathToCall, page);
            }
            return false;
        }   else    {
            String subCommandName = args[0].toLowerCase();
            // Add the sub command on to the path to call
            String newPathToCall = String.format("%s%s ", pathToCall, subCommandName);
            // Execute the sub command, making sure to
            ICommand subCommand = subCommands.get(subCommandName);
            return subCommand.execute(sender, newPathToCall, ArrayUtil.removeFirstElement(args));
        }
    }

    /**
     * Finds the sub-commands that <code>sender</code> has permission to use.
     * @param sender The user typing in the command or going to help
     * @return The commands that they have permission to use
     */
    private Map<String, ICommand> filterSubCommands(CommandSender sender) {
        Map<String, ICommand> result = new HashMap<>();
        subCommands.forEach((name, command) -> {
            if(command instanceof ParentCommand) {
                result.put(name, command);
            }   else    {
                SubCommand subCommand = (SubCommand) command;
                if(subCommand.hasPermissions(sender)) {
                    result.put(name, command);
                }
            }
        });

        return result;
    }

    List<String> tabComplete(CommandSender sender, String[] args) {
        // If we're at the end of the chain, return a list of our sub commands
        if(args.length == 0) {
            return new ArrayList<>(filterSubCommands(sender).keySet());
        }

        String lastArg = args[0];

        ICommand validEnteredCommand = subCommands.get(lastArg);
        if(validEnteredCommand instanceof ParentCommand) {
            return ((ParentCommand) validEnteredCommand).tabComplete(sender, ArrayUtil.removeFirstElement(args));
        }   else if(validEnteredCommand == null)    {
            // Find the commands that start with the currently entered word
            List<String> result = new ArrayList<>();
            for(String command : filterSubCommands(sender).keySet()) {
                if(command.startsWith(lastArg)) {
                    result.add(command);
                }
            }

            return result;
        }   else    {
            return new ArrayList<>();
        }
    }

    private void displayHelp(CommandSender sender, String pathToCall, int page) {
        Map<String, ICommand> filtered = filterSubCommands(sender);
        if(filtered.isEmpty()) {
            sender.sendMessage(messageConfig.getChatMessage("noCommands"));
            return;
        }

        List<HelpEntry> entries = new ArrayList<>();
        filtered.forEach((name, subCommand) -> {
            if(aliases.contains(name)) { return; }
            entries.add(new HelpEntry(name, subCommand));
        });

        entries.sort(Comparator.comparing(HelpEntry::getName));

        int itemsPerPage = 6;
        int totalPages = (int) Math.ceil((double) entries.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1;
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, entries.size());

        MiniMessage mm = MiniMessage.miniMessage();
        Player player = (sender instanceof Player) ? (Player) sender : null;

        String headerStr = localeApi.getRaw(player, "help_header");
        if (headerStr == null) headerStr = "<gold><bold>BetterPortals Help</bold></gold> <gray>(Page {page}/{total_pages})</gray>";
        headerStr = headerStr.replace("{page}", String.valueOf(page)).replace("{total_pages}", String.valueOf(totalPages));
        
        sender.sendMessage(mm.deserialize(headerStr));
        sender.sendMessage(mm.deserialize("<yellow>──────────────────────────────────────────────────</yellow>"));

        for (int i = startIndex; i < endIndex; i++) {
            HelpEntry entry = entries.get(i);
            String name = entry.getName();
            ICommand subCommand = entry.getSubCommand();

            String path = pathToCall.trim();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            String cleanCommand = "/" + path + " " + name;
            String label = "<gold>• <yellow>/" + path + " <white>" + name + "</white>";
            String desc = "";
            String argsUsage = "";

            if (subCommand instanceof SubCommand) {
                SubCommand sc = (SubCommand) subCommand;
                argsUsage = sc.getArgumentsUsage();
                desc = sc.getDescription();
            }

            // Fetch translations
            String pathForTranslation = pathToCall.trim();
            if (pathForTranslation.startsWith("/")) {
                pathForTranslation = pathForTranslation.substring(1);
            }
            if (pathForTranslation.startsWith("bp")) {
                pathForTranslation = "betterportals" + pathForTranslation.substring(2);
            }
            String keyName = (pathForTranslation + "_" + name).replace(" ", "_").replace("/", "_").toLowerCase();
            String localizedDesc = localeApi.getRaw(player, "commands." + keyName + ".description");
            if (localizedDesc == null) {
                String camelKeyName = keyName;
                if (keyName.equals("betterportals_linkportals")) {
                    camelKeyName = "betterportals_linkPortals";
                } else if (keyName.equals("betterportals_linkexternalportals")) {
                    camelKeyName = "betterportals_linkExternalPortals";
                } else if (keyName.equals("betterportals_getallownonplayerteleportation")) {
                    camelKeyName = "betterportals_getallowNonPlayerTeleportation";
                } else if (keyName.equals("betterportals_setallownonplayerteleportation")) {
                    camelKeyName = "betterportals_setAllowNonPlayerTeleportation";
                } else if (keyName.equals("betterportals_setportalname")) {
                    camelKeyName = "betterportals_setPortalName";
                } else if (keyName.equals("betterportals_setorigin")) {
                    camelKeyName = "betterportals_setOrigin";
                } else if (keyName.equals("betterportals_setdestination")) {
                    camelKeyName = "betterportals_setDestination";
                }
                localizedDesc = localeApi.getRaw(player, "commands." + camelKeyName + ".description");
            }
            if (localizedDesc != null) {
                desc = localizedDesc;
            }

            if (!argsUsage.isEmpty()) {
                label += " <gray>" + argsUsage + "</gray>";
            }
            label += "</yellow>";

            if (!desc.isEmpty()) {
                label += " <dark_gray>-</dark_gray> <gray>" + desc + "</gray>";
            }

            String autofillStr = localeApi.getRaw(player, "autofill_suggest");
            if (autofillStr == null) autofillStr = "Click to autofill command:";
            String hoverText = "<yellow>" + autofillStr + "<br><white>" + cleanCommand + (argsUsage.isEmpty() ? "" : " " + argsUsage) + "</white>";
            if (!desc.isEmpty()) {
                hoverText += "<br><br><gray>" + desc + "</gray>";
            }

            String miniMessageString = "<click:suggest_command:'" + cleanCommand + " '><hover:show_text:'" + hoverText + "'>" + label + "</hover></click>";
            sender.sendMessage(mm.deserialize(miniMessageString));
        }

        sender.sendMessage(mm.deserialize("<yellow>──────────────────────────────────────────────────</yellow>"));

        if (totalPages > 1) {
            String footer = "";
            String prevText = localeApi.getRaw(player, "help_previous");
            if (prevText == null) prevText = "[◀ Previous]";
            String nextText = localeApi.getRaw(player, "help_next");
            if (nextText == null) nextText = "[Next ▶]";
            String goToPageText = localeApi.getRaw(player, "help_go_to_page");
            if (goToPageText == null) goToPageText = "<green>Go to page {page}";
            
            if (page > 1) {
                String hoverPrev = goToPageText.replace("{page}", String.valueOf(page - 1));
                footer += "<click:run_command:'/bp help " + (page - 1) + "'><hover:show_text:'" + hoverPrev + "'><gold><b>" + prevText + "</b></gold></hover></click>";
            } else {
                footer += "<dark_gray>" + prevText + "</dark_gray>";
            }

            String pageIndicator = localeApi.getRaw(player, "help_page_indicator");
            if (pageIndicator == null) pageIndicator = "<yellow>Page {page} of {total_pages}</yellow>";
            pageIndicator = pageIndicator.replace("{page}", String.valueOf(page)).replace("{total_pages}", String.valueOf(totalPages));
            
            footer += "  " + pageIndicator + "  ";

            if (page < totalPages) {
                String hoverNext = goToPageText.replace("{page}", String.valueOf(page + 1));
                footer += "<click:run_command:'/bp help " + (page + 1) + "'><hover:show_text:'" + hoverNext + "'><gold><b>" + nextText + "</b></gold></hover></click>";
            } else {
                footer += "<dark_gray>" + nextText + "</dark_gray>";
            }
            sender.sendMessage(mm.deserialize("<gray>" + footer + "</gray>"));
        } else {
            String clickInfo = localeApi.getRaw(player, "help_click_info");
            if (clickInfo == null) clickInfo = "<gray>💡 Click on any command to copy it to your chat box.</gray>";
            sender.sendMessage(mm.deserialize(clickInfo));
        }
    }

    private static class HelpEntry {
        private final String name;
        private final ICommand subCommand;

        public HelpEntry(String name, ICommand subCommand) {
            this.name = name;
            this.subCommand = subCommand;
        }

        public String getName() { return name; }
        public ICommand getSubCommand() { return subCommand; }
    }

    void addCommandAlias(String[] remainingElements, String aliasName) {
        String originalName = remainingElements[0];
        if(remainingElements.length > 1) {
            // Go down to the next command if there is more left in the path
            ICommand nextCommand = subCommands.get(originalName);
            if(!(nextCommand instanceof ParentCommand)) {
                throw new IllegalArgumentException("Invalid original name for alias");
            }
            ((ParentCommand) nextCommand).addCommandAlias(ArrayUtil.removeFirstElement(remainingElements), aliasName);
        }   else    {
            ICommand toBeAliased = subCommands.get(originalName);
            if(toBeAliased == null) {throw new IllegalArgumentException("Invalid original name for alias");}

            if(subCommands.containsKey(aliasName)) {
                logger.warning("Override existing command with alias");
            }
            subCommands.put(aliasName, toBeAliased);
            aliases.add(aliasName); // Also add it to this set so that we know it's an alias
        }
    }

    // Recursively adds more subcommands until reaching the specified command
    void recursivelyAdd(String[] remainingElements, SubCommand command) {
        String currentName = remainingElements[0];
        if(remainingElements.length == 1) {
            if(subCommands.containsKey(currentName)) {
                logger.warning("Overriding previously existing command");
            }
            subCommands.put(currentName, command);
        }   else    {
            // Add a new subparent command
            if(!subCommands.containsKey(currentName)) {
                subCommands.put(currentName, new ParentCommand(logger, messageConfig, localeApi));
            }

            // Add to the next parent command down if we haven't reached the bottom yet
            ParentCommand nextInLine = (ParentCommand) subCommands.get(currentName);
            nextInLine.recursivelyAdd(ArrayUtil.removeFirstElement(remainingElements), command);
        }
    }
}
