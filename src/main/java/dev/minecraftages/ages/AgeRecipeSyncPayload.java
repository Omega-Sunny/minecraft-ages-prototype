package dev.minecraftages.ages;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sends the locked recipe IDs and mod namespaces to a client for UI and prediction filtering. */
public record AgeRecipeSyncPayload(String lockedRecipeIds, String lockedModIds, String unlockedItemIds) implements CustomPacketPayload {
    public static final Type<AgeRecipeSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(AgesMod.MOD_ID, "locked_recipes")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, AgeRecipeSyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            AgeRecipeSyncPayload::lockedRecipeIds,
            ByteBufCodecs.STRING_UTF8,
            AgeRecipeSyncPayload::lockedModIds,
            ByteBufCodecs.STRING_UTF8,
            AgeRecipeSyncPayload::unlockedItemIds,
            AgeRecipeSyncPayload::new
        );

    /** Encodes locked recipe IDs and mod IDs as compact comma-separated payload fields. */
    public static AgeRecipeSyncPayload fromRules(
        List<ResourceLocation> recipeIds,
        Set<String> modIds,
        Set<ResourceLocation> itemIds
    ) {
        return new AgeRecipeSyncPayload(
            String.join(",", recipeIds.stream().map(ResourceLocation::toString).toList()),
            String.join(",", modIds.stream().sorted().toList()),
            String.join(",", itemIds.stream().map(ResourceLocation::toString).sorted().toList())
        );
    }

    /** Parses the encoded recipe IDs, ignoring malformed values from an invalid packet. */
    public List<ResourceLocation> getRecipeIds() {
        if (lockedRecipeIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(lockedRecipeIds.split(","))
            .map(ResourceLocation::tryParse)
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    /** Parses the encoded locked mod namespaces. */
    public Set<String> getModIds() {
        if (lockedModIds.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(lockedModIds.split(","))
            .filter(id -> id.matches("[a-z0-9_.-]+"))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Parses explicitly unlocked item IDs used as exceptions to a namespace lock. */
    public Set<ResourceLocation> getItemIds() {
        if (unlockedItemIds.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(unlockedItemIds.split(","))
            .map(ResourceLocation::tryParse)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Returns this payload's registered network type. */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}