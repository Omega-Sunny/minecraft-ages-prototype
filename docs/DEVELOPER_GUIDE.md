# Ages of Elysium Developer Guide

This document explains how the current Ages mod is put together, why it uses its NeoForge, Minecraft, JEI, Open Parties and Claims, and Mixin APIs, and where to extend it. It describes the code as implemented today; it does not imply that unfinished resource-rendering or structure-placement features already exist.

## Project at a Glance

- Minecraft: 1.21.1
- Loader: NeoForge 21.1.172
- Java: 21
- Mod ID: `agesofelysium`
- Main package: `dev.agesofelysium.ages`
- Build: `./gradlew build` on Unix-like systems or `gradlew.bat build` on Windows
- Output jar: `build/libs/agesofelysium-<version>.jar`

The custom mod is server-authoritative for age state and progression enforcement. The client receives a small derived snapshot for predicted interactions and JEI presentation. The full datapack catalog is loaded and interpreted by the server.

## Source Map

| Area | Main classes/resources | Responsibility |
| --- | --- | --- |
| Mod bootstrap | `AgesMod` | NeoForge mod entry point. Most setup is attached through event subscribers. |
| Age catalog | `AgeCatalog`, `AgeCatalogReload` | Load, validate, index, and atomically replace age datapack definitions. |
| Persistent state and commands | `AgeSavedData`, `AgeCommands` | Save one current age ID per world and expose `/age get` and `/age set`. |
| Player access | `AgeAccessEvents` | Gate player mining, placement, and right-click interactions by namespace or explicit item exception. |
| Mob spawning | `AgeMobSpawnEvents` | Reject configured mod mobs at natural and chunk-generation spawn checks outside their allowed ages. |
| Recipe enforcement | `AgeRecipeBookSync`, `AgeCraftingRules`, `mixin/*` | Filter recipe lookup, the vanilla crafting result, recipe-book knowledge, and Create sequenced assembly. |
| Client synchronization and JEI | `AgeRecipeNetworking`, `AgeRecipeSyncPayload`, `ClientAgeRecipeState`, `AgePlayerSyncEvents`, `AgesJeiPlugin` | Send derived lock state to clients and update JEI when the age changes. |
| Retro-generation | `RetroGenerationScheduler`, `RetroGenerationData`, `RetroGenerationRegistry`, `StructureSetRetroGenerationHandler`, `RetroStructureHandler`, `ClaimProtection` | Discover configured structures in registered structure sets, queue old chunks, retro-place standard structures safely, and provide a custom-handler extension API. |
| Runtime data | `src/main/resources/data/agesofelysium/ages/*.json` | Built-in age order and rule examples. Datapacks can override or extend these resources. |
| Mixins | `src/main/resources/agesofelysium.mixins.json` | Register targeted hooks for recipe enforcement and configured structure worldgen gating. |
| Planning only | `pack/ages.json` | Human-readable mod assignment notes; not loaded at runtime. |

## Runtime Catalog

`AgeCatalog.ReloadListener` extends Minecraft's `SimpleJsonResourceReloadListener`. It listens beneath the `ages` resource directory, so definitions use this layout:

```text
data/<namespace>/ages/<file>.json
```

The namespace and filename identify a resource, but the JSON `id` is the stable age identifier used in saved data and commands. A minimal definition is:

```json
{
  "id": "bronze",
  "name": "Bronze Age",
  "order": 1
}
```

Supported fields:

| Field | Meaning |
| --- | --- |
| `id` | Stable, lowercase progression ID. |
| `name` | Player-facing display name. It can contain spaces and can change without changing the save ID. |
| `order` | Unique nonnegative progression index. The lowest-order age is the default; orders need not start at zero. |
| `locked_mod_ids` | Mod namespaces that unlock at this age. A namespace may have only one default unlock age. Unlisted namespaces are always unlocked by this rule. |
| `wild_spawn_mod_ids` | Namespaces whose mobs may naturally spawn during this age. A namespace listed across multiple ages is allowed only in those ages. |
| `wild_spawn_entity_ids` | Exact entity IDs allowed to spawn during this age. If any entity rules exist for a namespace, only entity IDs listed in an age are allowed; other entities in that namespace are denied. |
| `unlock_recipe_ids` | Full recipe IDs whose individual unlock age is this age. An explicit recipe assignment overrides its owning mod's namespace lock. |
| `unlock_item_ids` | Full item IDs whose individual unlock age is this age. An explicit item assignment overrides its owning mod's namespace lock for player item access and JEI visibility. |
| `unlock_structure_ids` | Full structure IDs unlocked for normal generation and retro-generation at this age. |
| `unlock_placed_feature_ids` | Full placed-feature IDs blocked from generating in new chunks until this age. |
| `retro_generate_placed_features` | Opt-in retro rules with `id`, `radius_chunks` (0-8), and `dimensions`; an empty dimension list means any dimension. Each feature must be unlocked in this same age. |
| `unlock_dimension_ids` | Dimension registry IDs that players cannot enter before this age. |

