package dev.minecraftages.ages;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = AgesMod.MOD_ID)
public final class AgeCommands {
    /** Prevents instances; command registration is static. */
    private AgeCommands() {
    }

    /** Registers age inspection and operator-only age changes with catalog-backed suggestions. */
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("age")
                .then(Commands.literal("get")
                    .executes(context -> {
                        String ageId = AgeSavedData.get(context.getSource().getServer()).getCurrentAgeId();
                        String ageName = AgeCatalog.get(ageId).map(AgeCatalog.Age::name).orElse(ageId);
                        context.getSource().sendSuccess(
                            () -> Component.literal("Current age: " + ageName),
                            false
                        );
                        return 1;
                    }))
                .then(Commands.literal("set")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("age", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                            AgeCatalog.getAges().stream().map(AgeCatalog.Age::id),
                            builder
                        ))
                        .executes(context -> {
                            String ageId = StringArgumentType.getString(context, "age");
                            AgeCatalog.Age age = AgeCatalog.get(ageId).orElse(null);
                            if (age == null) {
                                context.getSource().sendFailure(Component.literal("Unknown age ID: " + ageId));
                                return 0;
                            }
                            var server = context.getSource().getServer();
                            AgeSavedData savedData = AgeSavedData.get(server);
                            if (!savedData.getCurrentAgeId().equals(age.id())) {
                                savedData.setCurrentAgeId(age.id());
                                RetroGenerationScheduler.onAgeChanged(server, age.id());
                                AgeCraftingRules.refreshOpenCraftingMenus(server);
                                AgeRecipeBookSync.syncAll(server);
                                AgeRecipeNetworking.sendToAll(server);
                            }
                            context.getSource().sendSuccess(
                                () -> Component.literal("World age set to: " + age.name()),
                                true
                            );
                            return 1;
                        })))
        );
    }
}