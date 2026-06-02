package org.envel.betterportals.bukkit.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.envel.betterportals.bukkit.BetterPortals;
import org.envel.betterportals.bukkit.command.framework.CommandException;
import org.envel.betterportals.bukkit.command.framework.CommandTree;
import org.envel.betterportals.bukkit.command.framework.annotations.Command;
import org.envel.betterportals.bukkit.command.framework.annotations.Description;
import org.envel.betterportals.bukkit.command.framework.annotations.Path;
import org.envel.betterportals.bukkit.command.framework.annotations.RequiresPermissions;
import org.envel.betterportals.bukkit.config.MessageConfig;
import org.envel.betterportals.bukkit.config.ProxyConfig;
import org.envel.betterportals.bukkit.net.IClientReconnectHandler;
import org.envel.betterportals.bukkit.net.IPortalClient;
import org.envel.betterportals.bukkit.util.performance.OperationTimer;
import org.envel.betterportals.shared.logging.Logger;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;

@Singleton
public class MainCommands {
    private final BetterPortals pl;
    private final Logger logger;
    private final MessageConfig messageConfig;
    private final IPortalClient portalClient;
    private final ProxyConfig proxyConfig;
    private final IClientReconnectHandler reconnectHandler;

    @Inject
    public MainCommands(BetterPortals pl, Logger logger, MessageConfig messageConfig, CommandTree commandTree, IPortalClient portalClient, ProxyConfig proxyConfig, IClientReconnectHandler reconnectHandler) {
        this.pl = pl;
        this.logger = logger;
        this.messageConfig = messageConfig;
        this.portalClient = portalClient;
        this.proxyConfig = proxyConfig;
        this.reconnectHandler = reconnectHandler;

        commandTree.registerCommands(this);
        commandTree.addAlias("betterportals", "bp");
    }

    @Command
    @Path("betterportals/reload")
    @Description("Reloads the plugin and the config file")
    @RequiresPermissions("betterportals.reload")
    public boolean reload(CommandSender sender) {
        PluginManager pluginManager = pl.getServer().getPluginManager();

        // Reload the config file, then disable/enable the plugin which will re-inject everything
        OperationTimer timer = new OperationTimer();
        pl.softReload();

        sender.sendMessage(String.format("%s (%.03fms)", messageConfig.getChatMessage("reload"), timer.getTimeTakenMillis()));
        return true;
    }

    @Command
    @Path("betterportals/reconnect")
    @Description("Reconnects to the proxy if disconnect")
    @RequiresPermissions("betterportals.reconnect")
    public boolean reconnect(CommandSender sender) throws CommandException  {
        if(!proxyConfig.isEnabled()) {
            throw new CommandException(messageConfig.getErrorMessage("proxyDisabled"));
        }

        if(portalClient.isConnectionOpen()) {
            throw new CommandException(messageConfig.getErrorMessage("alreadyConnected"));
        }

        sender.sendMessage(messageConfig.getChatMessage("startedReconnection"));
        reconnectHandler.prematureReconnect();
        return true;
    }
}
