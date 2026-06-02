package org.envel.betterportals.bukkit.block.fetch;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.envel.betterportals.bukkit.config.RenderConfig;
import org.envel.betterportals.bukkit.net.IPortalClient;
import org.envel.betterportals.bukkit.portal.IPortal;
import org.envel.betterportals.shared.logging.Logger;

@Singleton
public class BlockDataFetcherFactory {
    private final Logger logger;
    private final IPortalClient portalClient;
    private final RenderConfig renderConfig;

    @Inject
    public BlockDataFetcherFactory(Logger logger, IPortalClient portalClient, RenderConfig renderConfig) {
        this.logger = logger;
        this.portalClient = portalClient;
        this.renderConfig = renderConfig;
    }

    public IBlockDataFetcher create(IPortal portal) {
        if(portal.isCrossServer()) {
            return new ExternalBlockDataFetcher(logger, portalClient, renderConfig, portal);
        }   else    {
            return new LocalBlockDataFetcher(portal, renderConfig);
        }
    }
}
