package dev.minecraftages.ages;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(AgesMod.MOD_ID)
public final class AgesMod {
    public static final String MOD_ID = "ages";

    /** NeoForge entry point; feature setup is currently registered through event subscribers. */
    public AgesMod(IEventBus modEventBus) {
    }
}