package dev.minecraftages.ages.mixin;

import dev.minecraftages.ages.AgeCatalog;
import dev.minecraftages.ages.AgeSavedData;
import java.util.Collection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerRecipeBook.class)
public abstract class ServerRecipeBookMixin {
    /** Stops advancements and other recipe grants from adding age-locked recipes back to the book. */
    @ModifyVariable(method = "addRecipes", at = @At("HEAD"), argsOnly = true)
    private Collection<RecipeHolder<?>> ages$filterLockedRecipes(Collection<RecipeHolder<?>> recipes) {
        if (recipes.isEmpty()) {
            return recipes;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return recipes;
        }
        String currentAgeId = AgeSavedData.get(server).getCurrentAgeId();
        return recipes.stream()
            .filter(recipe -> AgeCatalog.isRecipeUnlocked(recipe.id(), currentAgeId))
            .toList();
    }
}