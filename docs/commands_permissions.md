# BetterPortals Commands & Permissions

This guide documents all the commands and permissions available in BetterPortals, including the interactive in-game Admin GUI.

---

## 🎮 Command Reference

The main plugin command is `/betterportals` (aliased as `/bp`).

### General Commands

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/bp reload` | `betterportals.reload` | Reloads the configuration file, lang files, and soft-restarts the plugin. |
| `/bp reconnect` | `betterportals.reconnect` | Prematurely attempts to reconnect to the proxy if the connection was lost. |
| `/bp wand` | `betterportals.wand` | Gives the player a **Portal Wand** (wooden axe by default) for selecting portal corners. |
| `/bp menu` <br>*(Aliases: `gui`, `list`, `admin`)* | `betterportals.select` | Opens the interactive Portal Admin GUI menu. |

### Portal Editing & Management

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/bp origin` <br>*(Alias: `/bp setOrigin`)* | `betterportals.select` | Sets the current wand selection coordinates as the portal's origin. |
| `/bp destination` <br>*(Aliases: `/bp dest`, `/bp setDestination`)* | `betterportals.select` | Sets the current wand selection coordinates as the portal's destination. |
| `/bp link` <br>*(Alias: `/bp linkPortals`)* | `betterportals.link` | Links the selected origin and destination portals together.<br>**Arguments:** `[twoWay?: true/false]` `[invert?: true/false]` |
| `/bp linkexternal` <br>*(Alias: `/bp linkExternalPortals`)* | `betterportals.linkexternal` | Links the origin selection on this server to a destination on another server.<br>**Arguments:** `[invert?: true/false]` |
| `/bp remove` <br>*(Aliases: `/bp delete`, `/bp del`)* | `betterportals.remove` | Removes the nearest custom portal within 20 blocks. Players can only remove their own portals unless they have bypass permissions.<br>**Arguments:** `[removeDestination?: true/false]` *(if true, deletes destination portal too; default `true`)* |
| `/bp removebyname <name>` <br>*(Alias: `/bp deletename`)* | `betterportals.remove` | Deletes all custom portals with the given name. |
| `/bp createfromcoords <originWorld> <originC1> <originC2> <destWorld> <destC1> <destC2>` | `betterportals.createfromcoords` | Creates a custom portal from coordinates (Vectors) directly. Useful for console setups.<br>**Arguments:** `<originWorld> <originC1> <originC2> <destWorld> <destC1> <destC2> [twoWay?: true/false] [invert?: true/false] [name]` |

### Portal Property Configuration

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/bp setname <newName>` <br>*(Alias: `/bp setPortalName`)* | `betterportals.setname` | Sets a custom name for the closest portal within 20 blocks. |
| `/bp getname` <br>*(Alias: `/bp getportalname`)* | `betterportals.getname` | Tells you the name of the closest portal within 20 blocks. |
| `/bp setprice <price>` | `betterportals.setname` | Sets a Vault entry fee (double) for the closest portal within 20 blocks. |
| `/bp setpreset <preset>` | `betterportals.setname` | Applies an effect theme/preset to the closest portal within 20 blocks. |
| `/bp setcanteleportmobs <true/false>` <br>*(Alias: `/bp setAllowNonPlayerTeleportation`)* | `betterportals.setAllowNonPlayerTeleportation` | Configures if items, mobs, and projectiles can teleport through the closest portal. |
| `/bp getcanteleportmobs` <br>*(Alias: `/bp getallowNonPlayerTeleportation`)* | `betterportals.getallowNonPlayerTeleportation` | Checks if the closest portal allows item/mob teleportation. |

### Player Preferences

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/bp setenablebpview <true/false>` <br>*(Alias: `/bp setseethroughportal`)* | `betterportals.see` | Sets whether the player can see through portals or see standard vanilla portals. |
| `/bp togglevanillaview` <br>*(Alias: `/bp toggleseethroughportal`)* | `betterportals.see` | Toggles the see-through portal view preference for the command sender. |

---

## 🖥️ Interactive Portal Admin GUI (`/bp gui`)

The Portal Admin GUI provides an intuitive inventory-based panel for managing custom portals without needing to remember commands. It adapts to the player's client language.

```
+---------------------------------------------------------+
|                  Portals List (Page 1)                  |
|                                                         |
|  [Custom Portal]  [Custom Portal]  [Dinnerbone]  ...    |
|  (ID: 1, $0.00)   (ID: 2, $5.00)   (ID: 3, Preset: fire) |
|                                                         |
|  [<< Previous]                     [Back]   [Next >>]   |
+---------------------------------------------------------+
```

### 1. Portal List Menu
When you open `/bp gui` (or `/bp menu`), you are presented with a paginated list of all custom portals registered on the server.
* **Portal Items:** Each portal is represented by a book. Hovering over a book shows a detailed tooltip:
  * **ID:** Unique internal UUID.
  * **Origin:** The world and coordinates of the portal's origin.
  * **Price:** The current Vault entry fee.
  * **Preset:** The applied visual and audio theme.
* **Navigation:** Arrow buttons (`Previous Page` / `Next Page`) are displayed at the bottom of the screen to browse through portals if there are more than 45 portals.
* **Action:** Left-clicking a portal opens the **Portal Editor Menu** for that portal.

---

### 2. Portal Editor Menu
This menu lets you edit the selected portal's configuration interactively:

```
+---------------------------------------------------------+
|                   Portal Editor Menu                    |
|                                                         |
|  [Compass]   [Hopper]   [RedWool] [GoldBlock] [LimeWool] |
|  Teleport    Mob/Item    -Price    $ Price     +Price   |
|                                                         |
|  [BlazePowder]          [NoteBlock]             [Barrier]|
|   Presets                ToggleSound             Delete  |
|                                                         |
|                       [Arrow: Back]                     |
+---------------------------------------------------------+
```

* 🧭 **Teleport to Origin (`Compass`):** Instantly teleports you to the portal's origin coordinates.
* 📥 **Item/Mob Teleportation (`Hopper`):** Click to toggle whether items, mobs, and projectiles can pass through the portal. Shows `Enabled` (Green) or `Disabled` (Red) status.
* 🪙 **Price Adjuster (`Gold Block` + `Wool`):**
  * 🟥 **Decrease Price (`Red Wool`):** Left-click to decrease the entrance fee by **$1.00**, or right-click to decrease by **$10.00**.
  * 🟨 **Current Price (`Gold Block`):** Shows the current price. Click this block to reset the price to **$0.00** instantly.
  * 🟩 **Increase Price (`Lime Wool`):** Left-click to increase the entrance fee by **$1.00**, or right-click to increase by **$10.00**.
* 🧪 **Effect Presets (`Blaze Powder`):** Click to open the **Portal Effects Menu** to change the visual theme of the portal.
* 🎵 **Sound Effects (`Note Block`):** Toggles the portal's ambient sound effects on or off.
* ❌ **Delete Portal (`Barrier`):** Permanently deletes this portal from the server.
* ⬅️ **Back to List (`Arrow`):** Returns you to the list of portals.

---

### 3. Portal Effects Menu
This menu lists all portal ambient audio-visual presets configured in the `portalEffects` section of `config.yml`.
* **Preset Items:** Each preset shows its name, particles count/speed, and sound volume/pitch/interval in a detailed tooltip.
* **Active Status:**
  * The currently active preset is marked with a green indicator: `● <PresetName> (Active)`.
  * Inactive presets are marked with a gray indicator: `○ <PresetName>`.
* **Action:** Click any preset to apply it to the portal immediately.
* ⬅️ **Back to Editor (`Arrow`):** Returns you to the main Portal Editor.
