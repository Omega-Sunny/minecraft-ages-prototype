package dev.minecraftages.ages;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import xaero.pac.common.server.api.OpenPACServerAPI;

public final class ClaimProtection {
    /** Prevents instances; claim checks are stateless queries against Open Parties and Claims. */
    private ClaimProtection() {
    }

    /** Returns true when any chunk in the inclusive candidate footprint is claimed. */
    public static boolean hasClaimedChunk(
        ServerLevel level,
        int minChunkX,
        int minChunkZ,
        int maxChunkX,
        int maxChunkZ
    ) {
        MinecraftServer server = level.getServer();
        var claims = OpenPACServerAPI.get(server).getServerClaimsManager();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (claims.get(level.dimension().location(), new ChunkPos(chunkX, chunkZ)) != null) {
                    return true;
                }
            }
        }

        return false;
    }
}