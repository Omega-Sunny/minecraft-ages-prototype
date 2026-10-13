package dev.agesofelysium.ages;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class AgeSavedData extends SavedData {
    private static final String DATA_NAME = "ages_world_state";
    private static final String AGE_KEY = "current_age";
    private static final Factory<AgeSavedData> FACTORY = new Factory<>(
        AgeSavedData::new,
        AgeSavedData::load,
        DataFixTypes.LEVEL
    );

    private String currentAgeId = "";

    /** Reads the shared age from overworld SavedData and migrates legacy display-name values. */
    public static AgeSavedData get(MinecraftServer server) {
        AgeSavedData data = server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        data.migrateLegacyAge();
        return data;
    }

    /** Restores the saved age ID, defaulting safely when older or empty data has no value. */
    private static AgeSavedData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        AgeSavedData data = new AgeSavedData();
        data.currentAgeId = tag.getString(AGE_KEY);
        return data;
    }

    /** Creates world state that is initialized from the loaded catalog on first access. */
    private AgeSavedData() {
    }

    /** Returns the persisted stable ID for the world's current age. */
    public String getCurrentAgeId() {
        return currentAgeId;
    }

    /** Stores a known age ID and marks the world data dirty for saving. */
    public void setCurrentAgeId(String ageId) {
        if (!AgeCatalog.isKnown(ageId)) {
            throw new IllegalArgumentException("Unknown age ID: " + ageId);
        }
        if (currentAgeId.equals(ageId)) {
            return;
        }
        currentAgeId = ageId;
        setDirty();
    }

    /** Converts values written by earlier prototypes from display names to IDs when possible. */
    private void migrateLegacyAge() {
        if (AgeCatalog.isKnown(currentAgeId)) {
            return;
        }
        String migratedId = AgeCatalog.findIdByDisplayName(currentAgeId)
            .orElseGet(() -> AgeCatalog.getDefaultAgeId().orElse(null));
        if (migratedId != null) {
            currentAgeId = migratedId;
            setDirty();
        }
    }

    /** Serializes the current age ID into the world's SavedData tag. */
    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        tag.putString(AGE_KEY, currentAgeId);
        return tag;
    }
}