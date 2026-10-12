package dev.minecraftages.ages;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;
import dev.minecraftages.ages.mixin.CraftingMenuAccessor;

public final class AgeCraftingRules {
    /** Prevents instances; this class only refreshes open crafting menus. */
    private AgeCraftingRules() {
    }

    /** Recalculates each open vanilla crafting grid after age or datapack rule changes. */
    public static void refreshOpenCraftingMenus(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof CraftingMenu menu) {
                CraftingContainer grid = ((CraftingMenuAccessor) menu).ages$getCraftSlots();
                menu.slotsChanged(grid);
            }
        }
    }
}