### Rule Precedence

For a recipe ID, an explicit `unlock_recipe_ids` assignment wins. Otherwise, the recipe inherits its namespace's `locked_mod_ids` age. For an item ID, an explicit `unlock_item_ids` assignment wins; otherwise, the item inherits its namespace lock. Thus a whole mod can remain locked while selected recipes and outputs are introduced earlier.

For example, keep Create locked until Steam but allow its brass mixing recipe in Bronze:

```json
{
  "id": "bronze",
  "name": "Bronze Age",
  "order": 1,
  "unlock_recipe_ids": ["create:mixing/brass_ingot"],
  "unlock_item_ids": ["create:brass_ingot"]
}
```

Remove `create:mixing/brass_ingot` from Steam's `unlock_recipe_ids` if it was there; recipe IDs and item IDs can each only be assigned to one unlock age. `unlock_item_ids` is needed if the mod's output item should be usable before the rest of its namespace unlocks.

### Validation and Reload

The catalog parser builds all maps locally, validates them, and only then publishes an immutable `Snapshot` through a `volatile` field. Readers therefore see either the last valid snapshot or the newly validated snapshot, not partially populated maps. Invalid JSON, malformed or duplicate IDs, blank names, negative or duplicate orders, an empty catalog, duplicate namespace unlock assignments, malformed recipe/item/structure/placed-feature/dimension IDs, or duplicate assignments cause the new catalog to be rejected. Retro-feature entries must have a bounded radius and match a placed feature unlocked in the same age. The reload listener logs the error and retains its previous valid snapshot.

Run `/reload` to reload datapacks. On a successful reload, the server refreshes open vanilla crafting menus, reconciles connected players' vanilla recipe books, and sends new lock state to clients. Datapack resource priority follows Minecraft's normal resource-pack rules; to override a built-in definition, use the same resource ID, such as `data/agesofelysium/ages/steam.json`, in a higher-priority pack.

## Age State and Commands

`AgeSavedData` extends Minecraft's `SavedData`. It is stored in the overworld data storage under `ages_world_state`, making the age shared by all dimensions in a world. Only the stable ID, such as `medieval`, is persisted; names and order come from the current catalog.

`DataFixTypes.LEVEL` declares the data's relationship to level save data. `setCurrentAgeId` rejects unknown IDs and calls `setDirty`, which tells Minecraft to serialize the state. New worlds use the lowest-order age after the catalog loads. Saved IDs still present in the catalog are preserved; legacy display names are resolved when possible, and removed/unknown IDs fall back to the current lowest-order age.

The `RegisterCommandsEvent` callback in `AgeCommands` builds Brigadier commands:

- `/age get` reads the saved ID and displays the catalog name.
- `/age set <age_id>` requires permission level 2, validates against the loaded catalog, and provides suggestions from it.

An actual transition performs a coordinated update: save the ID, scan nearby loaded chunks for retro-generation work, refresh open crafting results, reconcile each online player's recipe book, and send the new lock snapshot to clients. Repeating `/age set` with the current ID does not repeat this work.

## Client Synchronization

The server is authoritative. The client does not receive a second mutable world age; it receives derived lock lists used for client prediction and UI.

`AgeRecipeSyncPayload` implements Minecraft's `CustomPacketPayload`. Its registered `Type` provides a namespaced packet ID, and its `StreamCodec<RegistryFriendlyByteBuf, ...>` encodes the locked recipe IDs, locked mod namespaces, and explicitly unlocked item IDs. `RegisterPayloadHandlersEvent` registers this payload as `playToClient`; `PacketDistributor.sendToPlayer` delivers it on login, age transition, and successful datapack reload.

