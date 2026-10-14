package dev.agesofelysium.ages.mixin;

import dev.agesofelysium.ages.AgeCatalog;
import dev.agesofelysium.ages.AgeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlacedFeature.class)
public abstract class PlacedFeatureMixin {
    /** Blocks configured placed features from generating before their assigned age. */
    @Inject(method = "placeWithBiomeCheck", at = @At("HEAD"), cancellable = true)
    private void ages$gatePlacedFeature(
        WorldGenLevel level,
        ChunkGenerator generator,
        RandomSource random,
        BlockPos origin,
        CallbackInfoReturnable<Boolean> callback
    ) {
        ResourceLocation featureId = level.registryAccess()
            .registryOrThrow(Registries.PLACED_FEATURE)
            .getKey((PlacedFeature)(Object)this);
        if (featureId == null || !AgeCatalog.isPlacedFeatureManaged(featureId)) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !AgeCatalog.isPlacedFeatureUnlocked(
            featureId,
            AgeSavedData.get(server).getCurrentAgeId()
        )) {
            callback.setReturnValue(false);
        }
    }
}