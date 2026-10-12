# Minecraft Ages Prototype

A NeoForge 1.21.1 progression modpack where the server advances the whole world through named ages. Mods that provide later-age resources stay installed and their world generation stays enabled from the start; age rules control whether players can see, obtain, craft, or use those resources.

## Agreed behavior

- The server has one authoritative current age ID for the world. An operator can inspect or change it with `/age get` and `/age set <age_id>`.
- The age is saved with the world and synchronized to connected clients. Changing age does not require restarting the server or regenerating chunks.
- All selected content mods and their world generation are active from world creation. Do not gate a mod by disabling it per age: that would prevent its features from generating reliably in new chunks and risks inconsistent existing chunks.
- Resources are assigned to ages using data tags, rather than hard-coding individual mod IDs into the age system.
- Before a resource's age, its recipes are unavailable. Targeted ores can be invisible client-side and unbreakable server-side. Blocks that should remain visible can instead be unbreakable or have their drops suppressed.
- Access checks are enforced by the server. Client rendering and recipe-viewer hiding are presentation layers, not security or progression enforcement.

## Implementation outline

This behavior needs a small custom mod alongside the content mods. A normal datapack or recipe script can gate recipes and drops, but cannot by itself make arbitrary third-party blocks render invisibly in existing chunks while keeping their server-side world generation intact.

The custom mod should provide:

1. Persistent world age state, an operator command, and server-to-client synchronization.
2. Data-driven age definitions and tags for resources, with rules for hidden, unbreakable, and drop-suppressed blocks.
3. Server-side checks for block breaking, drops, crafting, and any other configured acquisition route.
4. Client-side rendering that hides tagged blocks when their age is locked, then refreshes affected chunk renders when the server changes age.
5. A JEI integration that updates hidden items and recipes when the age changes. Static recipe-hiding scripts alone are not sufficient for a live server transition.
6. Retro-generation that checks Open Parties and Claims for every chunk intersecting a structure's footprint and skips the candidate if any are claimed.

The scheduler queues loaded chunks within eight chunks of online players when the age changes, then discovers other chunks lazily on chunk load. It processes up to four tasks per server tick without force-loading chunks. Pending work and completed handler/chunk checks are saved with the world. A candidate skipped because it intersects a claim remains eligible for reconsideration on a later chunk load or age transition. Claims are checked immediately before placement because ownership can change while a candidate is queued.

Structure-specific integrations register a `RetroStructureHandler` during mod startup. Each handler decides whether it is unlocked at the current age, identifies a candidate and its complete chunk footprint, and provides the placement operation. The scheduler rechecks claims over that footprint immediately before invoking placement. Handlers must avoid duplicating structures that normal world generation has already placed and must use placement logic appropriate to the source mod. No structure handlers are registered yet, so the scheduler currently has no structures to place.

Use a recipe scripting mod such as KubeJS for pack-specific recipe edits where it fits the selected NeoForge build. JEI is optional, but if included its age-aware integration is part of the feature rather than an assumed default.

## Proposed progression

The editable starting list is in [pack/ages.json](pack/ages.json). Empty mod lists are intentional placeholders, and the list can grow or change during development.

| Age | Initial content mods |
| --- | --- |
| Stone Age | Ecliptic Seasons, Overgeared, Spawn |
| Bronze Age | None selected yet |
| Medieval Age | Twilight Forest, Waystones |
| Steam Age | Create, Create Aeronautics, Create: Copycats, Create Crafts & Additions, Create Deco, Create Diesel Generators, The Aether |
| Modern Age | Applied Energistics 2 |
| Atomic Age | None selected yet |

Waystones is intended to be installed from the beginning but unusable before Medieval Age. The runtime catalog locks the `waystones` namespace until Medieval Age. The installed Create addons are grouped with Create in Steam Age, and their namespaces are locked alongside `create`. Spawn adds world fauna and is grouped with the starting Stone Age. Lootr is a multiplayer loot utility, while Advancement Plaques, Jade, JEI, and the other interface/library jars are support or quality-of-life mods; they remain available throughout the ages rather than being progression-gated. Mod names in the planning manifest are labels; where namespace locks are used, the runtime IDs are listed in the age JSON. Compatibility should still be checked when changing mod versions.

