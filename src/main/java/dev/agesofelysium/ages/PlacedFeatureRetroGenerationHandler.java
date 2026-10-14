package dev.agesofelysium.ages;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.jetbrains.annotations.Nullable;

public final class PlacedFeatureRetroGenerationHandler implements RetroStructureHandler {
    private static final String TASK_PATH_PREFIX = "placed_feature/";

    private final ResourceLocation featureId;
    private final AgeCatalog.PlacedFeatureRetroRule rule;

    private PlacedFeatureRetroGenerationHandler(ResourceLocation featureId, AgeCatalog.PlacedFeatureRetroRule rule) {
        this.featureId = featureId;
        this.rule = rule;
    }

    /** Discovers age-configured retro features that exist in the current registry. */
    static List<PlacedFeatureRetroGenerationHandler> discover(RegistryAccess registries) {
        List<PlacedFeatureRetroGenerationHandler> handlers = new ArrayList<>();
        for (ResourceLocation featureId : AgeCatalog.getPlacedFeatureRetroRules().keySet()) {
            PlacedFeatureRetroGenerationHandler handler = fromFeatureId(registries, featureId);
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return List.copyOf(handlers);
    }

    /** Resolves a persisted handler ID after restart without mod-specific registrations. */
    @Nullable
    static PlacedFeatureRetroGenerationHandler fromTaskId(RegistryAccess registries, ResourceLocation taskId) {
        if (!taskId.getNamespace().equals(AgesMod.MOD_ID) || !taskId.getPath().startsWith(TASK_PATH_PREFIX)) {
            return null;
        }
        String featureKey = taskId.getPath().substring(TASK_PATH_PREFIX.length());
        int namespaceSeparator = featureKey.indexOf('/');
        if (namespaceSeparator <= 0 || namespaceSeparator == featureKey.length() - 1) {
            return null;
        }
        ResourceLocation featureId = ResourceLocation.tryParse(
            featureKey.substring(0, namespaceSeparator) + ":" + featureKey.substring(namespaceSeparator + 1)
        );
        return featureId == null ? null : fromFeatureId(registries, featureId);
    }

    @Nullable
    private static PlacedFeatureRetroGenerationHandler fromFeatureId(RegistryAccess registries, ResourceLocation featureId) {
        AgeCatalog.PlacedFeatureRetroRule rule = AgeCatalog.getPlacedFeatureRetroRules().get(featureId);
        if (rule == null) {
            return null;
        }
        Registry<PlacedFeature> placedFeatures = registries.registryOrThrow(Registries.PLACED_FEATURE);
        return placedFeatures.get(featureId) == null ? null : new PlacedFeatureRetroGenerationHandler(featureId, rule);
    }

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(
            AgesMod.MOD_ID,
            TASK_PATH_PREFIX + featureId.getNamespace() + "/" + featureId.getPath()
        );
    }

    @Override
    public boolean isUnlocked(String currentAgeId) {
        return AgeCatalog.isPlacedFeatureUnlocked(featureId, currentAgeId);
    }

    @Override
    public boolean isApplicableToDimension(ResourceLocation dimensionId) {
        return rule.dimensions().isEmpty() || rule.dimensions().contains(dimensionId);
    }

    @Override
    @Nullable
    public Candidate findCandidate(ServerLevel level, ChunkPos startChunk) {
        PlacedFeature feature = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).get(featureId);
        if (feature == null || !isApplicableToDimension(level.dimension().location())) {
            return null;
        }

        int radius = rule.radiusChunks();
        return new Candidate(
            startChunk.x - radius,
            startChunk.z - radius,
            startChunk.x + radius,
            startChunk.z + radius,
            () -> placeIfSafe(level, feature, startChunk, radius)
        );
    }

    private boolean placeIfSafe(ServerLevel level, PlacedFeature feature, ChunkPos startChunk, int radius) {
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(startChunk.x + offsetX, startChunk.z + offsetZ);
                if (chunk == null) {
                    return false;
                }
            }
        }

        int originX = startChunk.getMinBlockX();
        int originZ = startChunk.getMinBlockZ();
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setDecorationSeed(level.getSeed() ^ featureId.hashCode(), originX, originZ);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        return feature.placeWithBiomeCheck(
            level,
            generator,
            random,
            new net.minecraft.core.BlockPos(originX, level.getMinBuildHeight(), originZ)
        );
    }
}