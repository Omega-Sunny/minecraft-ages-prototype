package dev.minecraftages.ages;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@EventBusSubscriber(modid = AgesMod.MOD_ID)
public final class AgeCatalogReload {
    /** Prevents instances; reload registration is handled by the static event subscriber. */
    private AgeCatalogReload() {
    }

    /** Adds the age JSON loader to each server datapack reload. */
    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new AgeCatalog.ReloadListener());
    }
}