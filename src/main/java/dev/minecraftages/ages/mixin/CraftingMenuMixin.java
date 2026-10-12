package dev.minecraftages.ages.mixin;

import dev.minecraftages.ages.AgeCatalog;
import dev.minecraftages.ages.AgeSavedData;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    /** Clears and syncs locked vanilla crafting results before they can be taken. */
    @Inject(method = "slotChangedCraftingGrid", at = @At("HEAD"), cancellable = true)
    private static void ages$hideLockedRecipe(
        AbstractContainerMenu menu,
        Level level,
        Player player,
        CraftingContainer craftingGrid,
        ResultContainer resultSlots,
        @Nullable RecipeHolder<CraftingRecipe> recipe,
        CallbackInfo callback
    ) {
        if (!(level instanceof ServerLevel serverLevel)
            || recipe == null
            || AgeCatalog.isRecipeUnlocked(
                recipe.id(),
                AgeSavedData.get(serverLevel.getServer()).getCurrentAgeId()
            )) {
            return;
        }

        ItemStack emptyResult = ItemStack.EMPTY;
        resultSlots.setItem(0, emptyResult);
        menu.setRemoteSlot(0, emptyResult);
        ServerPlayer serverPlayer = (ServerPlayer) player;
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
            menu.containerId,
            menu.incrementStateId(),
            0,
            emptyResult
        ));
        callback.cancel();
    }
}