`ClientAgeRecipeState` stores immutable snapshots in thread-safe sets and notifies registered `Runnable` listeners. The payload handler uses `context.enqueueWork` because Minecraft client state must be updated on the game thread. `AgePlayerSyncEvents` sends initial state after login.

The payload is intentionally derived from the active catalog and current age. If adding another client feature that depends on progression, prefer extending this server-generated snapshot instead of independently parsing datapack rules on the client.

## Enforcement Paths

### Player Block and Item Access

`AgeAccessEvents` subscribes to NeoForge's main game event bus:

- `BlockEvent.BreakEvent` and `BlockEvent.EntityPlaceEvent` are enforced on the logical server.
- `PlayerInteractEvent.RightClickBlock` and `RightClickItem` are checked on client and server. Cancellation uses `InteractionResult.FAIL` so client prediction does not temporarily consume a stack or show a placement that the server will reject.

The event layer gets IDs from `BuiltInRegistries.BLOCK` and `BuiltInRegistries.ITEM`, then asks `AgeCatalog` on the server or `ClientAgeRecipeState` on the client. An explicit `unlock_item_ids` exception allows the matching item, and its corresponding block when it has an item form, without opening other content from that namespace.

This gate is about player interactions. It does not cover every mod-internal call, command, hopper transfer, machine, or automation route. Add a mod-specific integration if those paths must also be restricted.

### Wild Mob Spawning

`AgeMobSpawnEvents` listens to NeoForge's `MobSpawnEvent.SpawnPlacementCheck`. That event is raised on the server before spawn placement succeeds and provides both the `EntityType<?>` and `MobSpawnType`. The code checks only `NATURAL` and `CHUNK_GENERATION`, obtains the full entity ID through `BuiltInRegistries.ENTITY_TYPE`, and sets the event result to `FAIL` when the exact entity or its namespace is not allowed in the current age. Namespace rules remain the default; adding any entity-specific rule for a namespace switches that namespace to allowlist behavior.

This hook is deliberately not `EntityJoinLevelEvent`: it does not run when saved entities are loaded from chunks. Existing mobs therefore remain. Spawners, spawn eggs, commands, breeding, structure spawns, and other non-natural spawn types are not rejected by this rule. Some spawner custom-rule flows do not invoke placement checks, which is another reason not to describe this as a universal entity ban.

### Recipe Lookup, Crafting, and Recipe Book

`RecipeManagerMixin` filters the return of each 1.21.1 `RecipeManager.getRecipeFor` overload and `getAllRecipesFor`. Server-side lookups use the active age catalog; clients use the synchronized locked-recipe snapshot. The `getAllRecipesFor` filter covers mods such as Overgeared that scan all recipes of a type when searching for forging matches. A mod that bypasses `RecipeManager` or uses a private cache still needs a dedicated integration.

Vanilla crafting has a separate result path. `CraftingMenuMixin` intercepts `slotChangedCraftingGrid` on the server before the result can be taken. For a locked recipe it clears the result container, updates the remote slot, sends `ClientboundContainerSetSlotPacket`, and cancels the original calculation. `CraftingMenuAccessor` is a Mixin `@Accessor` for the private `craftSlots` field; `AgeCraftingRules` uses it to force open crafting screens to recalculate after an age change.

`ServerRecipeBookMixin` filters arguments to `ServerRecipeBook.addRecipes`. This prevents an advancement from adding a still-locked recipe back into the vanilla recipe book. `AgeRecipeBookSync` separately reconciles known recipes at login, age changes, and datapack reload: it removes locked recipes with `ServerPlayer.resetRecipes` and adds unlocked recipes with `ServerPlayer.awardRecipes`. Those player APIs send the recipe-book update to the client.

Create's sequenced-assembly helper directly scans recipes rather than relying exclusively on `RecipeManager.getRecipeFor`. `SequencedAssemblyRecipeMixin` is therefore a `@Pseudo` optional mixin targeting Create by class-name string. `require = 0` means the mod can load without Create or without that method being present. This is a narrow compatibility hook, not a general guarantee for every Create or other-mod machine path.

