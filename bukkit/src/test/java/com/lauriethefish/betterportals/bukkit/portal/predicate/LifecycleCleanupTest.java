package com.lauriethefish.betterportals.bukkit.portal.predicate;

import com.lauriethefish.betterportals.bukkit.BetterPortals;
import com.lauriethefish.betterportals.bukkit.block.external.IExternalBlockWatcherManager;
import com.lauriethefish.betterportals.bukkit.config.MiscConfig;
import com.lauriethefish.betterportals.bukkit.config.ProxyConfig;
import com.lauriethefish.betterportals.bukkit.net.ClientReconnectHandler;
import com.lauriethefish.betterportals.bukkit.net.IClientReconnectHandler;
import com.lauriethefish.betterportals.bukkit.net.IPortalClient;
import com.lauriethefish.betterportals.bukkit.net.PortalClient;
import com.lauriethefish.betterportals.bukkit.player.IPlayerDataManager;
import com.lauriethefish.betterportals.bukkit.player.PlayerDataManager;
import com.lauriethefish.betterportals.bukkit.portal.IPortalManager;
import com.lauriethefish.betterportals.bukkit.portal.storage.IPortalStorage;
import com.lauriethefish.betterportals.bukkit.tasks.AsyncBlockUpdateFinisher;
import com.lauriethefish.betterportals.bukkit.tasks.BlockUpdateFinisher;
import com.lauriethefish.betterportals.bukkit.tasks.MainUpdate;
import com.lauriethefish.betterportals.bukkit.util.SchedulerUtil;
import com.lauriethefish.betterportals.shared.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.lauriethefish.betterportals.bukkit.block.external.ExternalBlockWatcherManager;

import static org.junit.jupiter.api.Assertions.*;

public class LifecycleCleanupTest {
    private TestLogger logger;
    private AtomicInteger activeTasksCount;
    private List<MockBukkitTaskInfo> trackedTasks;
    private JavaPlugin mockPlugin;
    private org.bukkit.plugin.PluginManager mockPluginManager;
    private List<org.bukkit.entity.Player> onlinePlayers;

    public static class MockPlugin extends JavaPlugin {
    }

    private static class TestLogger extends Logger {
        protected TestLogger() {
            super("TestLogger", null);
        }
    }

    private static class MockBukkitTaskInfo {
        public final String methodCalled;
        public boolean isCancelled = false;

        public MockBukkitTaskInfo(String methodCalled) {
            this.methodCalled = methodCalled;
        }
    }

    private static class TestMiscConfig extends MiscConfig {
        private final int portalSaveInterval;
        public TestMiscConfig(int portalSaveInterval) {
            super(null);
            this.portalSaveInterval = portalSaveInterval;
        }
        @Override
        public int getPortalSaveInterval() {
            return portalSaveInterval;
        }
    }

    private static class TestProxyConfig extends ProxyConfig {
        private final int reconnectionDelay;
        public TestProxyConfig(int reconnectionDelay) {
            super(null);
            this.reconnectionDelay = reconnectionDelay;
        }
        @Override
        public int getReconnectionDelay() {
            return reconnectionDelay;
        }
    }

