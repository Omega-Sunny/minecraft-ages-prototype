package dev.agesofelysium.ages.mixin;

import dev.agesofelysium.ages.AgeCatalog;
import dev.agesofelysium.ages.ClientAgeRecipeState;
import dev.agesofelysium.ages.AgeSavedData;
import java.util.Optional;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    /** Filters direct recipe scans used by mods such as Overgeared's custom forging recipes. */
    @Inject(method = "getAllRecipesFor", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void ages$filterAllRecipesFor(
        RecipeType<T> recipeType,
        CallbackInfoReturnable<List<RecipeHolder<T>>> callback
    ) {
        List<RecipeHolder<T>> recipes = callback.getReturnValue();
        if (recipes == null || recipes.isEmpty()) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        List<RecipeHolder<T>> unlockedRecipes;
        if (server != null) {
            String currentAgeId = AgeSavedData.get(server).getCurrentAgeId();
            unlockedRecipes = recipes.stream()
                .filter(holder -> AgeCatalog.isRecipeUnlocked(holder.id(), currentAgeId))
                .toList();
        } else {
            unlockedRecipes = recipes.stream()
                .filter(holder -> ClientAgeRecipeState.isRecipeUnlocked(holder.id()))
                .toList();
        }

        if (unlockedRecipes.size() != recipes.size()) {
            callback.setReturnValue(unlockedRecipes);
        }
    }

    /** Rejects age-locked recipes found through the common machine/player lookup overload. */
    @Inject(
        method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",
        at = @At("RETURN"),
        cancellable = true
    )
    private <I extends RecipeInput, T extends Recipe<I>> void ages$filterRecipe(
        RecipeType<T> recipeType,
        I input,
        Level level,
        CallbackInfoReturnable<Optional<RecipeHolder<T>>> callback
    ) {
        rejectLockedRecipe(level, callback);
    }

    /** Applies the same lock when a lookup supplies a preferred recipe ID. */
    @Inject(
        method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
        at = @At("RETURN"),
        cancellable = true
    )
    private <I extends RecipeInput, T extends Recipe<I>> void ages$filterRecipeById(
        RecipeType<T> recipeType,
        I input,
        Level level,
        net.minecraft.resources.ResourceLocation lastRecipeId,
        CallbackInfoReturnable<Optional<RecipeHolder<T>>> callback
    ) {
        rejectLockedRecipe(level, callback);
    }

    /** Applies the same lock when a lookup supplies a cached recipe holder. */
    @Inject(
        method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
        at = @At("RETURN"),
        cancellable = true
    )
    private <I extends RecipeInput, T extends Recipe<I>> void ages$filterRecipeByHolder(
        RecipeType<T> recipeType,
        I input,
        Level level,
        RecipeHolder<T> lastRecipe,
        CallbackInfoReturnable<Optional<RecipeHolder<T>>> callback
    ) {
        rejectLockedRecipe(level, callback);
    }

    /** Removes locked results from server-side recipe lookups without affecting client recipe displays. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void rejectLockedRecipe(Level level, CallbackInfoReturnable callback) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Optional<?> result = (Optional<?>) callback.getReturnValue();
        if (result.isEmpty() || !(result.get() instanceof RecipeHolder<?> holder)) {
            return;
        }
        String currentAgeId = AgeSavedData.get(serverLevel.getServer()).getCurrentAgeId();
        if (!AgeCatalog.isRecipeUnlocked(holder.id(), currentAgeId)) {
            callback.setReturnValue(Optional.empty());
        }
    }
}