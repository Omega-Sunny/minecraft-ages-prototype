package dev.agesofelysium.ages;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import net.minecraft.resources.ResourceLocation;

public final class ClientAgeRecipeState {
    private static final Set<ResourceLocation> LOCKED_RECIPE_IDS = new CopyOnWriteArraySet<>();
    private static final Set<String> LOCKED_MOD_IDS = new CopyOnWriteArraySet<>();
    private static final Set<ResourceLocation> UNLOCKED_ITEM_IDS = new CopyOnWriteArraySet<>();
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    /** Prevents instances; synchronized lock state is shared with optional client integrations. */
    private ClientAgeRecipeState() {
    }

    /** Returns an immutable snapshot of recipe IDs the server says are still locked. */
    public static Set<ResourceLocation> getLockedRecipeIds() {
        return Set.copyOf(LOCKED_RECIPE_IDS);
    }

    /** Returns whether the server's latest lock snapshot permits the supplied recipe ID. */
    public static boolean isRecipeUnlocked(ResourceLocation recipeId) {
        return !LOCKED_RECIPE_IDS.contains(recipeId);
    }

    /** Returns an immutable snapshot of mod namespaces currently locked by the server. */
    public static Set<String> getLockedModIds() {
        return Set.copyOf(LOCKED_MOD_IDS);
    }

    /** Returns item IDs explicitly unlocked ahead of their mod namespace. */
    public static Set<ResourceLocation> getUnlockedItemIds() {
        return Set.copyOf(UNLOCKED_ITEM_IDS);
    }

    /** Replaces the server-provided lock set and notifies registered presentation listeners. */
    public static void setLockState(
        List<ResourceLocation> recipeIds,
        Set<String> modIds,
        Set<ResourceLocation> itemIds
    ) {
        LOCKED_RECIPE_IDS.clear();
        LOCKED_RECIPE_IDS.addAll(recipeIds);
        LOCKED_MOD_IDS.clear();
        LOCKED_MOD_IDS.addAll(modIds);
        UNLOCKED_ITEM_IDS.clear();
        UNLOCKED_ITEM_IDS.addAll(itemIds);
        LISTENERS.forEach(Runnable::run);
    }

    /** Registers a listener that should refresh its UI when recipe locks change. */
    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    /** Removes a previously registered listener when its UI integration shuts down. */
    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }
}