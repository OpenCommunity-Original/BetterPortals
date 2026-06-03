package org.envel.betterportals.bukkit.net;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import org.envel.betterportals.shared.net.IRequestHandler;
import org.envel.betterportals.shared.net.encryption.EncryptedObjectStream;
import org.envel.betterportals.shared.net.encryption.EncryptedObjectStreamFactory;
import org.envel.betterportals.shared.net.encryption.IEncryptedObjectStream;

public class NetworkModule extends AbstractModule {
    @Override
    public void configure() {
        install(new FactoryModuleBuilder()
                .implement(IEncryptedObjectStream.class, EncryptedObjectStream.class)
                .build(EncryptedObjectStreamFactory.class)
        );

        bind(IPortalClient.class).to(PortalClient.class);
        bind(IRequestHandler.class).to(ClientRequestHandler.class);
        bind(IClientReconnectHandler.class).to(ClientReconnectHandler.class);
    }
}
