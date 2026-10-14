package dev.agesofelysium.ages;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = AgesMod.MOD_ID)
public final class RetroGenerationScheduler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int UNLOCK_SCAN_RADIUS = 8;
    private static final int TASKS_PER_TICK = 4;

    /** Prevents instances; chunk discovery and queue processing use static server events. */
    private RetroGenerationScheduler() {
    }

    /** Queues already-loaded chunks near online players for structures newly unlocked by this age. */
    public static void onAgeChanged(MinecraftServer server, String newAge) {
        RetroGenerationData.get(server).clearCompleted();
        for (ServerLevel level : server.getAllLevels()) {
            for (var player : level.players()) {
                ChunkPos center = player.chunkPosition();
                for (int offsetX = -UNLOCK_SCAN_RADIUS; offsetX <= UNLOCK_SCAN_RADIUS; offsetX++) {
                    for (int offsetZ = -UNLOCK_SCAN_RADIUS; offsetZ <= UNLOCK_SCAN_RADIUS; offsetZ++) {
                        ChunkPos chunk = new ChunkPos(center.x + offsetX, center.z + offsetZ);
                        if (level.getChunkSource().hasChunk(chunk.x, chunk.z)) {
                            enqueue(level, chunk, newAge);
                        }
                    }
                }
            }
        }
    }

    /** Lazily queues a loaded chunk for every handler unlocked at the world's current age. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            enqueue(level, event.getChunk().getPos(), AgeSavedData.get(level.getServer()).getCurrentAgeId());
        }
    }

    /** Processes a small fixed number of queued candidates each server tick. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        RetroGenerationData data = RetroGenerationData.get(event.getServer());
        int tasksThisTick = Math.min(TASKS_PER_TICK, data.pendingCount());
        for (int task = 0; task < tasksThisTick; task++) {
            processOne(event.getServer(), data);
        }
    }

    /** Adds one unlocked handler/chunk pair to the persistent queue without duplicating work. */
    private static void enqueue(ServerLevel level, ChunkPos chunk, String currentAge) {
        RetroGenerationData data = RetroGenerationData.get(level.getServer());
        for (RetroStructureHandler handler : RetroGenerationRegistry.handlers(level.registryAccess())) {
            if (handler.isUnlocked(currentAge) && handler.isApplicableToDimension(level.dimension().location())) {
                data.enqueue(level.dimension().location(), chunk, handler.id());
            }
        }
    }

    /** Runs one task without loading chunks, skipping claimed footprints and recording finished checks. */
    private static void processOne(MinecraftServer server, RetroGenerationData data) {
        RetroGenerationData.Task task = data.poll();
        if (task == null) {
            return;
        }

        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, task.dimension()));
        if (level == null || !level.getChunkSource().hasChunk(task.getChunkPos().x, task.getChunkPos().z)) {
            data.defer(task);
            return;
        }

        RetroStructureHandler handler = RetroGenerationRegistry.get(task.handlerId(), level.registryAccess());
        if (handler == null || !handler.isUnlocked(AgeSavedData.get(server).getCurrentAgeId())) {
            data.defer(task);
            return;
        }
        if (!handler.isApplicableToDimension(level.dimension().location())) {
            data.markCompleted(task);
            return;
        }

        try {
            RetroStructureHandler.Candidate candidate = handler.findCandidate(level, task.getChunkPos());
            if (candidate == null) {
                data.markCompleted(task);
                return;
            }

            if (ClaimProtection.hasClaimedChunk(
                level,
                candidate.minChunkX(),
                candidate.minChunkZ(),
                candidate.maxChunkX(),
                candidate.maxChunkZ()
            )) {
                data.defer(task);
                return;
            }

            if (candidate.placement().place()) {
                data.markCompleted(task);
            } else {
                data.defer(task);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Retro-generation handler {} failed at {} in {}", task.handlerId(), task.getChunkPos(), task.dimension(), exception);
            data.defer(task);
        }
    }
}