## Configuring Ages

Age definitions are server datapack resources. The mod reads every JSON resource under `data/<namespace>/ages/`; its built-in definitions are in [src/main/resources/data/minecraftages/ages](src/main/resources/data/minecraftages/ages). Put configuration in a normal datapack under the world's `datapacks` directory, or at the root of a datapack ZIP. Run `/reload` after changing the datapack so the server rebuilds the catalog.

Each definition currently supports these fields:

| Field | Required | Meaning |
| --- | --- | --- |
| `id` | Yes | Stable lowercase ID used by commands and saved worlds, such as `medieval`. |
| `name` | Yes | Display name shown to players, such as `Medieval Age`. |
| `order` | Yes | Nonnegative progression position. IDs and order values must each be unique. |
| `locked_mod_ids` | No | Mod namespaces that become usable at this age. Omit it or use `[]` for no namespace unlocks. |
| `wild_spawn_mod_ids` | No | Mod namespaces allowed to naturally spawn during this age. A namespace listed in several ages can spawn only during those ages. |
| `unlock_recipe_ids` | No | Recipe IDs that become available at this age, such as `minecraft:diamond_pickaxe`. This overrides the owning mod's namespace lock for that recipe. |
| `unlock_item_ids` | No | Item IDs usable and visible at this age despite their mod namespace remaining locked. Use for outputs of early recipe exceptions. |

`stone` must exist and have order `0`. A namespace may be assigned to only one `locked_mod_ids` age. Recipe and item IDs may each be assigned to only one unlock age. Namespaces not listed in any `locked_mod_ids` field are treated as unlocked at all ages. Use the actual lowercase mod ID, not the mod's display name.

`unlock_recipe_ids` uses full recipe resource IDs. The shipped Modern Age definition unlocks the vanilla diamond pickaxe recipe with `"unlock_recipe_ids": ["minecraft:diamond_pickaxe"]`; the Steam Age definition locks the Create namespace and unlocks `create:mixing/brass_ingot`. To unlock a recipe from a still-locked mod earlier, list the recipe ID under the earlier age's `unlock_recipe_ids`. If its result item should also be available, list the item ID under that age's `unlock_item_ids`; otherwise use of the mod-owned result remains locked. The rest of the mod and its recipes stay locked until its `locked_mod_ids` age. Recipe and item IDs may refer to vanilla or mod content.

For example, to make Create's brass mixing recipe available in Bronze while keeping the rest of Create locked until Steam, put this in `bronze.json` and remove `create:mixing/brass_ingot` from Steam's `unlock_recipe_ids`:

```json
{
	"id": "bronze",
	"name": "Bronze Age",
	"order": 1,
	"unlock_recipe_ids": ["create:mixing/brass_ingot"],
	"unlock_item_ids": ["create:brass_ingot"]
}
```

`wild_spawn_mod_ids` controls natural and chunk-generation spawn attempts by entity namespace. The shipped Bronze and Medieval definitions list `iceandfire`, allowing Ice and Fire mobs to spawn naturally in those ages only. In other ages their natural spawn checks fail. This does not remove mobs already in the world, and it does not block spawners, spawn eggs, commands, breeding, or other non-natural spawn types.

To override a built-in age, create a higher-priority datapack resource at the same path. For example, this overrides Medieval Age while keeping Waystones locked until then:

```text
data/minecraftages/ages/medieval.json
```

```json
{
	"id": "medieval",
	"name": "Medieval Age",
	"order": 2,
	"locked_mod_ids": ["waystones"]
}
```

To add an age, use your datapack's namespace and a new resource path. For example, `data/my_pack/ages/copper.json`:

```json
{
	"id": "copper",
	"name": "Copper Age",
	"order": 1,
	"locked_mod_ids": []
}
```

The resource path namespace (`my_pack`) is separate from the JSON age `id` (`copper`). Adding an age requires assigning it an unused order and shifting other ages if it belongs between them. Override the affected age resources in the same datapack so the resulting catalog has no order collisions.

