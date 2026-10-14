# Ages of Elysium

Developer architecture, API rationale, data flow, extension points, and test guidance are documented in [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md).

A NeoForge 1.21.1 progression modpack where the server advances the whole world through named ages. Mods that provide later-age resources stay installed and their world generation stays enabled from the start; age rules control whether players can see, obtain, craft, or use those resources.

## Agreed behavior

- The server has one authoritative current age ID for the world. An operator can inspect or change it with `/age get` and `/age set <age_id>`.
- The age is saved with the world and synchronized to connected clients. Changing age does not require restarting the server or regenerating chunks.
- All selected content mods stay installed, and their normal worldgen remains active unless an individual structure is explicitly listed in `unlock_structure_ids`. Do not gate a whole mod by disabling it per age; that risks inconsistent existing/new chunks. Explicit structure gates suppress new starts before unlock and use retro-generation for eligible old terrain.
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
6. Retro-generation that optionally checks Open Parties and Claims for every chunk intersecting a structure's footprint and skips claimed candidates when OPAC is installed.

The scheduler queues loaded chunks within eight chunks of online players when the age changes, then discovers other chunks lazily on chunk load. It processes up to four tasks per server tick without force-loading chunks. Pending work and completed handler/chunk checks are saved with the world. A candidate skipped because it intersects a claim remains eligible for reconsideration on a later chunk load or age transition. When Open Parties and Claims is installed, claims are checked immediately before placement because ownership can change while a candidate is queued; without OPAC, claim checks are skipped.

Structures listed in `unlock_structure_ids` are matched automatically to registered structure sets. Standard Minecraft structure sets can be age-gated and retro-generated without mod-specific registration code; generation uses each set's placement and weighted structure entries, respects claims, waits for all footprint chunks to already be loaded, and records vanilla starts/references. Mods with nonstandard placement or generation behavior can still provide a custom `RetroStructureHandler`.

Use a recipe scripting mod such as KubeJS for pack-specific recipe edits where it fits the selected NeoForge build. JEI is optional, but if included its age-aware integration is part of the feature rather than an assumed default.

## Proposed progression

The editable starting list is in [pack/ages.json](pack/ages.json). Empty mod lists are intentional placeholders, and the list can grow or change during development.

| Age | Initial content mods |
| --- | --- |
| Stone Age | Ecliptic Seasons, Overgeared, Spawn |
| Bronze Age | None selected yet |
| Medieval Age | Twilight Forest, Waystones, SkyVillages |
| Steam Age | Create, Create Aeronautics, Create: Copycats, Create Crafts & Additions, Create Deco, Create Diesel Generators, The Aether |
| Modern Age | Applied Energistics 2 |
| Atomic Age | None selected yet |

Waystones is intended to be installed from the beginning but unusable before Medieval Age. The runtime catalog locks the `waystones` namespace until Medieval Age. The installed Create addons are grouped with Create in Steam Age, and their namespaces are locked alongside `create`. Spawn adds world fauna and is grouped with the starting Stone Age. Lootr is a multiplayer loot utility, while Advancement Plaques, Jade, JEI, and the other interface/library jars are support or quality-of-life mods; they remain available throughout the ages rather than being progression-gated. Mod names in the planning manifest are labels; where namespace locks are used, the runtime IDs are listed in the age JSON. Compatibility should still be checked when changing mod versions.

## Configuring Ages

Age definitions are server datapack resources. The mod reads every JSON resource under `data/<namespace>/ages/`; its built-in definitions are in [src/main/resources/data/agesofelysium/ages](src/main/resources/data/agesofelysium/ages). Put configuration in a normal datapack under the world's `datapacks` directory, or at the root of a datapack ZIP. Run `/reload` after changing the datapack so the server rebuilds the catalog.

Each definition currently supports these fields:

