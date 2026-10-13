package dev.agesofelysium.ages;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import xaero.pac.common.server.api.OpenPACServerAPI;

public final class OpenPacClaimProtection implements ClaimProtection.ClaimChecker {
    public OpenPacClaimProtection() {
    }

    @Override
    public boolean hasClaimedChunk(
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