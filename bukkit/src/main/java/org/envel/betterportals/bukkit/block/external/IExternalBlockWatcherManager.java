package org.envel.betterportals.bukkit.block.external;

import org.envel.betterportals.bukkit.net.requests.GetBlockDataChangesRequest;
import org.envel.betterportals.shared.net.Response;

import java.util.function.Consumer;

public interface IExternalBlockWatcherManager {
    /**
     * The time of no requests for a particular data array before it gets cleared.
     */
    double CLEAR_TIME = 10;

    /**
     * Called whenever a request to fetch the block data changes is received (on the main thread) from an external server.
     * @param request The change request
     * @param onFinish Given the response when responding is complete
     */
    void onRequestReceived(GetBlockDataChangesRequest request, Consumer<Response> onFinish);

    /**
     * Removes any external change watchers that are unused.
     */
    void update();

    /**
     * Clears all active watchers and their cached block data.
     */
    void clear();
}