The `agesofelysium.mixins.json` file registers these hooks. Mixins are used only where the event API has no appropriate cancellable pre-operation event; most behavior uses normal NeoForge events or public methods instead.

### JEI Presentation

`AgesJeiPlugin` implements JEI's `IModPlugin` and is annotated with `@JeiPlugin`. The mod uses JEI as `compileOnly` and declares it optional, so a dedicated server does not need the JEI classes at runtime. `onRuntimeAvailable` stores `IJeiRuntime`; `onRuntimeUnavailable` removes listeners and clears plugin-owned visibility state.

On updates, the plugin:

1. Hides JEI recipe categories whose `RecipeType` UID namespace is locked, except when that namespace has an explicitly unlocked recipe exception. In that case it leaves the category visible and hides only locked recipe entries.
2. Hides registered item stacks and fluid stacks from locked namespaces, excluding explicit item exceptions.
3. Hides recipes with locked IDs and recipe output items, except outputs listed in `unlock_item_ids`.
4. Diffs its own previous visibility sets so it can unhide only the things this plugin hid when the server reports an unlock.

JEI's `IIngredientVisibility` supports hiding/unhiding item and fluid ingredient types in both `UidContext.Ingredient` and `UidContext.Recipe`. `IRecipeManager` supports hiding/unhiding recipe entries and categories. The JEI API is presentation only; server recipe checks remain the enforcement boundary. Some third-party JEI displays may use synthetic IDs or categories that do not match their Minecraft recipe namespace, so mod-specific JEI extensions may be needed for those displays.

## Structure Worldgen and Retro-Generation

`unlock_structure_ids` gives a registered worldgen structure an age. `ChunkGeneratorStructureMixin` hooks the normal `tryGenerateStructure` path and rejects configured structure starts while the current age is earlier. Unconfigured structures are unchanged. This prevents the structure from spawning into newly generated chunks before unlock while leaving the structure mod installed and its resources available.

`unlock_placed_feature_ids` gates individual registered `PlacedFeature`s through `PlacedFeatureMixin.placeWithBiomeCheck`. This covers worldgen features that are not structures, such as Ice and Fire's dragon skeleton placements. The rule suppresses new chunk generation before unlock.

`retro_generate_placed_features` is a separate opt-in. `PlacedFeatureRetroGenerationHandler` replays the selected feature from a deterministic world-seed/feature/chunk seed, only in the configured dimensions, and only when every chunk in its declared radius is already loaded. The scheduler applies the optional OPAC claim check to that full radius before placement. Empty `dimensions` means the feature's own placement rules choose applicable dimensions. The current pack opts Waystone variants in at radius zero and Create's layered ore features at radius two. Large/custom YUNG features are age-gated but remain new-chunk-only until dedicated footprint handlers are implemented.

`unlock_dimension_ids` is enforced by `DimensionAccessEvents` on entity travel. Players already inside a dimension that becomes locked are returned to the Overworld after login, age change, or datapack reload. The built-in `aether:the_aether` dimension and Aether item namespace unlock in Steam Age.

`RetroGenerationScheduler` queues already-loaded chunks within eight chunks of players at age changes and discovers other old chunks on chunk load. `ServerTickEvent.Post` processes at most four tasks per tick. It never force-loads candidate chunks. Pending tasks are persisted, retried if their footprint is not currently loaded, and completed checks are remembered. Completion markers are cleared when the age changes or the catalog reloads so newly eligible structures can be reconsidered.

`RetroGenerationRegistry` combines explicitly registered custom handlers with generic handlers discovered from the live structure-set registry. Every structure in `unlock_structure_ids` is matched to its containing structure set; matching sets use `StructureSetRetroGenerationHandler`, which follows placement rules and weighted entries, calls the registered `Structure.generate` implementation, and records vanilla starts/references. This supports structures using Minecraft's standard `StructureSet`/`Structure` generation path without compile-time dependencies or per-mod ID lists. Handler IDs are structure-set IDs, allowing persisted tasks to resolve again after restart or datapack reload.

