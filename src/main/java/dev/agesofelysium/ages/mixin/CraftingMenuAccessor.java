package dev.agesofelysium.ages.mixin;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CraftingMenu.class)
public interface CraftingMenuAccessor {
    /** Exposes the crafting grid so age changes can recompute the menu result immediately. */
    @Accessor("craftSlots")
    CraftingContainer ages$getCraftSlots();
}