| Field | Required | Meaning |
| --- | --- | --- |
| `id` | Yes | Stable lowercase ID used by commands and saved worlds, such as `medieval`. |
| `name` | Yes | Display name shown to players, such as `Medieval Age`. |
| `order` | Yes | Nonnegative progression position. IDs and order values must each be unique. |
| `locked_mod_ids` | No | Mod namespaces that become usable at this age. Omit it or use `[]` for no namespace unlocks. |
| `wild_spawn_mod_ids` | No | Mod namespaces allowed to naturally spawn during this age. A namespace listed in several ages can spawn only during those ages. |
| `wild_spawn_entity_ids` | No | Exact entity IDs allowed to spawn during this age. Any namespace with entity-specific rules becomes an allowlist: unlisted entities from that namespace are blocked. |
| `unlock_recipe_ids` | No | Recipe IDs that become available at this age, such as `minecraft:diamond_pickaxe`. This overrides the owning mod's namespace lock for that recipe. |
| `unlock_item_ids` | No | Item IDs usable and visible at this age despite their mod namespace remaining locked. Use for outputs of early recipe exceptions. |
| `unlock_structure_ids` | No | Worldgen structure IDs that begin generating in new chunks and become eligible for retro-generation at this age. |
| `unlock_placed_feature_ids` | No | Placed-feature IDs blocked from generating in new chunks until this age. |
| `retro_generate_placed_features` | No | Opt-in retro rules with `id`, `radius_chunks` (0-8), and `dimensions`. IDs must also be unlocked in the same age. An empty dimensions list allows any dimension. |
| `unlock_dimension_ids` | No | Dimension IDs players cannot enter until this age. |

The age with the lowest `order` is the default for new worlds and for saved ages that are no longer present in the catalog. Orders must be unique and nonnegative, but need not start at zero. A namespace may be assigned to only one `locked_mod_ids` age. Recipe and item IDs may each be assigned to only one unlock age. Namespaces not listed in any `locked_mod_ids` field are treated as unlocked at all ages. Use the actual lowercase mod ID, not the mod's display name.

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

`wild_spawn_mod_ids` controls natural and chunk-generation spawn attempts by entity namespace. The shipped Bronze and Medieval definitions list `iceandfire`, allowing Ice and Fire mobs to spawn naturally in those ages only. Separately, Bronze lists `iceandfire` in `locked_mod_ids`, so Ice and Fire items remain locked in Stone and unlock in Bronze. To allow only selected species from a namespace, list their full entity IDs under `wild_spawn_entity_ids` in each allowed age; once any entity-specific rule exists for a namespace, every unlisted species in that namespace is blocked. For example, listing `jurassicreborn:tyrannosaurus` in Bronze allows that species in Bronze, while other Jurassic Reborn entities stay blocked unless separately listed. These rules do not remove mobs already in the world and do not block spawners, spawn eggs, commands, breeding, or other non-natural spawn types.

`unlock_structure_ids` uses full structure IDs. Before its listed age, the mod's worldgen mixin prevents a configured structure from starting in newly generated chunks. The generic retro-generation handler discovers registered structure sets containing configured IDs, then reconstructs eligible starts when the structure unlocks. It waits until every chunk intersecting the complete structure footprint is already loaded and skips claimed or already-occupied footprints; it never force-loads chunks. Mods that bypass Minecraft's normal structure-set and `Structure.generate` path need a custom handler.

`unlock_placed_feature_ids` uses full placed-feature IDs, such as `iceandfire:spawn_dragon_skeleton_fire`. Configured placed features are skipped before their unlock age when Minecraft generates new chunks. To retrofit a bounded, repeatable feature into eligible existing chunks, also add an entry under `retro_generate_placed_features`. The scheduler only processes already-loaded chunks, checks claims when OPAC is installed, and never force-loads the feature's declared radius. Features without an opt-in entry remain new-chunk-only.

For example, Bronze opts Create's striated ores into retro-generation in their respective dimensions; Medieval opts Waystones in with radius zero and lets Waystones apply its own dimension settings. YUNG placed-feature structures are gated at Bronze but are not retro-generated yet because they need dedicated footprint handlers. Aether's placed features are not individually listed because the whole `aether:the_aether` dimension unlocks in Steam. `unlock_dimension_ids` blocks player travel into a dimension before its assigned age and returns players to the Overworld if they log in there or remain there after an age change or datapack reload.

To override a built-in age, create a higher-priority datapack resource at the same path. For example, this overrides Medieval Age while keeping Waystones locked until then:

```text
data/agesofelysium/ages/medieval.json
```

```json
{
	"id": "medieval",
	"name": "Medieval Age",
	"order": 2,
	"locked_mod_ids": ["waystones"]
}
```
| `unlock_recipe_ids` | No | Recipe IDs that become available at this age, such as `minecraft:diamond_pickaxe`. This overrides the owning mod's namespace lock for that recipe. |
| `unlock_structure_ids` | No | Worldgen structure IDs that begin generating in new chunks and become eligible for retro-generation at this age. |
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

