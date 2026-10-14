package dev.agesofelysium.ages;

import java.util.Collection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;

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
    static RetroStructureHandler get(ResourceLocation id, RegistryAccess registries) {
        RetroStructureHandler registered = HANDLERS.get(id);
        if (registered != null) {
            return registered;
        }
        RetroStructureHandler structureHandler = StructureSetRetroGenerationHandler.fromRegistry(registries, id);
        return structureHandler != null ? structureHandler : PlacedFeatureRetroGenerationHandler.fromTaskId(registries, id);
    }

    /** Returns custom handlers plus generic handlers derived from current age rules and registries. */
    static Collection<RetroStructureHandler> handlers(RegistryAccess registries) {
        List<RetroStructureHandler> handlers = new ArrayList<>(HANDLERS.values());
        Set<ResourceLocation> registeredIds = new HashSet<>(HANDLERS.keySet());
        for (StructureSetRetroGenerationHandler handler : StructureSetRetroGenerationHandler.discover(registries)) {
            if (registeredIds.add(handler.id())) {
                handlers.add(handler);
            }
        }
        for (PlacedFeatureRetroGenerationHandler handler : PlacedFeatureRetroGenerationHandler.discover(registries)) {
            if (registeredIds.add(handler.id())) {
                handlers.add(handler);
            }
        }
        return List.copyOf(handlers);
    }
}