package dev.agesofelysium.ages.mixin;

import dev.agesofelysium.ages.AgeCatalog;
import dev.agesofelysium.ages.AgeSavedData;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorStructureMixin {
    /** Avoids an exhaustive locate scan when every requested structure is age-locked. */
    @Inject(method = "findNearestMapStructure", at = @At("HEAD"), cancellable = true)
    private void ages$skipLocateForLockedStructures(
        ServerLevel level,
        HolderSet<Structure> structures,
        BlockPos origin,
        int radius,
        boolean skipKnownStructures,
        CallbackInfoReturnable<Object> callback
    ) {
        String currentAgeId = AgeSavedData.get(level.getServer()).getCurrentAgeId();
        boolean allRequestedStructuresLocked = true;
        for (Holder<Structure> holder : structures) {
            ResourceLocation structureId = holder.unwrapKey()
                .map(key -> key.location())
                .orElse(null);
            if (structureId == null
                || !AgeCatalog.isStructureManaged(structureId)
                || AgeCatalog.isStructureUnlocked(structureId, currentAgeId)) {
                allRequestedStructuresLocked = false;
                break;
            }
        }

        if (allRequestedStructuresLocked) {
            callback.setReturnValue(null);
        }
    }

    /** Stops explicitly age-gated structures from being generated into new chunks too early. */
    @Inject(
        method = "tryGenerateStructure(Lnet/minecraft/world/level/levelgen/structure/StructureSet$StructureSelectionEntry;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;JLnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/core/SectionPos;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void ages$gateStructureWorldgen(
        StructureSet.StructureSelectionEntry entry,
        StructureManager structureManager,
        RegistryAccess registries,
        RandomState randomState,
        StructureTemplateManager templateManager,
        long seed,
        ChunkAccess chunk,
        ChunkPos chunkPos,
        SectionPos sectionPos,
        CallbackInfoReturnable<Boolean> callback
    ) {
        ResourceLocation structureId = entry.structure().unwrapKey()
            .map(key -> key.location())
            .orElse(null);
        if (structureId == null || !AgeCatalog.isStructureManaged(structureId)) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !AgeCatalog.isStructureUnlocked(
            structureId,
            AgeSavedData.get(server).getCurrentAgeId()
        )) {
            callback.setReturnValue(false);
        }
    }
}