Use stable IDs in commands: `/age set medieval`. `/age get` displays the configured name. Keep IDs stable when editing names; saved worlds store IDs, not display names. Earlier prototype saves containing a display name such as `Stone Age` are migrated when possible. Before removing or renaming an age ID that is currently active in a world, change that world's age to an ID you will retain.

The catalog is validated as a whole on reload. Invalid IDs, blank names, negative or duplicate orders, duplicate IDs/names, an absent or nonzero-order `stone`, or a mod namespace assigned more than once reject the new catalog; the last valid catalog remains active. Check the server log if a datapack change does not take effect.

**Configuration coverage today:** age names, progression order, whole-mod-namespace unlock ages, wild-spawn age windows, per-recipe unlock ages, and per-item unlock exceptions are data-driven. The namespace gate blocks player breaking, placement, and right-click use of mod blocks/items before unlock, except explicitly listed `unlock_item_ids`. Wild-spawn rules reject only natural and chunk-generation spawn checks; loaded entities remain, and non-natural spawn sources are unaffected. Right-click locks are checked on both client and server to prevent predicted placement/use from temporarily consuming a stack; the server remains authoritative. Per-recipe rules override namespace locks in server recipe checks and the vanilla recipe book. When JEI is installed, locked recipes and outputs are hidden while recipe exceptions remain visible; item exceptions can also be shown. A soft integration also filters Create sequenced-assembly recipe scans. These gates prevent new recipe selection while locked; a machine operation already in progress when an age changes may finish. Drop suppression, integrations for other mods' private recipe paths, and visual world-block hiding are not implemented yet. `pack/ages.json` remains planning data for content-mod assignments and is not loaded at runtime.

## Prototype milestones

1. Confirm the age names, transition permissions, and initial content mods.
2. Define age resource rules and add one test resource tag; validate worldgen, locked mining, and unlock behavior in a fresh world and an existing chunk.
3. Expand recipe and item rules to the chosen progression, including machine-specific automation integrations.
4. Add invisible rendering and drop suppression, then test the complete pack on a dedicated server and clients.

## Current scaffold

The custom mod currently loads the six-age runtime catalog and persists the current age ID (default `stone`). It provides:

- `/age get` to read the current display name.
- `/age set <age_id>` to set a known age, with tab completion; this requires permission level 2.
- `gradlew.bat build` to build the mod on Windows (Java 21 required).

The custom mod also includes the retro-generation scheduler and OPAC footprint protection described above. Before its unlock age, the server blocks player breaking and placement of blocks from a locked namespace, plus right-click interactions with those blocks and items. Right-click actions are rejected client-side as well to prevent inventory prediction glitches. This currently gates Waystones before Medieval Age, Create and its bundled Aeronautics namespaces (`aeronautics_bundled`, `aeronautics`, `offroad`, `simulated`) before Steam Age, and AE2 before Atomic Age. Create Diesel Generators is assigned to Modern Age. Recipe IDs in `unlock_recipe_ids` are rejected server-side by recipe lookups. JEI dynamically hides categories, items, and fluids owned by locked namespaces, explicitly locked recipes, and their output ingredients, then restores them on unlock. Create's standard processing lookups and sequenced assembly search have an integration; other mod-specific production paths, commands, and work already in progress may bypass or complete despite a lock. Invisible rendering, drop rules, and structure-specific handlers are not implemented yet. `pack/ages.json` remains the planning manifest for mod assignments; runtime age IDs, order, namespace locks, and recipe unlocks live in the datapack definitions.

The mod now depends on Open Parties and Claims 0.32.7+ for future structure protection. `ClaimProtection` checks the full candidate chunk rectangle through OPAC's server API; retro-generation must call it immediately before placing a structure and skip the candidate when it returns true. This is claim-based protection only: unclaimed player builds are intentionally not tracked or protected by this rule.

## Open pack decisions

- Whether transitions can move backward.
- Which operators or permissions may change the age.
- Initial content mods and which resources belong to each age.
- Whether locked items already in player inventories remain usable, or are also blocked.
