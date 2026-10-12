package dev.minecraftages.ages;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public interface RetroStructureHandler {
    /** Returns the unique registry ID used to persist and resume this handler's work. */
    ResourceLocation id();

    /** Returns the catalog age ID at which this handler's structures become available. */
    String unlockAgeId();

    /** Compares the world's current age with this handler's configured unlock age. */
    default boolean isUnlocked(String currentAgeId) {
        return AgeCatalog.isAtLeast(currentAgeId, unlockAgeId());
    }

    /** Finds a structure candidate starting from a loaded chunk, or null when none applies. */
    Candidate findCandidate(ServerLevel level, ChunkPos startChunk);

    /** Describes the chunk footprint protected from claims and the operation that places the candidate. */
    record Candidate(
        int minChunkX,
        int minChunkZ,
        int maxChunkX,
        int maxChunkZ,
        Placement placement
    ) {
        /** Rejects invalid footprints so claim checks cannot accidentally inspect an empty area. */
        public Candidate {
            if (minChunkX > maxChunkX || minChunkZ > maxChunkZ) {
                throw new IllegalArgumentException("Candidate chunk bounds are inverted");
            }
        }
    }

    @FunctionalInterface
    interface Placement {
        /** Places the candidate and returns true only when placement completed successfully. */
        boolean place();
    }
}