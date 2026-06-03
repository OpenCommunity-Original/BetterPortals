package org.envel.betterportals.bukkit.block.external;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.envel.betterportals.api.IntVector;
import org.envel.betterportals.bukkit.net.requests.GetBlockDataChangesRequest;
import org.envel.betterportals.shared.logging.Logger;
import org.envel.betterportals.shared.net.Response;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Singleton
public class ExternalBlockWatcherManager implements IExternalBlockWatcherManager    {
    /**
     * Number of seconds before clearing block watchers due to inactivity
     */
    private static final int BLOCK_WATCHER_CLEAR_DELAY = 5;

    private final Logger logger;
    private final IBlockChangeWatcher.Factory blockChangeWatcherFactory;
    private final Map<UUID, IBlockChangeWatcher> watchers = new HashMap<>();
    // Stores last-request time as epoch millis — avoids Instant/Duration allocations each tick
    private final Map<UUID, Long> lastRequestedMillis = new HashMap<>();

    @Inject
    public ExternalBlockWatcherManager(Logger logger, IBlockChangeWatcher.Factory blockChangeWatcherFactory) {
        this.logger = logger;
        this.blockChangeWatcherFactory = blockChangeWatcherFactory;
    }

    @Override
    public void onRequestReceived(GetBlockDataChangesRequest request, Consumer<Response> onFinish) {
        logger.finer("Processing block changes with ID %s", request.getChangeSetId());
        UUID watcherId = request.getChangeSetId();
        IBlockChangeWatcher watcher = watchers.computeIfAbsent(watcherId, key -> blockChangeWatcherFactory.create(request));
        lastRequestedMillis.put(watcherId, System.currentTimeMillis());

        Response response = new Response();
        Map<IntVector, Integer> changes = watcher.checkForChanges();
        logger.finer("Change count: %d", changes.size());

        response.setResult(changes);
        onFinish.accept(response);
    }

    @Override
    public void update() {
        // Early exit — nothing to clean up if no watchers are active
        if (lastRequestedMillis.isEmpty()) return;

        // Clear any watchers that are inactive, using millis to avoid Instant/Duration allocations
        long now = System.currentTimeMillis();
        long clearThresholdMs = BLOCK_WATCHER_CLEAR_DELAY * 1000L;
        Iterator<Map.Entry<UUID, Long>> iterator = lastRequestedMillis.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (now - entry.getValue() > clearThresholdMs) {
                logger.fine("Clearing external block watcher due to inactivity");
                iterator.remove();
                watchers.remove(entry.getKey());
            }
        }
    }

    @Override
    public void clear() {
        watchers.clear();
        lastRequestedMillis.clear();
    }
}
