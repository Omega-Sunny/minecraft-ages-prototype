package dev.agesofelysium.ages;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = AgesMod.MOD_ID)
public final class DimensionAccessEvents {
    private DimensionAccessEvents() {
    }

    /** Cancels player travel into dimensions whose configured unlock age has not been reached. */
    @SubscribeEvent
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isLocked(player, event.getDimension().location())) {
            event.setCanceled(true);
            notifyLocked(player, event.getDimension().location());
        }
    }

    /** Recovers players who log in while already inside a dimension that is locked in this save. */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            returnToOverworldIfLocked(player);
        }
    }

    /** Evicts players when an age change makes their current dimension unavailable. */
    public static void enforceCurrentDimensions(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                returnToOverworldIfLocked(player);
            }
        }
    }

    private static void returnToOverworldIfLocked(ServerPlayer player) {
        ResourceLocation dimensionId = player.serverLevel().dimension().location();
        if (!isLocked(player, dimensionId)) {
            return;
        }

        ServerLevel overworld = player.getServer().overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(
            overworld,
            spawn.getX() + 0.5,
            spawn.getY(),
            spawn.getZ() + 0.5,
            player.getYRot(),
            player.getXRot()
        );
        notifyLocked(player, dimensionId);
    }

    private static boolean isLocked(ServerPlayer player, ResourceLocation dimensionId) {
        if (!AgeCatalog.isDimensionManaged(dimensionId)) {
            return false;
        }
        String currentAgeId = AgeSavedData.get(player.getServer()).getCurrentAgeId();
        return !AgeCatalog.isDimensionUnlocked(dimensionId, currentAgeId);
    }

    private static void notifyLocked(ServerPlayer player, ResourceLocation dimensionId) {
        String unlockAgeId = AgeCatalog.getDimensionUnlockAgeId(dimensionId).orElse(null);
        String unlockAgeName = unlockAgeId == null
            ? "a later age"
            : AgeCatalog.get(unlockAgeId).map(AgeCatalog.Age::name).orElse(unlockAgeId);
        player.sendSystemMessage(Component.literal("That dimension unlocks in " + unlockAgeName + "."));
    }
}