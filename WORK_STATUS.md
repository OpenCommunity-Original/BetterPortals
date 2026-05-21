# BetterPortals — Work Session Status

> **Status**: COMPLETED — all tests GREEN (BUILD SUCCESSFUL)  
> **Purpose**: Resume point for the next AI session

---

## 1. Project Overview

**BetterPortals** is a Minecraft plugin that renders visual portals between worlds and servers. It is a multi-module Gradle Java project targeting Bukkit/Paper/Folia, BungeeCord, and Velocity.

### Module Map

| Module | Path | Description |
|--------|------|-------------|
| `api` | `/api` | Public Java API (interfaces for external plugins) |
| `bukkit` | `/bukkit` | Main Bukkit/Paper/Folia plugin logic |
| `bungee` | `/bungee` | BungeeCord proxy plugin |
| `velocity` | `/velocity` | Velocity proxy plugin |
| `proxy` | `/proxy` | Shared proxy networking layer (used by bungee + velocity) |
| `shared` | `/shared` | Shared utilities, logging, encryption, net packet defs |
| `final` | `/final` | Final shadow JAR assembly |

### Key Technology Stack
- Java 17+, Gradle 8.10.2
- Guice (dependency injection)
- Lombok (boilerplate reduction)
- Paper/Folia API (dual-scheduler support)
- BungeeCord + Velocity APIs

---

## 2. Work Completed This Session

### 2.1 SchedulerUtil — Folia Support + Task Tracking
**File**: `bukkit/src/main/java/.../util/SchedulerUtil.java`

- Replaced all raw `Bukkit.getScheduler()` calls with a unified `SchedulerUtil` abstraction.
- Added full **Folia compatibility**: uses `GlobalRegionScheduler`, `AsyncScheduler`, `RegionScheduler`, `EntityScheduler` when Folia is detected at runtime.
- Added **global task tracking** via a `ConcurrentHashMap.newKeySet()` set of active `PortalTask` references.
- Added `SchedulerUtil.cancelAll()` — cancels every tracked task at once (used on reload/disable).
- One-shot tasks auto-remove themselves from the tracking set on completion.

### 2.2 BetterPortals.java — Lifecycle Fixes
**File**: `bukkit/src/main/java/.../BetterPortals.java`

- `softReload()` now calls `SchedulerUtil.cancelAll()` before re-starting tasks — prevents ghost tasks surviving reload.
- `onDisable()` now calls `SchedulerUtil.cancelAll()` — guarantees all scheduler threads are terminated on shutdown.

### 2.3 MainUpdate — Stop/Restart Safety
**File**: `bukkit/src/main/java/.../tasks/MainUpdate.java`

- Stores a `PortalTask` reference.
- `start()` cancels the previous task before creating a new one.
- `stop()` cancels and nulls the reference.

### 2.4 IPortalStorage — Autosave Task Safety
**File**: `bukkit/src/main/java/.../portal/storage/IPortalStorage.java`

- Stores a `PortalTask` reference for the autosave timer.
- `start()` calls `stop()` first to cancel any previous autosave.
- `stop()` cancels and nulls the reference.

### 2.5 AsyncBlockUpdateFinisher — Async Task Safety
**File**: `bukkit/src/main/java/.../tasks/AsyncBlockUpdateFinisher.java`

- Stores a `PortalTask` reference.
- `start()` calls `stop()` before scheduling a new async repeating task.
- `stop()` cancels the task properly.

### 2.6 ClientReconnectHandler — Reconnection Task Leak Fix
**File**: `bukkit/src/main/java/.../net/ClientReconnectHandler.java`  
**Interface**: `IClientReconnectHandler.java`

- Added `stop()` method to `IClientReconnectHandler` interface.
- `ClientReconnectHandler.stop()` cancels the reconnection timer task.
- `PortalClient.shutDown()` now calls `reconnectHandler.stop()` first.

### 2.7 EventRegistrar — Listener Duplication Fix
**File**: `bukkit/src/main/java/.../events/EventRegistrar.java`

- `onPluginReload()` now calls `HandlerList.unregisterAll(plugin)` before re-registering all listeners.
- Prevents duplicate event handler triggers on each `/bp reload`.

### 2.8 PlayerDataManager — Player Reference Leak Fix
**File**: `bukkit/src/main/java/.../player/PlayerDataManager.java`

- `onPluginDisable()` only calls `IPlayerData::onPluginDisable` + clears maps — does NOT re-add players.
- `onPluginReload()` clears + re-adds online players (soft reload only).
- `onPlayerLeave()` schedules a delayed cleanup for `loggedOutPlayerSelections` entries (6000 ticks = 5 min) to prevent indefinite map growth.

### 2.9 ExternalBlockWatcherManager — Cache Clearance
**File**: `bukkit/src/main/java/.../block/external/ExternalBlockWatcherManager.java`  
**Interface**: `IExternalBlockWatcherManager.java`

- Added `clear()` method to interface.
- `ExternalBlockWatcherManager.clear()` clears all cached block watcher entries.
- Called in `softReload()` and `onDisable()`.

### 2.10 CrossServerDestinationChecker — Cache + Concurrency Fix
**File**: `bukkit/src/main/java/.../portal/predicate/CrossServerDestinationChecker.java`

- Added `clear()` method to interface + implementation.
- Rewrote `pruneCache()` to use `entrySet().removeIf(...)` instead of direct iteration + removal (was `ConcurrentModificationException`-prone).
- Merged active maps into a single `ConcurrentHashMap<BetterPortal, CacheEntry>` for efficiency and reduced thread contention.
- Called in `softReload()` and `onDisable()`.

