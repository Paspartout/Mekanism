package mekanism.generators.common.tile.fission;

import java.util.Collection;
import javax.annotation.Nonnull;
import mekanism.api.NBTConstants;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.providers.IBlockProvider;
import mekanism.api.text.EnumColor;
import mekanism.common.capabilities.heat.ITileHeatHandler;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableDouble;
import mekanism.common.inventory.container.sync.SyncableFluidStack;
import mekanism.common.inventory.container.sync.SyncableGasStack;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.container.sync.SyncableLong;
import mekanism.common.multiblock.IValveHandler;
import mekanism.common.multiblock.MultiblockManager;
import mekanism.common.multiblock.UpdateProtocol;
import mekanism.common.tile.prefab.TileEntityMultiblock;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.NBTUtils;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.content.fission.FissionReactorMultiblockData;
import mekanism.generators.common.content.fission.FissionReactorUpdateProtocol;
import mekanism.generators.common.content.fission.FissionReactorUpdateProtocol.FormedAssembly;
import mekanism.generators.common.registries.GeneratorsBlocks;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

public class TileEntityFissionReactorCasing extends TileEntityMultiblock<FissionReactorMultiblockData> implements IValveHandler {

    public float prevCoolantScale, prevFuelScale, prevHeatedCoolantScale, prevWasteScale;
    private boolean handleSound, prevBurning;

    public TileEntityFissionReactorCasing() {
        super(GeneratorsBlocks.FISSION_REACTOR_CASING);
    }

    public TileEntityFissionReactorCasing(IBlockProvider blockProvider) {
        super(blockProvider);
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        boolean needsPacket = false;
        boolean burning = structure != null && structure.handlesSound(this) && structure.isBurning();
        if (burning != prevBurning) {
            needsPacket = true;
            prevBurning = burning;
        }
        if (structure != null && isRendering) {
            // burn reactor fuel, create energy
            if (structure.isActive()) {
                structure.burnFuel();
            } else {
                structure.lastBurnRate = 0;
            }
            if (structure.isBurning() != structure.clientBurning) {
                needsPacket = true;
                structure.clientBurning = structure.isBurning();
            }
            // handle coolant heating (water -> steam)
            structure.handleCoolant();
            // external heat dissipation
            structure.lastEnvironmentLoss = structure.simulateEnvironment();
            // adjacent heat transfer
            structure.lastTransferLoss = 0;
            for (ValveData valve : structure.valves) {
                TileEntity tile = world.getTileEntity(valve.location.getPos());
                if (tile instanceof ITileHeatHandler) {
                    structure.lastTransferLoss += ((ITileHeatHandler) tile).simulateAdjacent();
                }
            }
            // update temperature
            structure.updateHeatCapacitors(null);
            structure.handleDamage(getWorld());

            // update scales
            float coolantScale = MekanismUtils.getScale(prevCoolantScale, structure.fluidCoolantTank);
            float fuelScale = MekanismUtils.getScale(prevFuelScale, structure.fuelTank);
            float steamScale = MekanismUtils.getScale(prevHeatedCoolantScale, structure.heatedCoolantTank), wasteScale = MekanismUtils.getScale(prevWasteScale, structure.wasteTank);
            if (coolantScale != prevCoolantScale || fuelScale != prevFuelScale || steamScale != prevHeatedCoolantScale || wasteScale != prevWasteScale) {
                needsPacket = true;
                prevCoolantScale = coolantScale;
                prevFuelScale = fuelScale;
                prevHeatedCoolantScale = steamScale;
                prevWasteScale = wasteScale;
            }
            // save changed data
            markDirty(false);
        }
        if (needsPacket) {
            sendUpdatePacket();
        }
    }

    public double getLastEnvironmentLoss() { return structure != null ? structure.lastEnvironmentLoss : 0; }
    public double getLastTransferLoss() { return structure != null ? structure.lastTransferLoss : 0; }
    public double getTemperature() { return structure != null ? structure.heatCapacitor.getTemperature() : 0; }
    public long getHeatCapacity() { return structure != null ? Math.round(structure.heatCapacitor.getHeatCapacity()) : 0; }
    public long getSurfaceArea() { return structure != null ? structure.surfaceArea : 0; }
    public double getBoilEfficiency() { return structure != null ? (double) Math.round(structure.getBoilEfficiency() * 1000) / 1000 : 0; }
    public long getLastBoilRate() { return structure != null ? structure.lastBoilRate : 0; }
    public double getLastBurnRate() { return structure != null ? structure.lastBurnRate : 0; }
    public long getMaxBurnRate() { return structure != null ? structure.fuelAssemblies * FissionReactorMultiblockData.BURN_PER_ASSEMBLY : 1; }
    public double getRateLimit() { return structure != null ? structure.rateLimit : 0; }
    public boolean isReactorActive() { return structure != null ? structure.isActive() : false; }
    public void setReactorActive(boolean active) { if (structure != null) structure.setActive(active); }

