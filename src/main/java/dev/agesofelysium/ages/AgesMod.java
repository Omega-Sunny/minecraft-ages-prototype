package dev.agesofelysium.ages;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(AgesMod.MOD_ID)
public final class AgesMod {
    public static final String MOD_ID = "agesofelysium";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** NeoForge entry point; feature setup is currently registered through event subscribers. */
    public AgesMod(IEventBus modEventBus) {
        if (ModList.get().isLoaded("openpartiesandclaims")) {
            try {
                Class<?> integrationClass = Class.forName("dev.agesofelysium.ages.OpenPacClaimProtection");
                ClaimProtection.ClaimChecker checker = (ClaimProtection.ClaimChecker)integrationClass
                    .getDeclaredConstructor()
                    .newInstance();
                ClaimProtection.register(checker);
            } catch (ReflectiveOperationException | LinkageError | ClassCastException exception) {
                LOGGER.warn("Open Parties and Claims was detected, but claim protection could not be initialized", exception);
            }
        }
    }
}