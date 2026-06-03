package org.envel.betterportals.velocity;

import org.envel.betterportals.proxy.IProxy;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class VelocityProxy implements IProxy {
    private final ProxyServer proxyServer;
    @Getter private final String pluginVersion;

    @Inject
    public VelocityProxy(ProxyServer proxyServer, @Named("pluginVersion") String pluginVersion) {
        this.proxyServer = proxyServer;
        this.pluginVersion = pluginVersion;
    }

    @Deprecated
    @Override
    public @Nullable String findServer(InetSocketAddress clientAddress) {
        for(RegisteredServer server : proxyServer.getAllServers()) {
            InetSocketAddress serverAddress = server.getServerInfo().getAddress();
            if(serverAddress.equals(clientAddress)) {
                return server.getServerInfo().getName();
            }
        }

        return null;
    }

    @Override
    public boolean serverExists(String serverName) {
        return proxyServer.getServer(serverName).isPresent();
    }

    @Override
    public boolean playerExists(UUID uid) {
        return proxyServer.getPlayer(uid).isPresent();
    }

    @Override
    public void changePlayerServer(UUID uid, String destinationServer) {
        Optional<Player> player = proxyServer.getPlayer(uid);
        if(player.isEmpty()) {
            throw new IllegalArgumentException(String.format("No player existed with UUID %s", uid));
        }

        Optional<RegisteredServer> server = proxyServer.getServer(destinationServer);
        if(server.isEmpty()) {
            throw new IllegalArgumentException(String.format("No server existed with the UUID %s", destinationServer));
        }

        player.get().createConnectionRequest(server.get()).fireAndForget();
    }
}