    public String getDamageString() {
        if (structure == null) return "0%";
        return Math.round((structure.reactorDamage / FissionReactorMultiblockData.MAX_DAMAGE) * 100) + "%";
    }
    public EnumColor getDamageColor() {
        if (structure == null) return EnumColor.BRIGHT_GREEN;
        double damage = structure.reactorDamage / FissionReactorMultiblockData.MAX_DAMAGE;
        return damage < 0.25 ? EnumColor.BRIGHT_GREEN : (damage < 0.5 ? EnumColor.YELLOW : (damage < 0.75 ? EnumColor.ORANGE : EnumColor.DARK_RED));
    }
    public EnumColor getTempColor() {
        double temp = getTemperature();
        return temp < 600 ? EnumColor.BRIGHT_GREEN : (temp < 1000 ? EnumColor.YELLOW :
              (temp < 1200 ? EnumColor.ORANGE : (temp < 1600 ? EnumColor.RED : EnumColor.DARK_RED)));
    }

    public void setRateLimitFromPacket(double rate) {
        if (structure != null) {
            structure.rateLimit = Math.min(getMaxBurnRate(), rate);
        }
        markDirty(false);
    }

    @Override
    public FissionReactorMultiblockData getNewStructure() {
        return new FissionReactorMultiblockData(this);
    }

    @Override
    public UpdateProtocol<FissionReactorMultiblockData> getProtocol() {
        return new FissionReactorUpdateProtocol(this);
    }

    @Override
    public MultiblockManager<FissionReactorMultiblockData> getManager() {
        return MekanismGenerators.fissionReactorManager;
    }

    @Override
    protected boolean canPlaySound() {
        return structure != null && structure.isBurning() && handleSound;
    }

    @Override
    public Collection<ValveData> getValveData() {
        return structure != null ? structure.valves : null;
    }

    @Nonnull
    @Override
    public CompoundNBT getReducedUpdateTag() {
        CompoundNBT updateTag = super.getReducedUpdateTag();
        updateTag.putBoolean(NBTConstants.HANDLE_SOUND, structure != null && structure.handlesSound(this));
        if (structure != null) {
            updateTag.putDouble(NBTConstants.BURNING, structure.lastBurnRate);
            if (isRendering) {
                updateTag.putFloat(NBTConstants.SCALE, prevCoolantScale);
                updateTag.putFloat(NBTConstants.SCALE_ALT, prevFuelScale);
                updateTag.putFloat(NBTConstants.SCALE_ALT_2, prevHeatedCoolantScale);
                updateTag.putFloat(NBTConstants.SCALE_ALT_3, prevWasteScale);
                updateTag.putInt(NBTConstants.VOLUME, structure.getVolume());
                updateTag.put(NBTConstants.FLUID_STORED, structure.fluidCoolantTank.getFluid().writeToNBT(new CompoundNBT()));
                updateTag.put(NBTConstants.GAS_STORED, structure.fuelTank.getStack().write(new CompoundNBT()));
                updateTag.put(NBTConstants.GAS_STORED_ALT, structure.heatedCoolantTank.getStack().write(new CompoundNBT()));
                updateTag.put(NBTConstants.GAS_STORED_ALT_2, structure.wasteTank.getStack().write(new CompoundNBT()));
                writeValves(updateTag);
                ListNBT list = new ListNBT();
                structure.assemblies.forEach(assembly -> list.add(assembly.write()));
                updateTag.put(NBTConstants.ASSEMBLIES, list);
            }
        }
        return updateTag;
    }

