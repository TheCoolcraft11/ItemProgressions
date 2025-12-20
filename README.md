ItemProgressions
=================

A Minecraft Bukkit/Paper plugin to "lock" items and dimensions until a condition is met (realtime, per-player playtime,
global/server playtime). While locked, the plugin blocks using/placing/crafting/consuming and can also block entering
dimensions. Locked items get a lock lore line; advancements can show progress.

# Features

- Lock items and dimensions until realtime, per-player playtime, or global/server playtime criteria are met.
- Block use/place/craft/consume of locked items; optionally allow breaking locked blocks via config.
- Block entering locked dimensions (same logic as items).
- Optional advancement tab with rule + per-item advancements; multiple layout modes (none, columns, square, auto) with
  dummy nodes for visibility.
- Configurable icons/names/description templates for advancements; ability to disable advancement creation entirely.

# How to use

1. Download the jar file and place it in your `plugins/` folder.
2. Restart the Server.
3. Edit `config.yml` (common keys):
    - `locks`: list of rules; each has `items` and `unlock` (type=`realtime|perPlayer|global`; use `at` for timestamps
      or `seconds` for playtime). Optional per-rule `icon` and `name` apply to the rule advancement.
    - `allowBreaking`: true lets players break locked blocks.
    - `disableAdvancementCreation`: true skips creating any custom advancements.
    - `advancementGridMode`: layout for per-item advancements (`none`, `columns`, `square`, `auto`).
    - `itemAdvancements.defaults`: description templates and default icon. Placeholders: `%name%`, `%time%`,
      `%playtime%`, `%servertime%`.

# Config keys (detailed)

- `allowBreaking`: when true, players may break locked blocks; other lock checks still apply.
- `disableAdvancementCreation`: when true, the plugin does not register custom advancements.
- `advancementGridMode`: layout for per-item advancements; choose `none` (all under rule), `columns` (fixed-width
  columns), `square` (square-ish grid with dummies), or `auto` (best-fit grid with dummies per row for visibility).
- `itemAdvancements.tab`: custom tab info; set `key`, `title`, `description`, `icon`, and `background`.
- `itemAdvancements.defaults`: description templates and a fallback icon; placeholders: `%name%`, `%time%`,
  `%playtime%`, `%servertime%`.
- `locks`: list of lock rules:
    - `items`: material names/patterns (e.g., `"DIAMOND_*"`). Only item materials are used for per-item advancements;
      block-only materials are skipped.
    - `unlock.type`: `realtime`, `perPlayer`, or `global`.
    - `unlock.at`: ISO-8601 timestamp for `realtime` locks.
    - `unlock.seconds`: duration in seconds for `perPlayer` or `global` locks.
    - `unlock.icon`: optional rule-advancement icon (namespaced item id). If empty, defaults to a matched item.
    - `unlock.name`: optional rule-advancement title. If empty, the item’s translated name is used.

# Examples

Minimal config with a realtime lock and a per-player lock:

```yaml
allowBreaking: false
disableAdvancementCreation: false
advancementGridMode: auto

itemAdvancements:
  tab:
    key: "itemprogressions:locked_items"
    title: "Locked Items"
    description: "Items managed by ItemProgressions"
    icon: "minecraft:barrier"
    background: "minecraft:textures/gui/advancement/backgrounds/adventure.png"
  defaults:
    realtimeDescription: "%name% can be used at %time%"
    perPlayerDescription: "%name% can be used after %playtime%"
    globalDescription: "%name% can be used after %servertime%"
    icon: "minecraft:barrier"

locks:
  - items: [ "DIAMOND_*" ]
    unlock:
      type: perPlayer
      seconds: 3600
      icon: "minecraft:diamond"
      name: "Diamond Gear"

  - items: [ "NETHERITE_*" ]
    unlock:
      type: realtime
      at: "2026-01-01T00:00:00Z"
      icon: "minecraft:netherite_ingot"
      name: "Netherite Items"
```
