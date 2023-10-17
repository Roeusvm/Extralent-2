package com.extralent.common.block.FuseMachine;

import com.extralent.api.tools.EEnergyStorage;
import com.extralent.api.tools.IGuiTile;
import com.extralent.api.tools.IRestorableTileEntity;
import com.extralent.api.tools.RecipeAPI;
import com.extralent.common.config.FuseMachineConfig;
import com.extralent.common.recipe.RecipeHandler;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TileFuseMachine extends TileEntity implements ITickable, IRestorableTileEntity, IGuiTile {

    public static final int INPUT_SLOTS = 2;
    public static final int OUTPUT_SLOTS = 1;
    public static final int SIZE = INPUT_SLOTS + OUTPUT_SLOTS;

    private int progress = 0;
    private MachineState state = MachineState.NOPOWER;

    private int clientProgress = -1;
    private int clientEnergy = -1;

    @Override
    public void update() {
        if (!world.isRemote) {
            if (energyStorage.getEnergyStored() < FuseMachineConfig.RF_PER_TICK) {
                setState(MachineState.NOPOWER);
                return;
            }
            if (progress > 0) {
                setState(MachineState.ON);
                energyStorage.consumePower(FuseMachineConfig.RF_PER_TICK);
                progress--;
                if (progress == 0) {
                    attemptFusing();
                }
                markDirty();
            } else {
                startFusing();
            }
        }
    }

    private boolean insertOutput(ItemStack output, boolean simulate) {
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            ItemStack remaining = outputHandler.insertItem(i, output, simulate);
            if (remaining.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void startFusing() {
        ItemStack input0 = inputHandler.getStackInSlot(0);
        ItemStack input1 = inputHandler.getStackInSlot(1);

        if (input0.isEmpty() || input1.isEmpty()) {
            setState(MachineState.OFF);
            return;
        }

        RecipeAPI recipe = RecipeHandler.getRecipeForInput(inputHandler);
        if (recipe == null) {
            setState(MachineState.OFF);
            return;
        }

        ItemStack result = recipe.getCraftingResult(inputHandler);
        if (insertOutput(result.copy(), true)) {
            setState(MachineState.ON);
            progress = FuseMachineConfig.MAX_PROGRESS;
            markDirty();
        }
    }

    private void attemptFusing() {
        ItemStack input0 = inputHandler.getStackInSlot(0);
        ItemStack input1 = inputHandler.getStackInSlot(1);

        if (input0.isEmpty() || input1.isEmpty()) {
            setState(MachineState.OFF);
            return;
        }

        RecipeAPI recipe = RecipeHandler.getRecipeForInput(inputHandler);
        if (recipe == null) {
            setState(MachineState.OFF);
            return;
        }

        ItemStack result = recipe.getCraftingResult(inputHandler);
        if (insertOutput(result.copy(), false)) {
            inputHandler.extractItem(0, 1, false);
            inputHandler.extractItem(1, 1, false);
        }
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int getClientProgress() {
        return clientProgress;
    }

    public void setClientProgress(int clientProgress) {
        this.clientProgress = clientProgress;
    }

    public int getClientEnergy() {
        return clientEnergy;
    }

    public void setClientEnergy(int clientEnergy) {
        this.clientEnergy = clientEnergy;
    }

    public int getEnergy() {
        return energyStorage.getEnergyStored();
    }

    //------------------------------------------------------------------------

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound nbtTag = super.getUpdateTag();
        nbtTag.setInteger("state", state.ordinal());
        return nbtTag;
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 1, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        int stateIndex = packet.getNbtCompound().getInteger("state");

        if (world.isRemote && stateIndex != state.ordinal()) {
            state = MachineState.VALUES[stateIndex];
            world.markBlockRangeForRenderUpdate(pos, pos);
        }
    }

    public void setState(MachineState state) {
        if (this.state != state) {
            this.state = state;
            markDirty();
            IBlockState blockState = world.getBlockState(pos);
            getWorld().notifyBlockUpdate(pos, blockState, blockState, 3);
        }
    }

    public MachineState getState() {
        return state;
    }

    //------------------------------------------------------------------------

    // This item handler will hold our three input slots
    private ItemStackHandler inputHandler = new ItemStackHandler(INPUT_SLOTS) {
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return slot < 2;
        }

        @Override
        protected void onContentsChanged(int slot) {
            TileFuseMachine.this.markDirty();
        }
    };

    // This item handler will hold our three output slots
    private ItemStackHandler outputHandler = new ItemStackHandler(OUTPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            TileFuseMachine.this.markDirty();
        }
    };

    private CombinedInvWrapper combinedHandler = new CombinedInvWrapper(inputHandler, outputHandler);

    //------------------------------------------------------------------------

    private EEnergyStorage energyStorage = new EEnergyStorage(FuseMachineConfig.MAX_POWER, FuseMachineConfig.RF_PER_TICK_INPUT);

    //------------------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        readRestorableFromNBT(compound);
        state = MachineState.VALUES[compound.getInteger("state")];
    }

    @Override
    public void readRestorableFromNBT(NBTTagCompound compound) {
        if (compound.hasKey("itemsIn")) {
            inputHandler.deserializeNBT((NBTTagCompound) compound.getTag("itemsIn"));
        }
        if (compound.hasKey("itemsOut")) {
            outputHandler.deserializeNBT((NBTTagCompound) compound.getTag("itemsOut"));
        }
        progress = compound.getInteger("progress");
        energyStorage.setEnergy(compound.getInteger("energy"));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        writeRestorableToNBT(compound);
        compound.setInteger("state", state.ordinal());
        return compound;
    }

    @Override
    public void writeRestorableToNBT(NBTTagCompound compound) {
        compound.setTag("itemsIn", inputHandler.serializeNBT());
        compound.setTag("itemsOut", outputHandler.serializeNBT());
        compound.setInteger("progress", progress);
        compound.setInteger("energy", energyStorage.getEnergyStored());
    }

    public boolean canInteractWith(EntityPlayer playerIn) {
        // If we are too far away from this tile entity you cannot use it
        return !isInvalid() && playerIn.getDistanceSq(pos.add(0.5D, 0.5D, 0.5D)) <= 64D;
    }

    @Override
    public Container createContainer(EntityPlayer player) {
        return new ContainerFuseMachine(player.inventory, this);
    }

    @Override
    public GuiContainer createGui(EntityPlayer player) {
        return new GuiFuseMachine(this, new ContainerFuseMachine(player.inventory, this));
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
        if (capability == CapabilityEnergy.ENERGY) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (facing == null) {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(combinedHandler);
            } else if (facing == EnumFacing.UP) {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inputHandler);
            } else {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(outputHandler);
            }
        }
        if (capability == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.cast(energyStorage);
        }
        return super.getCapability(capability, facing);
    }
}