Before generic placement, every chunk in the generated bounding-box footprint must already be loaded, and existing starts from the same structure set are checked across that footprint. `StructureStart.placeInChunk` is clipped to each chunk, then the start and references are recorded with `StructureManager`. If a chunk is missing, the task is deferred rather than force-loading it. When Open Parties and Claims is installed, the scheduler checks claims immediately before invoking placement; without OPAC, `ClaimProtection` has no provider and reports no claimed chunks. Mods with custom placement/generation mechanisms that do not follow the normal Minecraft APIs can register a dedicated `RetroStructureHandler`. Claim checks do not detect unclaimed player builds.

`RetroStructureHandler` is the extension interface for other mods. Its `id()` must match an ID in `unlock_structure_ids`; its default `isUnlocked` consults the catalog rule. `findCandidate` returns a full inclusive footprint plus a placement callback. Register it through `RetroGenerationRegistry`. A handler must reproduce the source mod's placement rules, avoid existing structures, declare the complete footprint, and ensure its placement callback is safe to retry if it returns false or throws.

## Why These APIs

| API or interface | Why this project uses it |
| --- | --- |
| NeoForge `@Mod` | Declares the mod entry point and lets NeoForge discover the mod. |
| NeoForge `@EventBusSubscriber` / `@SubscribeEvent` | Registers command, datapack-reload, player-login, block interaction, chunk-load, server-tick, and spawn callbacks with the appropriate event bus. |
| `SavedData` / `SavedData.Factory` | Persists world-wide age and retro-generation state using Minecraft's save lifecycle rather than an external file. |
| `SimpleJsonResourceReloadListener` | Loads datapack resources using Minecraft resource priorities and `/reload`. |
| Brigadier `Commands` | Integrates `/age` with vanilla permission checks, parsing, feedback, and tab suggestions. |
| `CustomPacketPayload` / `StreamCodec` | Uses NeoForge's typed play-protocol payload system to send compact server-derived lock snapshots. |
| `MobSpawnEvent.SpawnPlacementCheck` | Rejects natural spawn attempts before entity insertion, without inspecting or deleting entities loaded from saved chunks. |
| JEI `IModPlugin` / `@JeiPlugin` and `IJeiRuntime` | Hooks the optional JEI lifecycle and changes visible recipes/ingredients when synchronized age rules change. |
| `IIngredientVisibility` / `IRecipeManager` | Provides reversible JEI hiding in ingredient-list, recipe-slot, recipe, and category contexts. |
| Optional Open Parties and Claims `OpenPACServerAPI` | Provides actual claim data for retro-generation protection when installed; it is not required for Ages of Elysium's other features. |
| SpongePowered Mixin `@Inject`, `@ModifyVariable`, `@Accessor` | Adds narrow hooks for recipe-result, recipe-book, and configured structure-start paths without replacing whole Minecraft classes. `@Pseudo` allows the Create hook to remain optional. |
| Java records and immutable collection snapshots | Make age definitions, task records, and shared catalog state concise and safe to read across callbacks. |

## Building and Testing

Use Java 21 and build from the repository root:

```powershell
.\gradlew.bat build
```

The jar is written to `build\libs\`. For an in-development client, run `gradlew.bat runClient` to launch the configured NeoForge client run. The current PrismLauncher instance can be used by copying the built jar into its `minecraft\mods` directory. Open Parties and Claims is optional; without it, retro-generation does not check claims. JEI is optional.

Useful smoke checks:

1. Start a fresh world. `/age get` should report the configured age with the lowest order; `/age set <age_id>` should use a stable catalog ID.
2. In Stone Age, verify a recipe configured for Modern is absent from crafting results and the vanilla recipe book; verify JEI hides the recipe and output when installed.
3. Set the age to Modern and check that open crafting screens, recipe-book knowledge, and JEI visibility update without reconnecting.
4. Test a namespace lock such as Create or AE2 and confirm right-click prediction does not consume the held stack before unlock.
5. With Ice and Fire installed, confirm its items are locked in Stone and unlock in Bronze; compare natural spawning in Bronze/Medieval with other ages, and confirm saved mobs and spawn eggs remain unaffected.
6. Configure a structure ID from an installed structure mod, verify no new starts before its configured age, then unlock it and check an old unclaimed area for a retro-generated structure. Repeat over a claimed area and confirm it is skipped; verify existing structures/entities survive age changes.

Compile/build checks do not launch Minecraft, apply mixins in a running game, or verify a mod's private machine pathways. Treat those as in-game integration tests.