    @BeforeEach
    public void setUp() {
        logger = new TestLogger();
        activeTasksCount = new AtomicInteger(0);
        trackedTasks = new ArrayList<>();
        onlinePlayers = new ArrayList<>();

        mockPluginManager = (org.bukkit.plugin.PluginManager) Proxy.newProxyInstance(
                org.bukkit.plugin.PluginManager.class.getClassLoader(),
                new Class<?>[]{org.bukkit.plugin.PluginManager.class},
                (proxy, method, args) -> null
        );

        try {
            java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            mockPlugin = (JavaPlugin) unsafe.allocateInstance(MockPlugin.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Create a mock BukkitScheduler that tracks scheduled and cancelled tasks
        BukkitScheduler mockScheduler = (BukkitScheduler) Proxy.newProxyInstance(
                BukkitScheduler.class.getClassLoader(),
                new Class<?>[]{BukkitScheduler.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if (methodName.startsWith("runTask") || methodName.startsWith("scheduleSync")) {
                        activeTasksCount.incrementAndGet();
                        MockBukkitTaskInfo taskInfo = new MockBukkitTaskInfo(methodName);
                        trackedTasks.add(taskInfo);

                        // Return a mock BukkitTask
                        return Proxy.newProxyInstance(
                                BukkitTask.class.getClassLoader(),
                                new Class<?>[]{BukkitTask.class},
                                (taskProxy, taskMethod, taskArgs) -> {
                                    if (taskMethod.getName().equals("cancel")) {
                                        if (!taskInfo.isCancelled) {
                                            taskInfo.isCancelled = true;
                                            activeTasksCount.decrementAndGet();
                                        }
                                        return null;
                                    }
                                    if (taskMethod.getName().equals("getTaskId")) {
                                        return 1;
                                    }
                                    return null;
                                }
                        );
                    }
                    return null;
                }
        );

        // Load Folia classes and create proxies if they are available
        Class<?> globalRegionSchedulerClass = null;
        Class<?> asyncSchedulerClass = null;
        Class<?> regionSchedulerClass = null;
        Class<?> scheduledTaskClass = null;
        try {
            globalRegionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            asyncSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            regionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
        } catch (ClassNotFoundException ignored) {}

        final Class<?> finalScheduledTaskClass = scheduledTaskClass;
        
        Object mockGlobalRegionScheduler = globalRegionSchedulerClass == null ? null : Proxy.newProxyInstance(
                globalRegionSchedulerClass.getClassLoader(),
                new Class<?>[]{globalRegionSchedulerClass},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("run")) {
                        activeTasksCount.incrementAndGet();
                        MockBukkitTaskInfo taskInfo = new MockBukkitTaskInfo(method.getName());
                        trackedTasks.add(taskInfo);

                        return Proxy.newProxyInstance(
                                finalScheduledTaskClass.getClassLoader(),
                                new Class<?>[]{finalScheduledTaskClass},
                                (taskProxy, taskMethod, taskArgs) -> {
                                    if (taskMethod.getName().equals("cancel")) {
                                        if (!taskInfo.isCancelled) {
                                            taskInfo.isCancelled = true;
                                            activeTasksCount.decrementAndGet();
                                        }
                                    }
                                    return null;
                                }
                        );
                    }
                    return null;
                }
        );

        Object mockAsyncScheduler = asyncSchedulerClass == null ? null : Proxy.newProxyInstance(
                asyncSchedulerClass.getClassLoader(),
                new Class<?>[]{asyncSchedulerClass},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("run")) {
                        activeTasksCount.incrementAndGet();
                        MockBukkitTaskInfo taskInfo = new MockBukkitTaskInfo(method.getName());
                        trackedTasks.add(taskInfo);

                        return Proxy.newProxyInstance(
                                finalScheduledTaskClass.getClassLoader(),
                                new Class<?>[]{finalScheduledTaskClass},
                                (taskProxy, taskMethod, taskArgs) -> {
                                    if (taskMethod.getName().equals("cancel")) {
                                        if (!taskInfo.isCancelled) {
                                            taskInfo.isCancelled = true;
                                            activeTasksCount.decrementAndGet();
                                        }
                                    }
                                    return null;
                                }
                        );
                    }
                    return null;
                }
        );

        Object mockRegionScheduler = regionSchedulerClass == null ? null : Proxy.newProxyInstance(
                regionSchedulerClass.getClassLoader(),
                new Class<?>[]{regionSchedulerClass},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("run")) {
                        activeTasksCount.incrementAndGet();
                        MockBukkitTaskInfo taskInfo = new MockBukkitTaskInfo(method.getName());
                        trackedTasks.add(taskInfo);

                        return Proxy.newProxyInstance(
                                finalScheduledTaskClass.getClassLoader(),
                                new Class<?>[]{finalScheduledTaskClass},
                                (taskProxy, taskMethod, taskArgs) -> {
                                    if (taskMethod.getName().equals("cancel")) {
                                        if (!taskInfo.isCancelled) {
                                            taskInfo.isCancelled = true;
                                            activeTasksCount.decrementAndGet();
                                        }
                                    }
                                    return null;
                                }
                        );
                    }
                    return null;
                }
        );

        // Setup mock server using dynamic proxy to return the mock schedulers
        Server mockServer = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getMinecraftVersion")) {
                        return "1.21";
                    }
                    if (name.equals("getScheduler")) {
                        return mockScheduler;
                    }
                    if (name.equals("getGlobalRegionScheduler")) {
                        return mockGlobalRegionScheduler;
                    }
                    if (name.equals("getAsyncScheduler")) {
                        return mockAsyncScheduler;
                    }
                    if (name.equals("getRegionScheduler")) {
                        return mockRegionScheduler;
                    }
                    if (name.equals("getOnlinePlayers")) {
                        return onlinePlayers;
                    }
                    if (name.equals("getPluginManager")) {
                        return mockPluginManager;
                    }
                    return null;
                }
        );

        try {
            Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, mockServer);

            Class<?> current = mockPlugin.getClass();
            while (current != null) {
                try {
                    Field f = current.getDeclaredField("server");
                    f.setAccessible(true);
                    f.set(mockPlugin, mockServer);
                    break;
                } catch (NoSuchFieldException e) {
                    current = current.getSuperclass();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testMainUpdateCancellation() {
        SchedulerUtil.init(mockPlugin);

        MainUpdate mainUpdate = new MainUpdate(mockPlugin, null, null, null, null, null, logger);

        assertEquals(0, activeTasksCount.get());
        mainUpdate.start();
        assertEquals(1, activeTasksCount.get());

        // Starting again should cancel the old task and create a new one, keeping count at 1
        mainUpdate.start();
        assertEquals(1, activeTasksCount.get());
        assertTrue(trackedTasks.get(0).isCancelled, "First scheduled task should be cancelled");

        // Stop should cancel the task
        mainUpdate.stop();
        assertEquals(0, activeTasksCount.get());
        assertTrue(trackedTasks.get(1).isCancelled, "Second scheduled task should be cancelled");
    }

    @Test
    public void testPortalStorageCancellation() {
        SchedulerUtil.init(mockPlugin);

        MiscConfig miscConfig = new TestMiscConfig(200);

        IPortalStorage portalStorage = new IPortalStorage(logger, mockPlugin, miscConfig) {
            @Override
            public void loadPortals() {}

            @Override
            public void savePortals() {}
        };

        assertEquals(0, activeTasksCount.get());
        portalStorage.start();
        assertEquals(1, activeTasksCount.get());

        // Restart should cancel old and start new
        portalStorage.start();
        assertEquals(1, activeTasksCount.get());
        assertTrue(trackedTasks.get(0).isCancelled, "First autosave task should be cancelled");

        // Stop should cancel the task
        portalStorage.stop();
        assertEquals(0, activeTasksCount.get());
        assertTrue(trackedTasks.get(1).isCancelled, "Second autosave task should be cancelled");
    }

    @Test
    public void testAsyncBlockUpdateFinisherCancellation() {
        SchedulerUtil.init(mockPlugin);

        AsyncBlockUpdateFinisher finisher = new AsyncBlockUpdateFinisher(mockPlugin, logger);

        assertEquals(0, activeTasksCount.get());
        finisher.start();
        assertEquals(1, activeTasksCount.get());

        // Restart should cancel old and start new
        finisher.start();
        assertEquals(1, activeTasksCount.get());
        assertTrue(trackedTasks.get(0).isCancelled, "First block updater task should be cancelled");

        // Stop should cancel the task
        finisher.stop();
        assertEquals(0, activeTasksCount.get());
        assertTrue(trackedTasks.get(1).isCancelled, "Second block updater task should be cancelled");
    }

    @Test
    public void testClientReconnectHandlerCancellation() {
        SchedulerUtil.init(mockPlugin);

        ProxyConfig proxyConfig = new TestProxyConfig(20);

        IPortalClient mockClient = (IPortalClient) Proxy.newProxyInstance(
                IPortalClient.class.getClassLoader(),
                new Class<?>[]{IPortalClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getShouldReconnect")) {
                        return true;
                    }
                    return null;
                }
        );

        IClientReconnectHandler reconnectHandler = new ClientReconnectHandler(mockPlugin, proxyConfig, mockClient, logger);

        assertEquals(0, activeTasksCount.get());
        reconnectHandler.onClientDisconnect();
        assertEquals(1, activeTasksCount.get());

        // Stop should cancel the reconnection task
        reconnectHandler.stop();
        assertEquals(0, activeTasksCount.get());
        assertTrue(trackedTasks.get(0).isCancelled, "Reconnection task should be cancelled");
    }

    @Test
    public void testPlayerDataManagerReloadVsDisable() {
        org.bukkit.entity.Player mockPlayer = (org.bukkit.entity.Player) Proxy.newProxyInstance(
                org.bukkit.entity.Player.class.getClassLoader(),
                new Class<?>[]{org.bukkit.entity.Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getUniqueId")) {
                        return UUID.randomUUID();
                    }
                    if (name.equals("hashCode")) {
                        return 42;
                    }
                    if (name.equals("equals")) {
                        return proxy == args[0];
                    }
                    if (name.equals("toString")) {
                        return "MockPlayer";
                    }
                    return null;
                }
        );
        onlinePlayers.add(mockPlayer);

        com.lauriethefish.betterportals.bukkit.events.IEventRegistrar mockRegistrar = (com.lauriethefish.betterportals.bukkit.events.IEventRegistrar) Proxy.newProxyInstance(
                com.lauriethefish.betterportals.bukkit.events.IEventRegistrar.class.getClassLoader(),
                new Class<?>[]{com.lauriethefish.betterportals.bukkit.events.IEventRegistrar.class},
                (proxy, method, args) -> null
        );

        com.lauriethefish.betterportals.bukkit.player.IPlayerData.Factory mockFactory = (com.lauriethefish.betterportals.bukkit.player.IPlayerData.Factory) Proxy.newProxyInstance(
                com.lauriethefish.betterportals.bukkit.player.IPlayerData.Factory.class.getClassLoader(),
                new Class<?>[]{com.lauriethefish.betterportals.bukkit.player.IPlayerData.Factory.class},
                (proxy, method, args) -> {
                    String mName = method.getName();
                    if (mName.equals("create")) {
                        return Proxy.newProxyInstance(
                                com.lauriethefish.betterportals.bukkit.player.IPlayerData.class.getClassLoader(),
                                new Class<?>[]{com.lauriethefish.betterportals.bukkit.player.IPlayerData.class},
                                (pProxy, pMethod, pArgs) -> {
                                    String pName = pMethod.getName();
                                    if (pName.equals("hashCode")) {
                                        return 43;
                                    }
                                    if (pName.equals("equals")) {
                                        return pProxy == pArgs[0];
                                    }
                                    if (pName.equals("toString")) {
                                        return "MockPlayerData";
                                    }
                                    return null;
                                }
                        );
                    }
                    if (mName.equals("hashCode")) {
                        return 44;
                    }
                    if (mName.equals("equals")) {
                        return proxy == args[0];
                    }
                    if (mName.equals("toString")) {
                        return "MockPlayerDataFactory";
                    }
                    return null;
                }
        );

        ProxyConfig mockProxyConfig = new TestProxyConfig(20);

        PlayerDataManager manager = new PlayerDataManager(mockRegistrar, logger, mockFactory, mockProxyConfig);
        assertEquals(1, manager.getPlayers().size(), "Should have registered 1 player initially");

        // Disable
        manager.onPluginDisable();
        assertEquals(0, manager.getPlayers().size(), "Should have no players after disable");

        // Reload
        manager.onPluginReload();
        assertEquals(1, manager.getPlayers().size(), "Should have re-registered 1 player after reload");
    }

    @Test
    public void testEventRegistrarReload() {
        AtomicInteger registerEventsCallCount = new AtomicInteger(0);
        mockPluginManager = (org.bukkit.plugin.PluginManager) Proxy.newProxyInstance(
                org.bukkit.plugin.PluginManager.class.getClassLoader(),
                new Class<?>[]{org.bukkit.plugin.PluginManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("registerEvents")) {
                        registerEventsCallCount.incrementAndGet();
                    }
                    return null;
                }
        );

        com.lauriethefish.betterportals.bukkit.events.EventRegistrar registrar = new com.lauriethefish.betterportals.bukkit.events.EventRegistrar(mockPlugin, logger);
        org.bukkit.event.Listener dummyListener = new org.bukkit.event.Listener() {};

        registrar.register(dummyListener);
        assertEquals(1, registerEventsCallCount.get(), "registerEvents should have been called once");

        registrar.onPluginReload();
        assertEquals(2, registerEventsCallCount.get(), "registerEvents should have been called again on reload");
    }

    @Test
    public void testExternalBlockWatcherManagerClear() throws Exception {
        com.lauriethefish.betterportals.bukkit.block.external.IBlockChangeWatcher.Factory mockWatcherFactory = (com.lauriethefish.betterportals.bukkit.block.external.IBlockChangeWatcher.Factory) Proxy.newProxyInstance(
                com.lauriethefish.betterportals.bukkit.block.external.IBlockChangeWatcher.Factory.class.getClassLoader(),
                new Class<?>[]{com.lauriethefish.betterportals.bukkit.block.external.IBlockChangeWatcher.Factory.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("create")) {
                        return Proxy.newProxyInstance(
                                com.lauriethefish.betterportals.bukkit.block.external.IBlockChangeWatcher.class.getClassLoader(),
                                new Class<?>[]{com.lauriethefish.betterportals.bukkit.block.external.IBlockChangeWatcher.class},
                                (wProxy, wMethod, wArgs) -> {
                                    if (wMethod.getName().equals("checkForChanges")) {
                                        return new java.util.HashMap<>();
                                    }
                                    return null;
                                }
                        );
                    }
                    return null;
                }
        );

        ExternalBlockWatcherManager manager = new ExternalBlockWatcherManager(logger, mockWatcherFactory);

        UUID changeSetId = java.util.UUID.randomUUID();
        com.lauriethefish.betterportals.bukkit.net.requests.GetBlockDataChangesRequest request = new com.lauriethefish.betterportals.bukkit.net.requests.GetBlockDataChangesRequest();
        request.setChangeSetId(changeSetId);

        manager.onRequestReceived(request, (response) -> {});

        java.lang.reflect.Field watchersField = ExternalBlockWatcherManager.class.getDeclaredField("watchers");
        watchersField.setAccessible(true);
        java.util.Map<?, ?> watchers = (java.util.Map<?, ?>) watchersField.get(manager);
        assertEquals(1, watchers.size(), "Should have 1 watcher registered");

        manager.clear();
        assertEquals(0, watchers.size(), "Should have 0 watchers after clear");
    }

    @Test
    public void testSchedulerUtilMultipleCancelAll() {
        SchedulerUtil.init(mockPlugin);
        SchedulerUtil.cancelAll();
        SchedulerUtil.cancelAll();
    }
}
