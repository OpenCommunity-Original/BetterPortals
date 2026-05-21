package com.lauriethefish.betterportals.bukkit.portal.predicate;

import com.lauriethefish.betterportals.api.BetterPortal;
import com.lauriethefish.betterportals.api.PortalDirection;
import com.lauriethefish.betterportals.api.PortalPosition;
import com.lauriethefish.betterportals.bukkit.net.IPortalClient;
import com.lauriethefish.betterportals.shared.logging.Logger;
import com.lauriethefish.betterportals.shared.net.Response;
import com.lauriethefish.betterportals.shared.net.RequestException;
import com.lauriethefish.betterportals.shared.net.requests.Request;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class CrossServerDestinationCheckerTest {
    private TestLogger logger;
    private TestPortalClient portalClient;
    private CrossServerDestinationChecker checker;

    private static class TestLogger extends Logger {
        protected TestLogger() {
            super("TestLogger", null);
        }
    }

    private static class TestPortal implements BetterPortal {
        private final UUID id = UUID.randomUUID();
        private final boolean crossServer;
        private final PortalPosition destPos;

        public TestPortal(boolean crossServer, PortalPosition destPos) {
            this.crossServer = crossServer;
            this.destPos = destPos;
        }

        @Override
        public UUID getId() { return id; }

        @Override
        public UUID getOwnerId() { return null; }

        @Override
        public String getName() { return null; }

        @Override
        public void setName(String name) {}

        @Override
        public PortalPosition getOriginPos() { return null; }

        @Override
        public PortalPosition getDestPos() { return destPos; }

        @Override
        public Vector getSize() { return new Vector(2, 3, 0); }

        @Override
        public boolean isCrossServer() { return crossServer; }

        @Override
        public boolean isCustom() { return false; }

        @Override
        public void remove(boolean removeOtherDirection) {}
    }

    private static class TestPortalClient implements IPortalClient {
        public boolean receiveRequests = true;
        public final List<Request> sentRequests = new ArrayList<>();
        public final List<String> sentDestinations = new ArrayList<>();
        public Consumer<Response> lastResponseCallback;

        @Override
        public void connect(boolean printExceptions) {}

        @Override
        public void shutDown() {}

        @Override
        public boolean canReceiveRequests() { return receiveRequests; }

        @Override
        public boolean isConnectionOpen() { return true; }

        @Override
        public boolean getShouldReconnect() { return false; }

        @Override
        public void sendRequestToProxy(Request request, Consumer<Response> onReceive) {}

        @Override
        public void sendRequestToServer(Request request, String destinationServer, Consumer<Response> onReceive) {
            sentRequests.add(request);
            sentDestinations.add(destinationServer);
            lastResponseCallback = onReceive;
        }
    }

    @BeforeEach
    public void setUp() {
        logger = new TestLogger();
        portalClient = new TestPortalClient();
        checker = new CrossServerDestinationChecker(logger, portalClient);

        // Setup mock server using dynamic proxy to avoid external dependencies
        Server mockServer = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getMinecraftVersion")) {
                        return "1.21";
                    }
                    return null;
                }
        );
        try {
            java.lang.reflect.Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, mockServer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testNonCrossServerBypass() {
        TestPortal portal = new TestPortal(false, null);
        assertTrue(checker.test(portal, null), "Non-cross-server portal should bypass checker");
        assertEquals(0, portalClient.sentRequests.size(), "No requests should be sent for non-cross-server portal");
    }

    @Test
    public void testClientDisconnected() {
        PortalPosition destPos = new PortalPosition(new Vector(1, 2, 3), PortalDirection.EAST, "targetServer", "worldName");
        TestPortal portal = new TestPortal(true, destPos);
        portalClient.receiveRequests = false;
        assertFalse(checker.test(portal, null), "Disconnected client should make portal invalid");
    }

    @Test
    public void testValidityCheckFlow() {
        PortalPosition destPos = new PortalPosition(new Vector(1, 2, 3), PortalDirection.EAST, "targetServer", "worldName");
        TestPortal portal = new TestPortal(true, destPos);

        // Initial check: no cached validity, so returns false and sends request
        assertFalse(checker.test(portal, null), "Initial check should be false");
        assertEquals(1, portalClient.sentRequests.size());
        assertEquals("targetServer", portalClient.sentDestinations.get(0));

        // Complete request successfully
        Response successResponse = new Response();
        portalClient.lastResponseCallback.accept(successResponse);

        // Subsequent check: should be valid now from cache
        assertTrue(checker.test(portal, null), "Portal should be valid after successful request");
    }

    @Test
    public void testValidityCheckErrorFlow() {
        PortalPosition destPos = new PortalPosition(new Vector(1, 2, 3), PortalDirection.EAST, "targetServer", "worldName");
        TestPortal portal = new TestPortal(true, destPos);

        // Initial check
        assertFalse(checker.test(portal, null));

        // Complete request with error
        Response errorResponse = new Response();
        errorResponse.setError(new RequestException("destination world not found"));
        portalClient.lastResponseCallback.accept(errorResponse);

        // Subsequent check: should still be invalid
        assertFalse(checker.test(portal, null), "Portal should be invalid after error response");
    }

    @Test
    public void testCachePruning() throws Exception {
        PortalPosition destPos = new PortalPosition(new Vector(1, 2, 3), PortalDirection.EAST, "targetServer", "worldName");
        TestPortal portal1 = new TestPortal(true, destPos);
        TestPortal portal2 = new TestPortal(true, destPos);

        java.lang.reflect.Field cacheField = CrossServerDestinationChecker.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<BetterPortal, Object> cache = (java.util.Map<BetterPortal, Object>) cacheField.get(checker);

        Class<?> cacheEntryClass = Class.forName("com.lauriethefish.betterportals.bukkit.portal.predicate.CrossServerDestinationChecker$CacheEntry");
        java.lang.reflect.Constructor<?> cacheEntryConstructor = cacheEntryClass.getDeclaredConstructor(boolean.class, java.time.Instant.class);
        cacheEntryConstructor.setAccessible(true);

        java.time.Instant expiredTime = java.time.Instant.now().minus(java.time.Duration.ofSeconds(40));
        java.time.Instant freshTime = java.time.Instant.now();

        Object expiredEntry = cacheEntryConstructor.newInstance(true, expiredTime);
        Object freshEntry = cacheEntryConstructor.newInstance(true, freshTime);

        cache.put(portal1, expiredEntry);
        cache.put(portal2, freshEntry);

        java.lang.reflect.Field lastPruneTimeField = CrossServerDestinationChecker.class.getDeclaredField("lastPruneTime");
        lastPruneTimeField.setAccessible(true);
        lastPruneTimeField.set(checker, java.time.Instant.now().minus(java.time.Duration.ofSeconds(15)));

        java.lang.reflect.Method pruneCacheMethod = CrossServerDestinationChecker.class.getDeclaredMethod("pruneCache");
        pruneCacheMethod.setAccessible(true);
        pruneCacheMethod.invoke(checker);

        assertFalse(cache.containsKey(portal1), "Expired portal should have been pruned");
        assertTrue(cache.containsKey(portal2), "Fresh portal should not have been pruned");
    }
}
