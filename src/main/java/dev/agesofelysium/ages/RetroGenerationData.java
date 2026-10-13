package dev.agesofelysium.ages;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

public final class RetroGenerationData extends SavedData {
    private static final String DATA_NAME = "ages_retro_generation";
    private static final String PENDING_KEY = "pending";
    private static final String COMPLETED_KEY = "completed";
    private static final Factory<RetroGenerationData> FACTORY = new Factory<>(
        RetroGenerationData::new,
        RetroGenerationData::load,
        DataFixTypes.LEVEL
    );

    private final LinkedHashMap<String, Task> pending = new LinkedHashMap<>();
    private final Set<String> completed = new HashSet<>();

    /** Gets the shared retro-generation queue stored with the overworld save data. */
    public static RetroGenerationData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** Restores queued and completed handler/chunk pairs from the saved world tag. */
    private static RetroGenerationData load(
        CompoundTag tag,
        net.minecraft.core.HolderLookup.Provider registries
    ) {
        RetroGenerationData data = new RetroGenerationData();
        ListTag pendingTags = tag.getList(PENDING_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < pendingTags.size(); index++) {
            Task task = Task.load(pendingTags.getCompound(index));
            if (task != null) {
                data.pending.put(task.key(), task);
            }
        }
        ListTag completedTags = tag.getList(COMPLETED_KEY, Tag.TAG_STRING);
        for (int index = 0; index < completedTags.size(); index++) {
            data.completed.add(completedTags.getString(index));
        }
        return data;
    }

    /** Creates an empty queue for a new world. */
    private RetroGenerationData() {
    }

    /** Adds work unless it is already pending or was previously completed. */
    public boolean enqueue(ResourceLocation dimension, ChunkPos chunk, ResourceLocation handlerId) {
        Task task = new Task(dimension, chunk.toLong(), handlerId);
        if (completed.contains(task.key()) || pending.putIfAbsent(task.key(), task) != null) {
            return false;
        }
        setDirty();
        return true;
    }

    /** Removes and returns the oldest pending task, or null when the queue is empty. */
    public Task poll() {
        if (pending.isEmpty()) {
            return null;
        }
        String firstKey = pending.keySet().iterator().next();
        Task task = pending.remove(firstKey);
        setDirty();
        return task;
    }

    /** Returns an unfinished task to the queue tail for a later tick or chunk-load retry. */
    public void defer(Task task) {
        if (!completed.contains(task.key()) && pending.putIfAbsent(task.key(), task) == null) {
            setDirty();
        }
    }

    /** Returns the queue size so deferred tasks are not retried repeatedly in the same tick. */
    public int pendingCount() {
        return pending.size();
    }

    /** Allows previously scanned chunks to be reconsidered after an age or rule change. */
    public void clearCompleted() {
        if (!completed.isEmpty()) {
            completed.clear();
            setDirty();
        }
    }

    /** Marks a task complete so future chunk loads do not repeat the same check. */
    public void markCompleted(Task task) {
        if (completed.add(task.key())) {
            setDirty();
        }
    }

    /** Serializes both pending tasks and completed keys into SavedData. */
    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag pendingTags = new ListTag();
        for (Task task : pending.values()) {
            pendingTags.add(task.save());
        }
        tag.put(PENDING_KEY, pendingTags);

        ListTag completedTags = new ListTag();
        for (String key : completed) {
            completedTags.add(net.minecraft.nbt.StringTag.valueOf(key));
        }
        tag.put(COMPLETED_KEY, completedTags);
        return tag;
    }

    public record Task(ResourceLocation dimension, long chunkPos, ResourceLocation handlerId) {
        /** Builds the stable deduplication key for this dimension/chunk/handler combination. */
        private String key() {
            return dimension + "|" + chunkPos + "|" + handlerId;
        }

        /** Encodes the task fields into NBT for persistence across server restarts. */
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", dimension.toString());
            tag.putLong("chunk", chunkPos);
            tag.putString("handler", handlerId.toString());
            return tag;
        }

        /** Decodes a saved task, returning null if its dimension or handler ID is malformed. */
        private static Task load(CompoundTag tag) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
            ResourceLocation handler = ResourceLocation.tryParse(tag.getString("handler"));
            if (dimension == null || handler == null) {
                return null;
            }
            return new Task(dimension, tag.getLong("chunk"), handler);
        }

        /** Reconstructs the chunk coordinate represented by the packed position value. */
        public ChunkPos getChunkPos() {
            return new ChunkPos(chunkPos);
        }
    }
}