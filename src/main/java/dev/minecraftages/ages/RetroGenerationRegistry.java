package dev.minecraftages.ages;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

public final class RetroGenerationRegistry {
    private static final Map<ResourceLocation, RetroStructureHandler> HANDLERS = new ConcurrentHashMap<>();

    /** Prevents instances; integrations register handlers in this shared registry. */
    private RetroGenerationRegistry() {
    }

    /** Registers one handler per ID and fails fast on duplicate integration IDs. */
    public static void register(RetroStructureHandler handler) {
        RetroStructureHandler previous = HANDLERS.putIfAbsent(handler.id(), handler);
        if (previous != null) {
            throw new IllegalStateException("A retro-generation handler is already registered for " + handler.id());
        }
    }

    /** Finds the handler needed to resume a persisted task, or null if it is unavailable. */
    static RetroStructureHandler get(ResourceLocation id) {
        return HANDLERS.get(id);
    }

    /** Returns the registered handlers used when discovering retro-generation work. */
    static Collection<RetroStructureHandler> handlers() {
        return HANDLERS.values();
    }
}