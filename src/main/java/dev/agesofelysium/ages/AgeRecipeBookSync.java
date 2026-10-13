package dev.agesofelysium.ages;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;

public final class AgeRecipeBookSync {
    private AgeRecipeBookSync() {
    }

    /** Adds newly available age-managed recipes and removes those still locked from the vanilla recipe book. */
    public static void syncPlayer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        String currentAgeId = AgeSavedData.get(server).getCurrentAgeId();
        List<RecipeHolder<?>> toAdd = new ArrayList<>();
        List<RecipeHolder<?>> toRemove = new ArrayList<>();
        for (RecipeHolder<?> recipe : server.getRecipeManager().getRecipes()) {
            ResourceLocation recipeId = recipe.id();
            if (!AgeCatalog.isRecipeManaged(recipeId)) {
                continue;
            }
            boolean unlocked = AgeCatalog.isRecipeUnlocked(recipeId, currentAgeId);
            boolean alreadyKnown = player.getRecipeBook().contains(recipe);
            if (unlocked && !alreadyKnown) {
                toAdd.add(recipe);
            } else if (!unlocked && alreadyKnown) {
                toRemove.add(recipe);
            }
        }

        if (!toRemove.isEmpty()) {
            player.resetRecipes(toRemove);
        }
        if (!toAdd.isEmpty()) {
            player.awardRecipes(toAdd);
        }
    }

    /** Reconciles all online players after an age or datapack rule change. */
    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayer(player);
        }
    }
}