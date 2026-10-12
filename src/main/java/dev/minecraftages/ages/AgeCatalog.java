package dev.minecraftages.ages;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public final class AgeCatalog {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_AGE_ID = "stone";
    private static volatile Snapshot current = Snapshot.empty();

    /** Prevents instances; catalog state is shared and replaced as one immutable snapshot. */
    private AgeCatalog() {
    }

    /** Returns the catalog's ages in progression order. */
    public static List<Age> getAges() {
        return current.ages();
    }

    /** Looks up an age by its stable ID. */
    public static Optional<Age> get(String id) {
        return Optional.ofNullable(current.byId().get(id));
    }

    /** Resolves an old display-name save value to its stable ID, ignoring case. */
    public static Optional<String> findIdByDisplayName(String name) {
        return Optional.ofNullable(current.idByDisplayName().get(name.toLowerCase(Locale.ROOT)));
    }

    /** Checks whether the currently loaded catalog contains the supplied age ID. */
    public static boolean isKnown(String id) {
        return current.byId().containsKey(id);
    }

    /** Compares two ages by configured order; unknown IDs never count as unlocked. */
    public static boolean isAtLeast(String currentAgeId, String requiredAgeId) {
        Age currentAge = current.byId().get(currentAgeId);
        Age requiredAge = current.byId().get(requiredAgeId);
        return currentAge != null && requiredAge != null && currentAge.order() >= requiredAge.order();
    }

    /** Treats unconfigured mod namespaces as unlocked and checks configured ones by age order. */
    public static boolean isModUnlocked(String modId, String currentAgeId) {
        String unlockAgeId = current.modUnlockAge().get(modId);
        return unlockAgeId == null || isAtLeast(currentAgeId, unlockAgeId);
    }

    /** Allows natural spawning for unconfigured namespaces or explicitly listed ages. */
    public static boolean isWildSpawnAllowed(String modId, String currentAgeId) {
        Set<String> allowedAges = current.wildSpawnAges().get(modId);
        return allowedAges == null || allowedAges.contains(currentAgeId);
    }

    /** Checks whether a recipe ID is available at the world's current age. */
    public static boolean isRecipeUnlocked(ResourceLocation recipeId, String currentAgeId) {
        String unlockAgeId = current.recipeUnlockAge().get(recipeId);
        if (unlockAgeId != null) {
            return isAtLeast(currentAgeId, unlockAgeId);
        }
        return isModUnlocked(recipeId.getNamespace(), currentAgeId);
    }

    /** Checks an item's explicit exception age first, then falls back to its mod's namespace lock. */
    public static boolean isItemUnlocked(ResourceLocation itemId, String currentAgeId) {
        String unlockAgeId = current.itemUnlockAge().get(itemId);
        if (unlockAgeId != null) {
            return isAtLeast(currentAgeId, unlockAgeId);
        }
        return isModUnlocked(itemId.getNamespace(), currentAgeId);
    }

    /** Checks whether an item has an explicit age rule distinct from its mod's namespace rule. */
    public static boolean hasItemUnlockRule(ResourceLocation itemId) {
        return current.itemUnlockAge().containsKey(itemId);
    }

    /** Checks whether an ID has either an explicit recipe rule or an inherited mod lock. */
    public static boolean isRecipeManaged(ResourceLocation recipeId) {
        return current.recipeUnlockAge().containsKey(recipeId)
            || current.modUnlockAge().containsKey(recipeId.getNamespace());
    }

    /** Returns explicitly age-assigned recipe IDs that have not reached their assigned age. */
    public static List<ResourceLocation> getLockedRecipeIds(String currentAgeId) {
        return current.recipeUnlockAge().entrySet().stream()
            .filter(entry -> !isAtLeast(currentAgeId, entry.getValue()))
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    }

    /** Returns mod namespaces whose configured unlock age is later than the supplied age. */
    public static Set<String> getLockedModIds(String currentAgeId) {
        return current.modUnlockAge().entrySet().stream()
            .filter(entry -> !isAtLeast(currentAgeId, entry.getValue()))
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Returns item IDs whose explicit exception is active at the supplied age. */
    public static Set<ResourceLocation> getUnlockedItemIds(String currentAgeId) {
        return current.itemUnlockAge().entrySet().stream()
            .filter(entry -> isAtLeast(currentAgeId, entry.getValue()))
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Returns all recipe IDs with age rules so player recipe books can be reconciled. */
    public static Set<ResourceLocation> getConfiguredRecipeIds() {
        return current.recipeUnlockAge().keySet();
    }

    /** Parses and validates a complete datapack catalog before it can replace live state. */
    static Snapshot parse(Map<ResourceLocation, JsonElement> resources) {
        List<Age> ages = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        Map<String, String> idByDisplayName = new HashMap<>();
        Map<String, String> modUnlockAge = new HashMap<>();
        Map<String, Set<String>> wildSpawnAges = new HashMap<>();
        Map<ResourceLocation, String> recipeUnlockAge = new HashMap<>();
        Map<ResourceLocation, String> itemUnlockAge = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
            String id = GsonHelper.getAsString(json, "id");
            String name = GsonHelper.getAsString(json, "name").trim();
            int order = GsonHelper.getAsInt(json, "order");

            if (!id.matches("[a-z0-9_.-]+")) {
                throw new IllegalArgumentException("Invalid age id '" + id + "' in " + entry.getKey());
            }
            if (name.isBlank()) {
                throw new IllegalArgumentException("Age " + id + " has a blank display name");
            }
            if (order < 0) {
                throw new IllegalArgumentException("Age " + id + " has a negative order");
            }
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate age id: " + id);
            }
            if (!orders.add(order)) {
                throw new IllegalArgumentException("Duplicate age order: " + order);
            }
            String normalizedName = name.toLowerCase(Locale.ROOT);
            if (idByDisplayName.putIfAbsent(normalizedName, id) != null) {
                throw new IllegalArgumentException("Duplicate age display name: " + name);
            }
            List<String> lockedModIds = new ArrayList<>();
            JsonArray modIds = json.has("locked_mod_ids") ? GsonHelper.getAsJsonArray(json, "locked_mod_ids") : new JsonArray();
            for (JsonElement modIdElement : modIds) {
                String modId = modIdElement.getAsString();
                if (!modId.matches("[a-z0-9_.-]+")) {
                    throw new IllegalArgumentException("Invalid mod ID '" + modId + "' in age " + id);
                }
                if (modUnlockAge.putIfAbsent(modId, id) != null) {
                    throw new IllegalArgumentException("Mod ID is assigned to more than one unlock age: " + modId);
                }
                lockedModIds.add(modId);
            }
            JsonArray wildSpawnModIds = json.has("wild_spawn_mod_ids")
                ? GsonHelper.getAsJsonArray(json, "wild_spawn_mod_ids")
                : new JsonArray();
            for (JsonElement modIdElement : wildSpawnModIds) {
                String modId = modIdElement.getAsString();
                if (!modId.matches("[a-z0-9_.-]+")) {
                    throw new IllegalArgumentException("Invalid wild-spawn mod ID '" + modId + "' in age " + id);
                }
                wildSpawnAges.computeIfAbsent(modId, ignored -> new HashSet<>()).add(id);
            }
            List<ResourceLocation> unlockRecipeIds = new ArrayList<>();
            JsonArray recipeIds = json.has("unlock_recipe_ids")
                ? GsonHelper.getAsJsonArray(json, "unlock_recipe_ids")
                : new JsonArray();
            for (JsonElement recipeIdElement : recipeIds) {
                ResourceLocation recipeId = ResourceLocation.tryParse(recipeIdElement.getAsString());
                if (recipeId == null) {
                    throw new IllegalArgumentException("Invalid recipe ID '" + recipeIdElement.getAsString() + "' in age " + id);
                }
                if (recipeUnlockAge.putIfAbsent(recipeId, id) != null) {
                    throw new IllegalArgumentException("Recipe is assigned to more than one unlock age: " + recipeId);
                }
                unlockRecipeIds.add(recipeId);
            }
            List<ResourceLocation> unlockItemIds = new ArrayList<>();
            JsonArray itemIds = json.has("unlock_item_ids")
                ? GsonHelper.getAsJsonArray(json, "unlock_item_ids")
                : new JsonArray();
            for (JsonElement itemIdElement : itemIds) {
                ResourceLocation itemId = ResourceLocation.tryParse(itemIdElement.getAsString());
                if (itemId == null) {
                    throw new IllegalArgumentException("Invalid item ID '" + itemIdElement.getAsString() + "' in age " + id);
                }
                if (itemUnlockAge.putIfAbsent(itemId, id) != null) {
                    throw new IllegalArgumentException("Item is assigned to more than one unlock age: " + itemId);
                }
                unlockItemIds.add(itemId);
            }
            ages.add(new Age(id, name, order, List.copyOf(lockedModIds), List.copyOf(unlockRecipeIds)));
        }

        for (Map.Entry<ResourceLocation, String> entry : recipeUnlockAge.entrySet()) {
            if (!ids.contains(entry.getValue())) {
                throw new IllegalArgumentException("Recipe " + entry.getKey() + " references unknown age " + entry.getValue());
            }
        }
        for (Map.Entry<ResourceLocation, String> entry : itemUnlockAge.entrySet()) {
            if (!ids.contains(entry.getValue())) {
                throw new IllegalArgumentException("Item " + entry.getKey() + " references unknown age " + entry.getValue());
            }
        }

        if (!ids.contains(DEFAULT_AGE_ID)) {
            throw new IllegalArgumentException("The age catalog must contain the default age '" + DEFAULT_AGE_ID + "'");
        }
        Age defaultAge = ages.stream().filter(age -> age.id().equals(DEFAULT_AGE_ID)).findFirst().orElseThrow();
        if (defaultAge.order() != 0) {
            throw new IllegalArgumentException("The default age '" + DEFAULT_AGE_ID + "' must have order 0");
        }
        ages.sort(Comparator.comparingInt(Age::order));
        Map<String, Age> byId = new HashMap<>();
        for (Age age : ages) {
            byId.put(age.id(), age);
        }
        return new Snapshot(
            List.copyOf(ages),
            Map.copyOf(byId),
            Map.copyOf(idByDisplayName),
            Map.copyOf(modUnlockAge),
            freezeSets(wildSpawnAges),
            Map.copyOf(recipeUnlockAge),
            Map.copyOf(itemUnlockAge)
        );
    }

    /** Copies nested mutable age sets into an immutable map for the live catalog snapshot. */
    private static Map<String, Set<String>> freezeSets(Map<String, Set<String>> values) {
        Map<String, Set<String>> frozen = new HashMap<>();
        values.forEach((key, value) -> frozen.put(key, Set.copyOf(value)));
        return Map.copyOf(frozen);
    }

    /** Atomically publishes a validated catalog snapshot to all readers. */
    static void replace(Snapshot snapshot) {
        current = snapshot;
    }

    /** Immutable definition for one progression age and its mod unlocks. */
    public record Age(
        String id,
        String name,
        int order,
        List<String> lockedModIds,
        List<ResourceLocation> unlockRecipeIds
    ) {
    }

    /** Immutable indexes used to answer age, display-name, and mod-unlock lookups. */
    record Snapshot(
        List<Age> ages,
        Map<String, Age> byId,
        Map<String, String> idByDisplayName,
        Map<String, String> modUnlockAge,
        Map<String, Set<String>> wildSpawnAges,
        Map<ResourceLocation, String> recipeUnlockAge,
        Map<ResourceLocation, String> itemUnlockAge
    ) {
        /** Creates the empty pre-reload state used before datapack resources are available. */
        private static Snapshot empty() {
            return new Snapshot(List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    static final class ReloadListener extends SimpleJsonResourceReloadListener {
        /** Reads JSON resources beneath data/<namespace>/ages/. */
        ReloadListener() {
            super(new Gson(), "ages");
        }

        /** Keeps the previous catalog intact if any resource makes the new catalog invalid. */
        @Override
        protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
            try {
                Snapshot snapshot = AgeCatalog.parse(resources);
                AgeCatalog.replace(snapshot);
                LOGGER.info("Loaded {} ages", snapshot.ages().size());
                net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    AgeCraftingRules.refreshOpenCraftingMenus(server);
                    AgeRecipeBookSync.syncAll(server);
                    AgeRecipeNetworking.sendToAll(server);
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Rejected invalid age catalog; keeping the previous valid catalog", exception);
            }
        }
    }
}