### 2.11 MultiBlockChangeManager — Renamed File
- Old name was `MultiBlockChangeManager_1_16_2.java` (a legacy naming artifact).
- Renamed to `MultiBlockChangeManager.java`.

### 2.12 Docs Created (`/docs/`)
| File | Description |
|------|-------------|
| `project_structure.md` | Full module map and source layout |
| `developer_guide.md` | How to build, run, contribute, coding conventions |
| `setup_guide.md` | Server setup guide (Paper, Folia, Bungee, Velocity) |
| `networking_protocol.md` | Cross-server packet format and encryption details |

### 2.13 README.md Rewrite
- Full modern README with badges, feature table, quick-start, Folia notice, config reference pointers, license.

### 2.14 Gradle 9.0 Compatibility
- Removed deprecated `archivesBaseName` configuration property in `build.gradle` and `final/build.gradle` in favor of `base { archivesName.set(...) }`.
- Replaced deprecated `configurations.runtime` dependency mapping with `runtimeClasspath` in `final/build.gradle`.

### 2.15 Config Range Validation
- Implemented parameter range checks and validation constraints in `MiscConfig`, `PortalSpawnConfig`, and `ProxyConfig` classes to catch invalid settings on startup/reload.

### 2.16 API Layer Hardening
- Implemented `ApiPortalWrapper` to wrap internal `BetterPortal` objects and resolve them dynamically, preventing reference holding/stale reference leaks during soft reloads.
- Updated `API.java` to return the wrapper and expose `getPortalManager()`. Added `@since` tags to `BetterPortalsAPI.java`.

### 2.17 Expanded Automated Test Suite
- **Added cache pruning tests** in `CrossServerDestinationCheckerTest.java` verifying TTL check and expired entry removal.
- **Added lifecycle cleanup tests** in `LifecycleCleanupTest.java` verifying:
  - `PlayerDataManager` reloading vs disabling behavior.
  - `EventRegistrar` listener unregistration.
  - `ExternalBlockWatcherManager.clear()` verification.
  - Safe execution of multiple `SchedulerUtil.cancelAll()` calls.

---

## 3. Current Build State

```
BUILD SUCCESSFUL
All tests: PASS (including 14 bukkit tests + shared tests)
Jar generation: UP-TO-DATE (shadowJar completes successfully)
```

---

## 4. What Needs To Be Done Next

### COMPLETED THIS SESSION
- **[x] Memory Leak: Folia Thread Locals**: Audited codebase for `ThreadLocal` usage. Confirmed there is no `ThreadLocal` usage in any of the modules, preventing region thread context leakage on Folia.
- **[x] SchedulerUtil — cancelAll() Safety**: Refactored `cancelAll()` to copy the active task set to an `ArrayList` prior to iterating, ensuring concurrent modification safety when tasks unregister themselves during cancellation.
- **[x] Velocity & BungeeCord Plugins — Resource/Listener Leak Audit**: Verified that `PortalServer` cleanly shuts down all server socket and client connections. Bungee listener handles `ServerSwitch` and is unregistered automatically by Bungee on disable. Velocity listener is annotated with `@Subscribe` on the main class and is cleanly managed by the platform.
- **[x] Deprecation Warnings in Source**: Resolved all 11 Java deprecation warnings across `bukkit` and `proxy` modules. The project now builds completely warning-free.
- **[x] NMS Layer Version Verification**: Verified that standard reflection in `BlockDataUtil` and modern `EntityPacketManipulator` is fully compatible with 1.20.x/1.21.x without any experimental crashes.

All objectives are fully accomplished. No outstanding priority tasks remain.

---

## 5. Key Files Reference

| File | Role |
|------|------|
| `BetterPortals.java` | Main plugin entry point — `onEnable`, `onDisable`, `softReload` |
| `MainModule.java` | Guice DI module — all bindings here |
| `SchedulerUtil.java` | Unified scheduler (Paper + Folia) with task tracking |
| `LifecycleCleanupTest.java` | Core lifecycle safety tests |
| `CrossServerDestinationChecker.java` | Cache with TTL for cross-server portal validity |
| `ExternalBlockWatcherManager.java` | Per-chunk block state watchers |
| `PlayerDataManager.java` | Per-player data map lifecycle |
| `EventRegistrar.java` | Central Bukkit event listener registration |
| `ClientReconnectHandler.java` | BungeeCord/Velocity reconnection scheduling |
| `PortalClient.java` | Network connection to BungeeCord/Velocity proxy plugin |
| `ApiPortalWrapper.java` | Wrapper to insulate external API users from internal reload cycles |

---

## 6. How to Resume

1. **Read this file** to get context.
2. **Run** `.\gradlew.bat test` — should be 14/14 PASS.
3. **Pick the next task** from Section 4 above (suggested: start with 4.1 — Folia Thread Locals).
4. **Reference the walkthrough** at `C:\Users\Nikita\.gemini\antigravity-ide\brain\f4d42c73-de40-4190-b9e3-a3045df7357e\walkthrough.md` for the full narrative of what changed and why.

---

## 7. Architecture Notes (Important)

- **Guice injection**: Everything is `@Singleton` and injected via `MainModule`. No `new` in plugin code — always inject.
- **Folia**: `SchedulerUtil.isFolia()` is the runtime gate. All scheduling MUST go through `SchedulerUtil`, never call `Bukkit.getScheduler()` directly.
- **Soft reload**: `/bp reload` calls `BetterPortals.softReload()` (does NOT restart the JVM process). All stateful components must support being stopped and restarted cleanly.
- **Thread model**: On Paper = main thread + async pool. On Folia = per-region threads. Assume nothing about which thread you're on.
- **No Mockito**: Tests use `Proxy.newProxyInstance` + `Unsafe.allocateInstance` only. Keep it that way to avoid extra test dependencies.
