package dev.agesofelysium.ages;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = AgesMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class AgeRecipeNetworking {
    /** Prevents instances; payload setup and server broadcasts are static utilities. */
    private AgeRecipeNetworking() {
    }

    @SubscribeEvent
    /** Registers the server-to-client age recipe payload handler. */
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
            AgeRecipeSyncPayload.TYPE,
            AgeRecipeSyncPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                ClientAgeRecipeState.setLockState(payload.getRecipeIds(), payload.getModIds(), payload.getItemIds())
            )
        );
    }

    /** Sends the current world's recipe lock state to one player after they join. */
    public static void sendToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, createPayload(player.getServer()));
    }

    /** Sends the current world's recipe lock state to every connected player. */
    public static void sendToAll(MinecraftServer server) {
        AgeRecipeSyncPayload payload = createPayload(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** Builds the client presentation payload from the current age and active catalog. */
    private static AgeRecipeSyncPayload createPayload(MinecraftServer server) {
        String ageId = AgeSavedData.get(server).getCurrentAgeId();
        var lockedRecipeIds = server.getRecipeManager().getRecipes().stream()
            .map(net.minecraft.world.item.crafting.RecipeHolder::id)
            .filter(recipeId -> !AgeCatalog.isRecipeUnlocked(recipeId, ageId))
            .sorted()
            .toList();
        return AgeRecipeSyncPayload.fromRules(
            lockedRecipeIds,
            AgeCatalog.getLockedModIds(ageId),
            AgeCatalog.getUnlockedItemIds(ageId)
        );
    }
}