The catalog is validated as a whole on reload. Invalid IDs, blank names, negative or duplicate orders, duplicate IDs/names, an empty catalog, or a mod namespace assigned more than once reject the new catalog; the last valid catalog remains active. Check the server log if a datapack change does not take effect.

**Configuration coverage today:** age names, progression order, whole-mod-namespace unlock ages, wild-spawn age windows, per-recipe unlock ages, and per-item unlock exceptions are data-driven. The namespace gate blocks player breaking, placement, and right-click use of mod blocks/items before unlock, except explicitly listed `unlock_item_ids`. Wild-spawn rules reject only natural and chunk-generation spawn checks; loaded entities remain, and non-natural spawn sources are unaffected. Right-click locks are checked on both client and server to prevent predicted placement/use from temporarily consuming a stack; the server remains authoritative. Per-recipe rules filter both normal recipe lookups and direct `RecipeManager.getAllRecipesFor` scans, including Overgeared forging recipes. When JEI is installed, locked recipes and outputs are hidden while recipe exceptions remain visible; item exceptions can also be shown. A soft integration also filters Create sequenced-assembly recipe scans. These gates prevent new recipe selection while locked; a machine operation already in progress when an age changes may finish. Drop suppression, integrations for mods that bypass `RecipeManager` or use private caches, and visual world-block hiding are not implemented yet. `pack/ages.json` remains planning data for content-mod assignments and is not loaded at runtime.

## Prototype milestones

1. Confirm the age names, transition permissions, and initial content mods.
2. Define age resource rules and add one test resource tag; validate worldgen, locked mining, and unlock behavior in a fresh world and an existing chunk.
1. Confirm the age names, transition permissions, and initial content mods.
4. Add invisible rendering and drop suppression, then test the complete pack on a dedicated server and clients.

## Current scaffold
4. Add invisible rendering and drop suppression, then test the complete pack on a dedicated server and clients.
5. Test SkyVillages in a fresh and existing world: no starts before Medieval, retro-placement after unlock, and claimed footprints skipped.
The custom mod loads its age catalog from datapack resources and persists the current age ID. The lowest-order age is used as the default. It provides:
Open Parties and Claims 0.32.7+ is optional and adds claim protection to retro-generation. Without it, the rest of Ages of Elysium still works and eligible retro-generation proceeds without claim checks. `ClaimProtection` checks the full candidate chunk rectangle through OPAC's server API when available. This is claim-based protection only: unclaimed player builds are intentionally not tracked or protected by this rule.
- `/age get` to read the current display name.
- `/age set <age_id>` to set a known age, with tab completion; this requires permission level 2.
- `gradlew.bat build` to build the mod on Windows (Java 21 required).

The custom mod also includes generic structure-set discovery and claim-safe retro-generation. Any registered structure referenced by `unlock_structure_ids` is considered automatically when its containing structure set uses the standard Minecraft generation API. The scheduler checks loaded chunks near players on age changes and checks other chunks as they load. Mods with custom generation paths still need a dedicated handler. Before their unlock ages, the server blocks player interactions with locked namespaces: Waystones before Medieval; Create and its bundled Aeronautics namespaces (`aeronautics_bundled`, `aeronautics`, `offroad`, `simulated`) before Steam; and AE2 before Atomic. Create Diesel Generators is assigned to Modern. Recipe IDs in `unlock_recipe_ids` are rejected server-side by recipe lookups. JEI dynamically hides categories, items, and fluids owned by locked namespaces, explicitly locked recipes, and their output ingredients, then restores them on unlock. Create's standard processing lookups and sequenced assembly search have an integration; other mod-specific production paths, commands, and work already in progress may bypass or complete despite a lock. Invisible rendering and drop rules are not implemented yet. `pack/ages.json` remains the planning manifest for mod assignments; runtime age IDs and rules live in the datapack definitions.

The optional OPAC integration checks the full candidate chunk rectangle through its server API; retro-generation skips the candidate when any chunk is claimed. Without OPAC, this check returns false. This protection does not track unclaimed player builds.

## Open pack decisions

- Whether transitions can move backward.
- Which operators or permissions may change the age.
- Initial content mods and which resources belong to each age.
- Whether locked items already in player inventories remain usable, or are also blocked.
