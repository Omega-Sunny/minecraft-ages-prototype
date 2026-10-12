package dev.minecraftages.ages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mezz.jei.api.helpers.IPlatformFluidHelper;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;

@JeiPlugin
public final class AgesJeiPlugin implements IModPlugin {
    private final Runnable refreshListener = this::refreshRecipes;
    private IJeiRuntime runtime;
    private final Map<ResourceLocation, HiddenRecipe> hiddenRecipes = new HashMap<>();
    private final Set<net.minecraft.world.item.ItemStack> hiddenOutputs = new HashSet<>();
    private final Set<ItemStack> hiddenModItems = new HashSet<>();
    private final Set<FluidStack> hiddenModFluids = new HashSet<>();
    private final Set<RecipeType<?>> hiddenCategories = new HashSet<>();

    /** Returns the unique JEI plugin identifier for the Ages integration. */
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(AgesMod.MOD_ID, "jei_plugin");
    }

    /** Stores the active JEI runtime and applies the current server recipe lock state. */
    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        ClientAgeRecipeState.addListener(refreshListener);
        refreshRecipes();
    }

    /** Drops the JEI runtime and listener when JEI shuts down or reloads. */
    @Override
    public void onRuntimeUnavailable() {
        ClientAgeRecipeState.removeListener(refreshListener);
        if (runtime != null && !hiddenOutputs.isEmpty()) {
            runtime.getJeiHelpers().getIngredientVisibility().unhideIngredients(
                VanillaTypes.ITEM_STACK,
                hiddenOutputs,
                List.of(UidContext.Ingredient, UidContext.Recipe)
            );
        }
        if (runtime != null && !hiddenModFluids.isEmpty()) {
            IPlatformFluidHelper<FluidStack> fluidHelper = getFluidHelper();
            runtime.getJeiHelpers().getIngredientVisibility().unhideIngredients(
                fluidHelper.getFluidIngredientType(),
                hiddenModFluids,
                List.of(UidContext.Ingredient, UidContext.Recipe)
            );
        }
        runtime = null;
        hiddenRecipes.clear();
        hiddenOutputs.clear();
        hiddenModItems.clear();
        hiddenModFluids.clear();
        hiddenCategories.clear();
    }

    /** Diffs this plugin's hidden recipes against the server state and updates JEI. */
    private void refreshRecipes() {
        Minecraft minecraft = Minecraft.getInstance();
        if (runtime == null || minecraft.level == null) {
            return;
        }

        Set<ResourceLocation> lockedIds = ClientAgeRecipeState.getLockedRecipeIds();
        Set<String> lockedMods = ClientAgeRecipeState.getLockedModIds();
        Set<ResourceLocation> unlockedItems = ClientAgeRecipeState.getUnlockedItemIds();

        Map<ResourceLocation, RecipeHolder<?>> allRecipes = new HashMap<>();
        for (RecipeHolder<?> recipe : minecraft.level.getRecipeManager().getRecipes()) {
            allRecipes.put(recipe.id(), recipe);
        }
        Set<String> modNamespacesWithRecipeExceptions = new HashSet<>();
        for (RecipeHolder<?> recipe : allRecipes.values()) {
            if (lockedMods.contains(recipe.id().getNamespace()) && !lockedIds.contains(recipe.id())) {
                modNamespacesWithRecipeExceptions.add(recipe.id().getNamespace());
            }
        }

        Set<RecipeType<?>> currentlyHiddenCategories = new HashSet<>();
        runtime.getRecipeManager().createRecipeCategoryLookup().includeHidden().get()
            .map(category -> category.getRecipeType())
            .filter(type -> lockedMods.contains(type.getUid().getNamespace())
                && !modNamespacesWithRecipeExceptions.contains(type.getUid().getNamespace()))
            .forEach(currentlyHiddenCategories::add);

        for (RecipeType<?> previouslyHidden : hiddenCategories) {
            if (!currentlyHiddenCategories.contains(previouslyHidden)) {
                runtime.getRecipeManager().unhideRecipeCategory(previouslyHidden);
            }
        }
        for (RecipeType<?> newlyHidden : currentlyHiddenCategories) {
            if (!hiddenCategories.contains(newlyHidden)) {
                runtime.getRecipeManager().hideRecipeCategory(newlyHidden);
            }
        }
        hiddenCategories.clear();
        hiddenCategories.addAll(currentlyHiddenCategories);

        Set<ItemStack> currentlyHiddenModItems = runtime.getIngredientManager().getAllItemStacks().stream()
            .filter(stack -> lockedMods.contains(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()
            ) && !unlockedItems.contains(BuiltInRegistries.ITEM.getKey(stack.getItem())))
            .map(stack -> stack.copyWithCount(1))
            .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        updateHiddenItems(hiddenModItems, currentlyHiddenModItems);

        IPlatformFluidHelper<FluidStack> fluidHelper = getFluidHelper();
        Set<FluidStack> currentlyHiddenModFluids = runtime.getIngredientManager()
            .getAllIngredients(fluidHelper.getFluidIngredientType())
            .stream()
            .filter(stack -> lockedMods.contains(
                BuiltInRegistries.FLUID.getKey(stack.getFluid()).getNamespace()
            ))
            .map(FluidStack::copy)
            .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        updateHiddenFluids(fluidHelper, hiddenModFluids, currentlyHiddenModFluids);

        Set<ResourceLocation> currentlyLockedRecipeIds = new HashSet<>();
        Set<net.minecraft.world.item.ItemStack> currentlyLockedOutputs = new HashSet<>();
        Map<ResourceLocation, HiddenRecipe> currentlyHidden = new HashMap<>();
        for (RecipeHolder<?> recipe : allRecipes.values()) {
            if (lockedIds.contains(recipe.id())) {
                currentlyLockedRecipeIds.add(recipe.id());
                addJeiRecipeIfSupported(currentlyHidden, recipe);
            }
        }

        for (ResourceLocation recipeId : currentlyLockedRecipeIds) {
            RecipeHolder<?> recipe = allRecipes.get(recipeId);
            if (recipe == null) {
                continue;
            }
            net.minecraft.world.item.ItemStack output = recipe.value().getResultItem(minecraft.level.registryAccess());
            if (output.isEmpty()) {
                continue;
            }
            if (unlockedItems.contains(BuiltInRegistries.ITEM.getKey(output.getItem()))) {
                continue;
            }
            net.minecraft.world.item.ItemStack singleOutput = output.copyWithCount(1);
            if (currentlyLockedOutputs.stream().noneMatch(
                existing -> net.minecraft.world.item.ItemStack.isSameItemSameComponents(existing, singleOutput)
            )) {
                currentlyLockedOutputs.add(singleOutput);
            }
        }

        Set<net.minecraft.world.item.ItemStack> outputsToUnhide = new HashSet<>(hiddenOutputs);
        outputsToUnhide.removeIf(hidden -> currentlyLockedOutputs.stream().anyMatch(
            current -> net.minecraft.world.item.ItemStack.isSameItemSameComponents(hidden, current)
        ));
        if (!outputsToUnhide.isEmpty()) {
            runtime.getJeiHelpers().getIngredientVisibility().unhideIngredients(
                VanillaTypes.ITEM_STACK,
                outputsToUnhide,
                List.of(UidContext.Ingredient, UidContext.Recipe)
            );
        }

        Set<net.minecraft.world.item.ItemStack> outputsToHide = new HashSet<>(currentlyLockedOutputs);
        outputsToHide.removeIf(current -> hiddenOutputs.stream().anyMatch(
            hidden -> net.minecraft.world.item.ItemStack.isSameItemSameComponents(hidden, current)
        ));
        if (!outputsToHide.isEmpty()) {
            runtime.getJeiHelpers().getIngredientVisibility().hideIngredients(
                VanillaTypes.ITEM_STACK,
                outputsToHide,
                List.of(UidContext.Ingredient, UidContext.Recipe)
            );
        }
        hiddenOutputs.clear();
        hiddenOutputs.addAll(currentlyLockedOutputs);

        for (Map.Entry<ResourceLocation, HiddenRecipe> hidden : hiddenRecipes.entrySet()) {
            if (!currentlyHidden.containsKey(hidden.getKey())) {
                setJeiRecipeVisibility(hidden.getValue(), false);
            }
        }
        for (Map.Entry<ResourceLocation, HiddenRecipe> locked : currentlyHidden.entrySet()) {
            if (!hiddenRecipes.containsKey(locked.getKey())) {
                setJeiRecipeVisibility(locked.getValue(), true);
            }
        }

        hiddenRecipes.clear();
        hiddenRecipes.putAll(currentlyHidden);
    }

    /** Hides and restores item ingredients by namespace as the synchronized age locks change. */
    private void updateHiddenItems(Set<ItemStack> previouslyHidden, Set<ItemStack> currentlyHidden) {
        Set<ItemStack> toUnhide = new HashSet<>(previouslyHidden);
        toUnhide.removeIf(previous -> currentlyHidden.stream().anyMatch(
            current -> ItemStack.isSameItemSameComponents(previous, current)
        ));
        if (!toUnhide.isEmpty()) {
            runtime.getJeiHelpers().getIngredientVisibility().unhideIngredients(
                VanillaTypes.ITEM_STACK,
                toUnhide,
                List.of(UidContext.Ingredient, UidContext.Recipe)
            );
        }

        Set<ItemStack> toHide = new HashSet<>(currentlyHidden);
        toHide.removeIf(current -> previouslyHidden.stream().anyMatch(
            previous -> ItemStack.isSameItemSameComponents(previous, current)
        ));
        if (!toHide.isEmpty()) {
            runtime.getJeiHelpers().getIngredientVisibility().hideIngredients(
                VanillaTypes.ITEM_STACK,
                toHide,
                List.of(UidContext.Ingredient, UidContext.Recipe)
            );
        }

        previouslyHidden.clear();
        previouslyHidden.addAll(currentlyHidden);
    }

    /** Hides and restores fluid ingredients by namespace as the synchronized age locks change. */
    private void updateHiddenFluids(
        IPlatformFluidHelper<FluidStack> fluidHelper,
        Set<FluidStack> previouslyHidden,
        Set<FluidStack> currentlyHidden
    ) {
        Set<FluidStack> toUnhide = new HashSet<>(previouslyHidden);
        toUnhide.removeIf(previous -> currentlyHidden.stream().anyMatch(
            current -> FluidStack.isSameFluidSameComponents(previous, current)
        ));
        if (!toUnhide.isEmpty()) {
            runtime.getJeiHelpers().getIngredientVisibility().unhideIngredients(
                fluidHelper.getFluidIngredientType(),
                toUnhide,
                List.of(UidContext.Ingredient, UidContext.Recipe)
            );
        }

        Set<FluidStack> toHide = new HashSet<>(currentlyHidden);
        toHide.removeIf(current -> previouslyHidden.stream().anyMatch(
            previous -> FluidStack.isSameFluidSameComponents(previous, current)
        ));
        if (!toHide.isEmpty()) {
            runtime.getJeiHelpers().getIngredientVisibility().hideIngredients(
                fluidHelper.getFluidIngredientType(),
                toHide,
                List.of(UidContext.Ingredient, UidContext.Recipe)
            );
        }

        previouslyHidden.clear();
        previouslyHidden.addAll(currentlyHidden);
    }

    /** Narrows JEI's platform fluid helper to the NeoForge fluid-stack type. */
    @SuppressWarnings("unchecked")
    private IPlatformFluidHelper<FluidStack> getFluidHelper() {
        return (IPlatformFluidHelper<FluidStack>) runtime.getJeiHelpers().getPlatformFluidHelper();
    }

    private void addJeiRecipeIfSupported(
        Map<ResourceLocation, HiddenRecipe> hiddenRecipes,
        RecipeHolder<?> recipe
    ) {
        HiddenRecipe hiddenRecipe = findJeiRecipe(recipe);
        if (hiddenRecipe != null) {
            hiddenRecipes.put(recipe.id(), hiddenRecipe);
        }
    }

    private HiddenRecipe findJeiRecipe(RecipeHolder<?> recipe) {
        ResourceLocation recipeTypeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.value().getType());
        return runtime.getRecipeManager().getRecipeType(recipeTypeId)
            .<HiddenRecipe>map(type -> new HiddenRecipe(type, recipe))
            .orElse(null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void setJeiRecipeVisibility(HiddenRecipe hidden, boolean visible) {
        if (visible) {
            runtime.getRecipeManager().unhideRecipes((mezz.jei.api.recipe.RecipeType) hidden.type(), List.of(hidden.recipe()));
        } else {
            runtime.getRecipeManager().hideRecipes((mezz.jei.api.recipe.RecipeType) hidden.type(), List.of(hidden.recipe()));
        }
    }

    private record HiddenRecipe(mezz.jei.api.recipe.RecipeType<?> type, Object recipe) {
    }
}