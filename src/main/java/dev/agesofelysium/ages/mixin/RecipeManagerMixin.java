package dev.agesofelysium.ages.mixin;

import dev.agesofelysium.ages.AgeCatalog;
import dev.agesofelysium.ages.AgeSavedData;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
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