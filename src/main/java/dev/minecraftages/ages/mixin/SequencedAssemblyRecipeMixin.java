package dev.minecraftages.ages.mixin;

import dev.minecraftages.ages.AgeCatalog;
import dev.minecraftages.ages.AgeSavedData;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe", remap = false)
public abstract class SequencedAssemblyRecipeMixin {
    /** Filters locked parent recipes from Create's direct sequenced-assembly scan on the server. */
    @Inject(method = "getRecipes", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void ages$filterLockedAssemblyRecipes(
        Level level,
        ItemStack input,
        RecipeType<?> recipeType,
        Class<?> recipeClass,
        Predicate<?> recipeFilter,
        CallbackInfoReturnable<List<?>> callback
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        String currentAgeId = AgeSavedData.get(serverLevel.getServer()).getCurrentAgeId();
        List<?> filtered = callback.getReturnValue().stream()
            .filter(candidate -> !(candidate instanceof RecipeHolder<?> holder)
                || AgeCatalog.isRecipeUnlocked(holder.id(), currentAgeId))
            .toList();
        callback.setReturnValue(filtered);
    }
}