# BetterPortals Configuration Guide

This guide describes all the configuration settings available in `config.yml` (located in `plugins/BetterPortals/`). Many of these options allow you to balance visual quality with server and client performance.

---

## 🏎️ Performance Tuning & Optimizations

These configuration parameters are the most critical for server TPS and client-side performance:

| Key | Default | Type | Description |
| :--- | :--- | :--- | :--- |
| `minTpsForRendering` | `19.0` | `double` | **TPS Guard:** The minimum server TPS required to render/visualize portals. If the TPS drops below this threshold, portal rendering is temporarily disabled for all players to save CPU time. Set to `0` to disable the TPS guard. |
| `maxPortalsPerPlayer` | `3` | `int` | **Fair Use Policy:** Limits the number of portals rendered simultaneously for a single player. Closest portals are prioritized. Set to `0` to disable the limit (unlimited rendering). |
| `allowedSpawnTimePerTick` | `35` | `int` | The maximum allowed CPU processing time (in milliseconds) per tick for finding portal destinations. This prevents the server main thread from freezing when players travel through portals. |
| `entityCheckInterval` | `7` | `int` | How often the portal checks for surrounding entities (in ticks) to mirror them. |
| `checkForEntitiesEveryTick` | `false` | `boolean` | If true, the `entityCheckInterval` is forced to 1 tick. Recommended to keep `false` to optimize CPU. |
| `entityMetadataUpdateInterval` | `6` | `int` | How often entity metadata updates are synced across portals (in ticks). Higher values improve CPU and network performance, as metadata updates are relatively heavy. |
| `blockStateRefreshInterval` | `40` | `int` | How often modified block states (behind the portal) are resent to the player (in ticks). |
| `portalBlockUpdateInterval` | `20` | `int` | How often the portal will re-check blocks surrounding it (in ticks). |

---

## 👁️ Core Visual & Distance Settings

| Key | Default | Type | Description |
| :--- | :--- | :--- | :--- |
| `portalEffectSizeXZ` | `13` | `int` | **Performance Warning:** Defines the horizontal boundaries (number of blocks in the X/Z directions) of the portal render distance. Higher values significantly degrade client/server performance. |
| `portalEffectSizeY` | `7` | `int` | Defines the vertical boundaries (number of blocks in the Y direction) of the portal render distance. |
| `portalActivationDistance` | `20` | `int` | Radius (in blocks) within which the closest portal is activated and rendered. All others in range are discarded. |
| `hidePortalBlocks` | `true` | `boolean` | Hides the portal blocks (e.g. nether portal blocks) to allow players to look straight through. If set to `false`, the transition may feel less seamless if the client experience lags. |

---

## 🚪 Portal Spawning & Bounds Settings

| Key | Default | Type | Description |
| :--- | :--- | :--- | :--- |
| `maxPortalSize.x` | `5` | `int` | Maximum width (X axis) of a custom portal. *Note: Should never be larger than twice `portalEffectSizeXZ`.* |
| `maxPortalSize.y` | `5` | `int` | Maximum height (Y axis) of a custom portal. *Note: Should never be larger than twice `portalEffectSizeY`.* |
| `minimumPortalSpawnDistance` | `6` | `int` | Minimum distance allowed between newly spawned portals (in blocks). |
| `portalCollisionBox.x` / `y` / `z` | `0.50` | `double` | Collision offsets used when rendering raycasts. Recommended to leave at defaults. |
| `disabledWorlds` | `['some_world']` | `List` | List of world names where the plugin will fall back to standard vanilla portal behavior. |
| `waitTimeAfterSwitchingWorlds` | `1` | `int` | Delay (in seconds) before starting portal rendering after a player switches worlds, mitigating loading lag. |
| `teleportCooldown` | `2` | `int` | Cooldown (in seconds) before a player can teleport back through a portal. Set to `0` to disable. |
| `portalSaveInterval` | `6000` | `int` | Auto-save interval for custom portals (in ticks). Set to `-1` to disable. |
| `preventDuplicatePortals` | `true` | `boolean` | Prevents registering multiple portals with identical coordinates. |
| `enableDefaultWorldConnections` | `true` | `boolean` | Enables default links between the overworld and nether dimensions if they exist. |
| `lightBlockInterval` | `-1` | `int` | Interval for replacing air with light blocks at portal destinations to prevent pitch darkness (for 1.18+). Set to `-1` or `0` to disable. |
| `forceLightLevel` | `-1` | `int` | Forces a specific light level at portal destinations. `-1` calculates it automatically based on time/dimension. |

