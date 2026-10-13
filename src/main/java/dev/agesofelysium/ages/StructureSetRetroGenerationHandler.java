package dev.agesofelysium.ages;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

public final class StructureSetRetroGenerationHandler implements RetroStructureHandler {
    private final ResourceLocation id;
    private final List<ResourceLocation> structureIds;

    public StructureSetRetroGenerationHandler(ResourceLocation id, List<ResourceLocation> structureIds) {
        this.id = id;
        this.structureIds = List.copyOf(structureIds);
    }

    /** Discovers structure sets containing at least one structure with an age rule. */
    static List<StructureSetRetroGenerationHandler> discover(RegistryAccess registries) {
        Registry<StructureSet> structureSets = registries.registryOrThrow(Registries.STRUCTURE_SET);
        List<StructureSetRetroGenerationHandler> handlers = new ArrayList<>();
        for (ResourceLocation setId : structureSets.keySet()) {
            StructureSetRetroGenerationHandler handler = fromRegistry(registries, setId);
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return List.copyOf(handlers);
    }

    /** Resolves a handler for a persisted structure-set ID if it still has configured members. */
    @Nullable
    static StructureSetRetroGenerationHandler fromRegistry(RegistryAccess registries, ResourceLocation setId) {
        Registry<StructureSet> structureSets = registries.registryOrThrow(Registries.STRUCTURE_SET);
        StructureSet structureSet = structureSets.get(setId);
        if (structureSet == null) {
            return null;
        }
        List<ResourceLocation> managedIds = structureSet.structures().stream()
            .map(entry -> entry.structure().unwrapKey().map(key -> key.location()).orElse(null))
            .filter(java.util.Objects::nonNull)
            .filter(AgeCatalog::isStructureManaged)
            .toList();
        return managedIds.isEmpty() ? null : new StructureSetRetroGenerationHandler(setId, managedIds);
    }

    @Override
    public ResourceLocation id() {
        return id;
    }

    @Override
    public boolean isUnlocked(String currentAgeId) {
        return structureIds.stream().anyMatch(structureId -> AgeCatalog.isStructureUnlocked(structureId, currentAgeId));
    }

    @Override
    @Nullable
    public Candidate findCandidate(ServerLevel level, ChunkPos startChunk) {
        Registry<StructureSet> structureSets = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        StructureSet structureSet = structureSets.get(id);
        if (structureSet == null) {
            return null;
        }

        List<Structure> structures = structureSet.structures().stream()
            .map(entry -> entry.structure().value())
            .toList();
        LevelChunk startLevelChunk = level.getChunkSource().getChunkNow(startChunk.x, startChunk.z);
        if (startLevelChunk == null) {
            return null;
        }
        for (Structure structure : structures) {
            StructureStart existingStart = startLevelChunk.getStartForStructure(structure);
            if (existingStart != null && existingStart.isValid()) {
                return null;
            }
        }

        ServerChunkCache chunkSource = level.getChunkSource();
        ChunkGenerator generator = chunkSource.getGenerator();
        RandomState randomState = chunkSource.randomState();
        ChunkGeneratorStructureState structureState = generator.createState(
            level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET),
            randomState,
            level.getSeed()
        );
        boolean isPossibleStructureChunk = structureState.possibleStructureSets().stream()
            .map(Holder::value)
            .anyMatch(possibleSet -> possibleSet == structureSet
                && possibleSet.placement().isStructureChunk(structureState, startChunk.x, startChunk.z));
        if (!isPossibleStructureChunk) {
            return null;
        }

        List<StructureSet.StructureSelectionEntry> remainingEntries = new ArrayList<>(structureSet.structures());
        WorldgenRandom selectionRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        selectionRandom.setLargeFeatureSeed(level.getSeed(), startChunk.x, startChunk.z);
        int remainingWeight = remainingEntries.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
        String currentAgeId = AgeSavedData.get(level.getServer()).getCurrentAgeId();

        while (!remainingEntries.isEmpty()) {
            int selectedWeight = selectionRandom.nextInt(remainingWeight);
            int selectedIndex = 0;
            while (selectedWeight >= remainingEntries.get(selectedIndex).weight()) {
                selectedWeight -= remainingEntries.get(selectedIndex).weight();
                selectedIndex++;
            }
            StructureSet.StructureSelectionEntry selectedEntry = remainingEntries.remove(selectedIndex);
            remainingWeight -= selectedEntry.weight();

            Structure structure = selectedEntry.structure().value();
            ResourceLocation structureId = selectedEntry.structure().unwrapKey()
                .map(key -> key.location())
                .orElse(null);
            if (structureId == null || !AgeCatalog.isStructureUnlocked(structureId, currentAgeId)) {
                continue;
            }

            StructureStart existingStart = startLevelChunk.getStartForStructure(structure);
            int references = existingStart == null ? 0 : existingStart.getReferences();
            StructureStart start = structure.generate(
                level.registryAccess(),
                generator,
                generator.getBiomeSource(),
                randomState,
                level.getStructureManager(),
                level.getSeed(),
                startChunk,
                references,
                level,
                structure.biomes()::contains
            );
            if (!start.isValid()) {
                continue;
            }

            BoundingBox footprint = start.getBoundingBox();
            int minChunkX = Math.floorDiv(footprint.minX(), 16);
            int minChunkZ = Math.floorDiv(footprint.minZ(), 16);
            int maxChunkX = Math.floorDiv(footprint.maxX(), 16);
            int maxChunkZ = Math.floorDiv(footprint.maxZ(), 16);
            Set<Structure> structureSetMembers = new HashSet<>(structures);
            return new Candidate(
                minChunkX,
                minChunkZ,
                maxChunkX,
                maxChunkZ,
                () -> placeIfSafe(level, structure, structureSetMembers, start, startChunk, footprint)
            );
        }

        return null;
    }

