package dev.agesofelysium.ages;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = AgesMod.MOD_ID)
public final class AgeAccessEvents {
    /** Prevents instances; access checks are static event handlers. */
    private AgeAccessEvents() {
    }

    /** Cancels player mining of blocks whose namespace is still age-locked. */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && isBlockLocked(event.getState().getBlock(), level)) {
            event.setCanceled(true);
        }
    }

    /** Cancels player placement of blocks whose namespace is still age-locked. */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level
            && event.getEntity() instanceof Player
            && isBlockLocked(event.getState().getBlock(), level)) {
            event.setCanceled(true);
        }
    }

    /** Rejects locked block and item use on both sides before the client predicts a placement. */
    @SubscribeEvent
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        {
            var block = level.getBlockState(event.getPos()).getBlock();
            var item = event.getItemStack().getItem();
            if (isBlockLocked(block, level) || isItemLocked(item, level)) {
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
        }
    }

    /** Rejects locked held items on both sides before use is predicted. */
    @SubscribeEvent
    public static void onItemInteract(PlayerInteractEvent.RightClickItem event) {
        if (isItemLocked(event.getItemStack().getItem(), event.getLevel())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    /** Uses authoritative server catalog data or the last synchronized client lock state. */
    private static boolean isBlockLocked(net.minecraft.world.level.block.Block block, Level level) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(block.asItem());
        if (hasItemUnlockRule(itemId, level) && isItemUnlocked(itemId, level)) {
            return false;
        }
        return isNamespaceLocked(BuiltInRegistries.BLOCK.getKey(block).getNamespace(), level);
    }

    private static boolean isItemLocked(net.minecraft.world.item.Item item, Level level) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        return !isItemUnlocked(itemId, level);
    }

    private static boolean isItemUnlocked(ResourceLocation itemId, Level level) {
        if (level instanceof ServerLevel serverLevel) {
            String currentAgeId = AgeSavedData.get(serverLevel.getServer()).getCurrentAgeId();
            return AgeCatalog.isItemUnlocked(itemId, currentAgeId);
        }
        return !ClientAgeRecipeState.getLockedModIds().contains(itemId.getNamespace())
            || ClientAgeRecipeState.getUnlockedItemIds().contains(itemId);
    }

    private static boolean hasItemUnlockRule(ResourceLocation itemId, Level level) {
        if (level instanceof ServerLevel) {
            return AgeCatalog.hasItemUnlockRule(itemId);
        }
        return ClientAgeRecipeState.getUnlockedItemIds().contains(itemId);
    }

    private static boolean isNamespaceLocked(String modId, Level level) {
        if (level instanceof ServerLevel serverLevel) {
            String currentAgeId = AgeSavedData.get(serverLevel.getServer()).getCurrentAgeId();
            return !AgeCatalog.isModUnlocked(modId, currentAgeId);
        }
        return ClientAgeRecipeState.getLockedModIds().contains(modId);
    }
}