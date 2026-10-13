package dev.agesofelysium.ages;

import net.minecraft.server.level.ServerLevel;

public final class ClaimProtection {
    private static volatile ClaimChecker checker = (level, minChunkX, minChunkZ, maxChunkX, maxChunkZ) -> false;

    /** Prevents instances; optional claim support is installed when its provider is available. */
    private ClaimProtection() {
    }

    /** Installs the claim provider; no provider means claims are not considered. */
    public static void register(ClaimChecker claimChecker) {
        checker = claimChecker;
    }

    /** Returns true when any chunk in the inclusive candidate footprint is claimed. */
    public static boolean hasClaimedChunk(
        ServerLevel level,
        int minChunkX,
        int minChunkZ,
        int maxChunkX,
        int maxChunkZ
    ) {
        return checker.hasClaimedChunk(level, minChunkX, minChunkZ, maxChunkX, maxChunkZ);
    }

    @FunctionalInterface
    public interface ClaimChecker {
        boolean hasClaimedChunk(
            ServerLevel level,
            int minChunkX,
            int minChunkZ,
            int maxChunkX,
            int maxChunkZ
        );
    }
}