package dev.agesofelysium.ages;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = AgesMod.MOD_ID)
public final class AgePlayerSyncEvents {
    /** Prevents instances; login synchronization is handled by a static event callback. */
    private AgePlayerSyncEvents() {
    }

    @SubscribeEvent
    /** Sends the current recipe visibility state to each joining server player. */
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AgeRecipeBookSync.syncPlayer(player);
            AgeRecipeNetworking.sendToPlayer(player);
        }
    }
}