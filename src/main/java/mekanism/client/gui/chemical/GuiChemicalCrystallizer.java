package mekanism.client.gui.chemical;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import mekanism.api.MekanismAPI;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.Slurry;
import mekanism.api.recipes.GasToItemStackRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiEnergyInfo;
import mekanism.client.gui.element.GuiProgress;
import mekanism.client.gui.element.GuiProgress.IProgressInfoHandler;
import mekanism.client.gui.element.GuiProgress.ProgressBar;
import mekanism.client.gui.element.GuiRedstoneControl;
import mekanism.client.gui.element.GuiSlot;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.client.gui.element.GuiSlot.SlotType;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.tab.GuiSecurityTab;
import mekanism.client.gui.element.tab.GuiSideConfigurationTab;
import mekanism.client.gui.element.tab.GuiTransporterConfigTab;
import mekanism.client.gui.element.tab.GuiUpgradeTab;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.tags.MekanismTags;
import mekanism.common.tile.TileEntityChemicalCrystallizer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import mekanism.common.util.text.EnergyDisplay;
import mekanism.common.util.text.TextComponentUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tags.Tag;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

public class GuiChemicalCrystallizer extends GuiMekanismTile<TileEntityChemicalCrystallizer, MekanismTileContainer<TileEntityChemicalCrystallizer>> {

    private List<ItemStack> iterStacks = new ArrayList<>();
    private ItemStack renderStack = ItemStack.EMPTY;
    private int stackSwitch = 0;
    private int stackIndex = 0;
    @Nonnull
    private Gas prevGas = MekanismAPI.EMPTY_GAS;

    public GuiChemicalCrystallizer(MekanismTileContainer<TileEntityChemicalCrystallizer> container, PlayerInventory inv, ITextComponent title) {
        super(container, inv, title);
    }

    @Override
    public void init() {
        super.init();
        addButton(new GuiSecurityTab<>(this, tile));
        addButton(new GuiRedstoneControl(this, tile));
        addButton(new GuiUpgradeTab(this, tile));
        addButton(new GuiVerticalPowerBar(this, tile, 160, 23));
        addButton(new GuiSideConfigurationTab(this, tile));
        addButton(new GuiTransporterConfigTab(this, tile));
        addButton(new GuiEnergyInfo(() -> Arrays.asList(MekanismLang.USING.translate(EnergyDisplay.of(tile.getEnergyPerTick())),
              MekanismLang.NEEDED.translate(EnergyDisplay.of(tile.getNeededEnergy()))), this));
        addButton(new GuiGasGauge(() -> tile.inputTank, GuiGauge.Type.STANDARD, this, 5, 4));
        addButton(new GuiSlot(SlotType.EXTRA, this, 5, 64).with(SlotOverlay.PLUS));
        addButton(new GuiSlot(SlotType.POWER, this, 154, 4).with(SlotOverlay.POWER));
        addButton(new GuiSlot(SlotType.OUTPUT, this, 130, 56));
        addButton(new GuiProgress(new IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return tile.getScaledProgress();
            }
        }, ProgressBar.LARGE_RIGHT, this, 51, 60));
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        drawString(tile.getName(), 37, 4, 0x404040);
        GasStack gasStack = tile.inputTank.getStack();
        if (!gasStack.isEmpty()) {
            drawString(TextComponentUtil.build(gasStack), 29, 15, 0x00CD00);
            if (gasStack.getType() instanceof Slurry && !renderStack.isEmpty()) {
                drawString(MekanismLang.GENERIC_PARENTHESIS.translate(renderStack), 29, 24, 0x00CD00);
            } else {
                CachedRecipe<GasToItemStackRecipe> recipe = tile.getUpdatedCache(0);
                if (recipe == null) {
                    drawString(MekanismLang.NO_RECIPE.translate(), 29, 24, 0x00CD00);
                } else {
                    drawString(MekanismLang.GENERIC_PARENTHESIS.translate(recipe.getRecipe().getOutput(gasStack)), 29, 24, 0x00CD00);
                }
            }
        }
        renderItem(renderStack, 131, 14);
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
    }

    @Override
    protected ResourceLocation getGuiLocation() {
        return MekanismUtils.getResource(ResourceType.GUI, "chemical_crystallizer.png");
    }

    @Nonnull
    private Gas getInputGas() {
        return tile.inputTank.getType();
    }

    private void resetStacks() {
        iterStacks.clear();
        renderStack = ItemStack.EMPTY;
        stackSwitch = 0;
        stackIndex = -1;
    }

    @Override
    public void tick() {
        super.tick();
        if (prevGas != getInputGas()) {
            prevGas = getInputGas();
            if (!prevGas.isEmptyType() && prevGas instanceof Slurry && !prevGas.isIn(MekanismTags.Gases.DIRTY_SLURRY)) {
                updateStackList(((Slurry) prevGas).getOreTag());
            } else {
                resetStacks();
            }
        }

        if (iterStacks.isEmpty()) {
            renderStack = ItemStack.EMPTY;
        } else {
            if (stackSwitch > 0) {
                stackSwitch--;
            }
            if (stackSwitch == 0) {
                stackSwitch = 20;
                if (stackIndex == -1 || stackIndex == iterStacks.size() - 1) {
                    stackIndex = 0;
                } else if (stackIndex < iterStacks.size() - 1) {
                    stackIndex++;
                }
                renderStack = iterStacks.get(stackIndex);
            }
        }
    }

    private void updateStackList(Tag<Item> oreTag) {
        iterStacks.clear();
        for (Item ore : oreTag.getAllElements()) {
            iterStacks.add(new ItemStack(ore));
        }
        stackSwitch = 0;
        stackIndex = -1;
    }
}