package mekanism.common.recipe.serializer;

import com.google.gson.JsonObject;
import java.util.Map;
import javax.annotation.Nonnull;
import mekanism.api.recipes.inputs.ItemStackIngredient;
import mekanism.common.Mekanism;
import mekanism.common.recipe.MekanismShapedRecipe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipe;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistryEntry;

public class MekanismShapedRecipeSerializer extends ForgeRegistryEntry<IRecipeSerializer<?>> implements IRecipeSerializer<MekanismShapedRecipe> {

    @Nonnull
    @Override
    public MekanismShapedRecipe read(@Nonnull ResourceLocation recipeId, @Nonnull JsonObject json) {
        return new MekanismShapedRecipe(IRecipeSerializer.CRAFTING_SHAPED.read(recipeId, json));
    }

    @Override
    public MekanismShapedRecipe read(@Nonnull ResourceLocation recipeId, @Nonnull PacketBuffer buffer) {
        try {
            return new MekanismShapedRecipe(IRecipeSerializer.CRAFTING_SHAPED.read(recipeId, buffer));
        } catch (Exception e) {
            Mekanism.logger.error("Error reading itemstack to itemstack recipe from packet.", e);
            throw e;
        }
    }

    @Override
    public void write(@Nonnull PacketBuffer buffer, @Nonnull MekanismShapedRecipe recipe) {
        try {
            IRecipeSerializer.CRAFTING_SHAPED.write(buffer, recipe.getInternal());
        } catch (Exception e) {
            Mekanism.logger.error("Error writing itemstack to itemstack recipe to packet.", e);
            throw e;
        }
    }
}