    @Override
    public void handleUpdateTag(@Nonnull CompoundNBT tag) {
        super.handleUpdateTag(tag);
        NBTUtils.setBooleanIfPresent(tag, NBTConstants.HANDLE_SOUND, value -> handleSound = value);
        if (clientHasStructure && structure != null) {
            NBTUtils.setDoubleIfPresent(tag, NBTConstants.BURNING, value -> structure.lastBurnRate = value);
            if (isRendering) {
                NBTUtils.setFloatIfPresent(tag, NBTConstants.SCALE, scale -> prevCoolantScale = scale);
                NBTUtils.setFloatIfPresent(tag, NBTConstants.SCALE_ALT, scale -> prevFuelScale = scale);
                NBTUtils.setFloatIfPresent(tag, NBTConstants.SCALE_ALT_2, scale -> prevHeatedCoolantScale = scale);
                NBTUtils.setFloatIfPresent(tag, NBTConstants.SCALE_ALT_3, scale -> prevWasteScale = scale);
                NBTUtils.setIntIfPresent(tag, NBTConstants.VOLUME, value -> structure.setVolume(value));
                NBTUtils.setFluidStackIfPresent(tag, NBTConstants.FLUID_STORED, value -> structure.fluidCoolantTank.setStack(value));
                NBTUtils.setGasStackIfPresent(tag, NBTConstants.GAS_STORED, value -> structure.fuelTank.setStack(value));
                NBTUtils.setGasStackIfPresent(tag, NBTConstants.GAS_STORED_ALT, value -> structure.heatedCoolantTank.setStack(value));
                NBTUtils.setGasStackIfPresent(tag, NBTConstants.GAS_STORED_ALT_2, value -> structure.wasteTank.setStack(value));
                readValves(tag);
                structure.assemblies.clear();
                if (tag.contains(NBTConstants.ASSEMBLIES)) {
                    ListNBT list = tag.getList(NBTConstants.ASSEMBLIES, NBT.TAG_COMPOUND);
                    for (int i = 0; i < list.size(); i++) {
                        structure.assemblies.add(FormedAssembly.read(list.getCompound(i)));
                    }
                }
            }
        }
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(() -> structure == null ? 0 : structure.getVolume(), value -> {
            if (structure != null) structure.setVolume(value);
        }));
        container.track(SyncableFluidStack.create(() -> structure == null ? FluidStack.EMPTY : structure.fluidCoolantTank.getFluid(), value -> {
            if (structure != null) structure.fluidCoolantTank.setStack(value);
        }));
        container.track(SyncableGasStack.create(() -> structure == null ? GasStack.EMPTY : structure.gasCoolantTank.getStack(), value -> {
            if (structure != null) structure.gasCoolantTank.setStack(value);
        }));
        container.track(SyncableGasStack.create(() -> structure == null ? GasStack.EMPTY : structure.fuelTank.getStack(), value -> {
            if (structure != null) structure.fuelTank.setStack(value);
        }));
        container.track(SyncableGasStack.create(() -> structure == null ? GasStack.EMPTY : structure.heatedCoolantTank.getStack(), value -> {
            if (structure != null) structure.heatedCoolantTank.setStack(value);
        }));
        container.track(SyncableGasStack.create(() -> structure == null ? GasStack.EMPTY : structure.wasteTank.getStack(), value -> {
            if (structure != null) structure.wasteTank.setStack(value);
        }));
        container.track(SyncableDouble.create(() -> structure == null ? 0 : structure.heatCapacitor.getHeat(), value -> {
            if (structure != null) structure.heatCapacitor.setHeat(value);
        }));
        container.track(SyncableDouble.create(() -> structure == null ? 0 : structure.heatCapacitor.getHeatCapacity(), value -> {
            if (structure != null) structure.heatCapacitor.setHeatCapacity(value, false);
        }));
        container.track(SyncableLong.create(this::getLastBoilRate, value -> {
            if (structure != null) structure.lastBoilRate = value;
        }));
        container.track(SyncableBoolean.create(this::isReactorActive, value -> {
            if (structure != null) structure.setActive(value);
        }));
        container.track(SyncableDouble.create(() -> structure == null ? 0 : structure.reactorDamage, value -> {
            if (structure != null) structure.reactorDamage = value;
        }));
        container.track(SyncableInt.create(() -> structure == null ? 0 : structure.fuelAssemblies, value -> {
            if (structure != null) structure.fuelAssemblies = value;
        }));
        container.track(SyncableDouble.create(() -> structure == null ? 0 : structure.lastBurnRate, value -> {
            if (structure != null) structure.lastBurnRate = value;
        }));
        container.track(SyncableDouble.create(() -> structure == null ? 0 : structure.rateLimit, value -> {
            if (structure != null) structure.rateLimit = value;
        }));
        container.track(SyncableInt.create(() -> structure == null ? 0 : structure.surfaceArea, value -> {
            if (structure != null) structure.surfaceArea = value;
        }));
        container.track(SyncableDouble.create(() -> structure == null ? 0 : structure.lastEnvironmentLoss, value -> {
            if (structure != null) structure.lastEnvironmentLoss = value;
        }));
        container.track(SyncableDouble.create(() -> structure == null ? 0 : structure.lastTransferLoss, value -> {
            if (structure != null) structure.lastTransferLoss = value;
        }));
    }
}