    private static boolean placeIfSafe(
        ServerLevel level,
        Structure structure,
        Set<Structure> structureSetMembers,
        StructureStart start,
        ChunkPos startChunk,
        BoundingBox footprint
    ) {
        List<LevelChunk> chunks = new ArrayList<>();
        int minChunkX = Math.floorDiv(footprint.minX(), 16);
        int minChunkZ = Math.floorDiv(footprint.minZ(), 16);
        int maxChunkX = Math.floorDiv(footprint.maxX(), 16);
        int maxChunkZ = Math.floorDiv(footprint.maxZ(), 16);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    return false;
                }
                boolean hasExistingStructure = level.structureManager()
                    .startsForStructure(chunk.getPos(), structureSetMembers::contains)
                    .stream()
                    .anyMatch(StructureStart::isValid);
                if (hasExistingStructure) {
                    return true;
                }
                chunks.add(chunk);
            }
        }

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        for (LevelChunk chunk : chunks) {
            ChunkPos chunkPos = chunk.getPos();
            BoundingBox chunkBounds = new BoundingBox(
                chunkPos.getMinBlockX(),
                level.getMinBuildHeight(),
                chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX(),
                level.getMaxBuildHeight() - 1,
                chunkPos.getMaxBlockZ()
            );
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureSeed(level.getSeed(), chunkPos.x, chunkPos.z);
            start.placeInChunk(level, level.structureManager(), generator, random, chunkBounds, chunkPos);
        }

        LevelChunk startChunkAccess = level.getChunkSource().getChunkNow(startChunk.x, startChunk.z);
        if (startChunkAccess == null) {
            return false;
        }
        StructureManager structureManager = level.structureManager();
        structureManager.setStartForStructure(
            net.minecraft.core.SectionPos.bottomOf(startChunkAccess),
            structure,
            start,
            startChunkAccess
        );
        for (LevelChunk chunk : chunks) {
            if (!chunk.getPos().equals(startChunk)) {
                structureManager.addReferenceForStructure(
                    net.minecraft.core.SectionPos.bottomOf(chunk),
                    structure,
                    startChunk.toLong(),
                    chunk
                );
            }
        }
        return true;
    }
}