---

## 🔗 Custom World Linkages (`worldConnections`)
You can configure custom linkages and coordinate scaling between specific worlds. For example:
```yaml
worldConnections:
  '0':
    originWorld: 'world'
    destinationWorld: 'world_nether'
    minSpawnY: 5                    # Minimum Y coordinate for portal destination (empty for auto)
    maxSpawnY: 122                  # Maximum Y coordinate for portal destination
    coordinateRescalingFactor: 0.125 # Coordinate division factor (e.g. 8:1 ratio)
  '1':
    originWorld: 'world_nether'
    destinationWorld: 'world'
    minSpawnY: 5
    maxSpawnY: 250
    coordinateRescalingFactor: 8.0  # Coordinate multiplication factor (e.g. 1:8 ratio)
```

---

## 🎨 Aesthetic Customizations

### 1. Dimension Blend
Swaps blocks randomly between the origin and destination dimensions at the portal boundaries to create a smooth, blended transition.
* `enable` (Default: `false`): Enables or disables blending.
* `fallOffRate` (Default: `0.15`): Controls how quickly the blending fades out away from the portal. Lower values stretch the blending zone further.

### 2. Background Blocks
Allows custom background rendering blocks for specific worlds, shown when the portal view is out of bounds or loading.
```yaml
worldBackgroundBlocks:
  'my_custom_world': COBBLESTONE # Default is WHITE_CONCRETE for Overworld, RED_CONCRETE for Nether, BLACK_CONCRETE for End
backgroundBlock: ''             # Material name (e.g. OBSIDIAN) to override backgrounds globally. Keep empty for default.
```

### 3. Portal Wand
* `portalWandName` (Default: `&aPortal Wand`): The display name of the item received when running `/bp wand`.

### 4. Ambient Portal Effects & Sound Presets (`portalEffects`)
You can define multiple themes of sounds and particles that can be applied to custom portals via `/bp setpreset <preset>` or the Admin GUI.

```yaml
portalEffects:
  default:
    particle:
      type: PORTAL                     # Bukkit Particle enum
      count: 3                         # Particles spawned per tick
      speed: 0.05                      # Speed of particles
    sound:
      type: BLOCK_PORTAL_AMBIENT       # Bukkit Sound enum
      volume: 0.15                     # Ambient volume
      pitch: 1.0                       # Sound pitch
      interval: 80                     # Ticks between sound loops
  sparkles:
    particle:
      type: TRIAL_SPAWNER_DETECTION
      count: 5
      speed: 0.02
    sound:
      type: BLOCK_AMETHYST_BLOCK_CHIME
      volume: 0.3
      pitch: 1.2
      interval: 40
```

---

## 🔌 Proxy (Velocity / Bungee) Configurations
Configure communication variables under the `proxy:` section if running cross-server portals:

* `enableProxy` (Default: `false`): Set to `true` to enable proxy networking.
* `proxyAddress` (Default: `""`): The IP address of the Velocity or BungeeCord proxy.
* `proxyPort` (Default: `25510`): The communications port (matches `serverPort` on the proxy).
* `key` (Default: `""`): Encryption key matching the UUID in the proxy's BetterPortals configuration.
* `reconnectionDelay` (Default: `300`): Delay in ticks before attempting to reconnect to the proxy if disconnected. Set to `-1` to disable auto-reconnection.
* `serverName` (Default: `""`): The name of this backend server in the proxy's server registry (e.g., `lobby`, `survival`).
* `warnOnMissingSelection` (Default: `true`): Warns players in chat if their active selection is lost when transferring servers.
* `keepAlive` (Default: `true`): Enables TCP keep-alive on connection sockets.
