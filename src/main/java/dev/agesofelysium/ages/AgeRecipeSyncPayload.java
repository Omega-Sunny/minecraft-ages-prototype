package dev.agesofelysium.ages;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sends locked recipe IDs, mod namespaces, and unlocked item exceptions to the client. */
public record AgeRecipeSyncPayload(
    List<ResourceLocation> lockedRecipeIds,
    List<String> lockedModIds,
    List<ResourceLocation> unlockedItemIds
) implements CustomPacketPayload {
    public static final Type<AgeRecipeSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(AgesMod.MOD_ID, "locked_recipes")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, AgeRecipeSyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ResourceLocation.STREAM_CODEC),
            AgeRecipeSyncPayload::lockedRecipeIds,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8),
            AgeRecipeSyncPayload::lockedModIds,
            ByteBufCodecs.collection(ArrayList::new, ResourceLocation.STREAM_CODEC),
            AgeRecipeSyncPayload::unlockedItemIds,
            AgeRecipeSyncPayload::new
        );

    /** Creates a payload from the currently locked recipe and mod IDs. */
    public static AgeRecipeSyncPayload fromRules(
        List<ResourceLocation> recipeIds,
        Set<String> modIds,
        Set<ResourceLocation> itemIds
    ) {
        return new AgeRecipeSyncPayload(
            List.copyOf(recipeIds),
            modIds.stream().sorted().toList(),
            itemIds.stream().sorted().toList()
        );
    }

    /** Returns the recipe IDs locked for the current age. */
    public List<ResourceLocation> getRecipeIds() {
        return lockedRecipeIds;
    }

    /** Returns the mod namespaces locked for the current age. */
    public Set<String> getModIds() {
        return Set.copyOf(lockedModIds);
    }

    /** Returns explicitly unlocked item IDs used as namespace-lock exceptions. */
    public Set<ResourceLocation> getItemIds() {
        return Set.copyOf(unlockedItemIds);
    }

    /** Returns this payload's registered network type. */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}