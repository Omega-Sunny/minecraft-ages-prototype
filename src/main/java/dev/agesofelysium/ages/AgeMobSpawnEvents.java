package dev.agesofelysium.ages;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

@EventBusSubscriber(modid = AgesMod.MOD_ID)
public final class AgeMobSpawnEvents {
    private AgeMobSpawnEvents() {
    }

    /** Prevents locked-namespace mobs from natural or chunk-generation spawns without touching saved entities. */
    @SubscribeEvent
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        MobSpawnType spawnType = event.getSpawnType();
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) {
            return;
        }

        ServerLevelAccessor levelAccessor = event.getLevel();
        ServerLevel level = levelAccessor.getLevel();
        net.minecraft.resources.ResourceLocation entityId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
            .getKey(event.getEntityType());
        String currentAgeId = AgeSavedData.get(level.getServer()).getCurrentAgeId();
        if (!AgeCatalog.isWildSpawnAllowed(entityId, currentAgeId)) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }
}