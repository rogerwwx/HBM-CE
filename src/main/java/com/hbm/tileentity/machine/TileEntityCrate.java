package com.hbm.tileentity.machine;

import com.hbm.blocks.generic.BlockStorageCrate;
import com.hbm.config.MachineConfig;
import com.hbm.config.ServerConfig;
import com.hbm.hazard.HazardSystem;
import com.hbm.items.ModItems;
import com.hbm.items.tool.ItemKeyPin;
import com.hbm.lib.HBMSoundHandler;
import com.hbm.lib.InventoryHelper;
import com.hbm.lib.Library;
import com.hbm.main.MainRegistry;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.IPersistentNBT;
import com.hbm.tileentity.machine.storage.TileEntityCrateBase;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.WorldServer;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class TileEntityCrate extends TileEntityCrateBase implements IGUIProvider, IPersistentNBT {

    public float fillPercentage = 0.0F;
    protected String name;
    private boolean destroyedByCreativePlayer = false;
    private boolean suppressInventoryCallbacks = false;
    private final AtomicBoolean sizeCheckRunning = new AtomicBoolean(false);
    private volatile boolean sizeCheckQueued = false;
    private int occupiedSlotCount = 0;
    private double totalRadiation = 0D;
    private NBTTagCompound persistentInventoryData = new NBTTagCompound();
    private final double[] slotRadiation;
    public transient ItemStack boundItem = ItemStack.EMPTY;

    private record CrateDropData(NBTTagCompound persistentData, double radiation) {
    }

    public TileEntityCrate(int scount, String name) {
        super(scount);
        this.name = name;
        this.slotRadiation = new double[scount];
    }

    @Override
    protected ItemStackHandler getNewInventory(int scount, int slotlimit) {
        return new ItemStackHandler(scount) {
            @Override
            public @NotNull ItemStack getStackInSlot(int slot) {
                ensureFilled();
                return super.getStackInSlot(slot);
            }

            @Override
            public void setStackInSlot(int slot, @NotNull ItemStack stack) {
                ensureFilled();
                super.setStackInSlot(slot, stack);
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                if (!boundItem.isEmpty() && (stack == boundItem || ItemStack.areItemStacksEqual(stack, boundItem))) {
                    return false;
                }
                return super.isItemValid(slot, stack);
            }


            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                if (suppressInventoryCallbacks) {
                    return;
                }
                updateCachedSlot(slot);
                updateDisplayedFillPercentage();
                markDirty();
                syncBoundItemSlot(slot);
                scheduleAsyncSizeCheck();
            }

            @Override
            public int getSlotLimit(int slot) {
                return slotlimit;
            }
        };
    }

    public void flushPendingByteAudit() {
        if (world == null) {
            return;
        }
        scheduleAsyncSizeCheck();
    }

    private void scheduleAsyncSizeCheck() {
        sizeCheckQueued = true;
        if (!sizeCheckRunning.compareAndSet(false, true)) {
            return;
        }
        sizeCheckQueued = false;
        CompletableFuture.supplyAsync(this::getSize).whenComplete((currentSize, error) ->
                enqueueSizeCheckCallback(() -> {
                    try {
                        if (error != null) {
                            MainRegistry.logger.error("Failed to calculate crate size at {}", pos, error);
                            return;
                        }
                        if (world == null) {
                            return;
                        }

                        if (!world.isRemote && requiresByteAudit() && currentSize > MachineConfig.crateByteSize) {
                            ejectAndClearInventory();
                            return;
                        }

                        this.fillPercentage = getCompressedFillPercentage(currentSize);
                        if (!world.isRemote && world.getTileEntity(pos) == this) {
                            networkPackNT(10);
                        }
                    } finally {
                        sizeCheckRunning.set(false);
                        if (sizeCheckQueued) {
                            scheduleAsyncSizeCheck();
                        }
                    }
                }));
    }

    private void enqueueSizeCheckCallback(Runnable task) {
        if (world == null) {
            task.run();
            return;
        }
        if (world.isRemote) {
            Minecraft.getMinecraft().addScheduledTask(task);
            return;
        }
        if (world instanceof WorldServer worldServer) {
            worldServer.addScheduledTask(task);
            return;
        }
        task.run();
    }

    private void rebuildCachedState() {
        persistentInventoryData = new NBTTagCompound();
        Arrays.fill(slotRadiation, 0D);
        occupiedSlotCount = 0;
        totalRadiation = 0D;
        int slots = inventory.getSlots();
        for (int i = 0; i < slots; i++) {
            updateCachedSlot(i);
        }
        updateDisplayedFillPercentage();
    }

    private void updateCachedSlot(int slot) {
        String key = getSlotKey(slot);
        boolean hadStack = persistentInventoryData.hasKey(key);
        ItemStack stack = inventory.getStackInSlot(slot);
        double newRadiation = 0D;

        if (stack.isEmpty()) {
            if (hadStack) {
                persistentInventoryData.removeTag(key);
                occupiedSlotCount--;
            }
        } else {
            NBTTagCompound slotTag = new NBTTagCompound();
            stack.writeToNBT(slotTag);
            persistentInventoryData.setTag(key, slotTag);
            if (!hadStack) {
                occupiedSlotCount++;
            }
            newRadiation = HazardSystem.getTotalRadsFromStack(stack) * stack.getCount();
        }

        totalRadiation += newRadiation - slotRadiation[slot];
        slotRadiation[slot] = newRadiation;
    }

    private void updateDisplayedFillPercentage() {
        int totalSlots = inventory.getSlots();
        fillPercentage = totalSlots <= 0 ? 0.0F : occupiedSlotCount * 100F / totalSlots;
    }

    private static float getCompressedFillPercentage(long currentSize) {
        if (currentSize <= 0L || MachineConfig.crateByteSize <= 0) {
            return 0.0F;
        }
        return (float) currentSize / MachineConfig.crateByteSize * 100F;
    }

    private void syncBoundItemSlot(int slot) {
        if (boundItem.isEmpty()) {
            return;
        }

        NBTTagCompound root = boundItem.hasTagCompound() ? boundItem.getTagCompound() : new NBTTagCompound();
        NBTTagCompound data = root.hasKey(NBT_PERSISTENT_KEY) ? root.getCompoundTag(
                NBT_PERSISTENT_KEY) : new NBTTagCompound();
        applyCachedSlotToPersistentData(data, slot);
        applyLockData(data);

        if (data.isEmpty()) {
            root.removeTag(NBT_PERSISTENT_KEY);
        } else {
            root.setTag(NBT_PERSISTENT_KEY, data);
        }

        applyRadiationData(root);
        setBoundItemTag(root);
    }

    private void syncBoundItemAll() {
        if (boundItem.isEmpty()) {
            return;
        }
        NBTTagCompound root = boundItem.hasTagCompound() ? boundItem.getTagCompound() : new NBTTagCompound();
        applyDropData(root, buildDropData());
        setBoundItemTag(root);
    }

    private void setBoundItemTag(NBTTagCompound root) {
        if (root.isEmpty()) {
            boundItem.setTagCompound(null);
        } else {
            boundItem.setTagCompound(root);
        }
    }

    private void applyCachedSlotToPersistentData(NBTTagCompound data, int slot) {
        String key = getSlotKey(slot);
        if (persistentInventoryData.hasKey(key)) {
            data.setTag(key, persistentInventoryData.getTag(key).copy());
        } else {
            data.removeTag(key);
        }
    }

    private void applyLockData(NBTTagCompound data) {
        if (this.isLocked()) {
            data.setInteger("lock", this.getPins());
            data.setDouble("lockMod", this.getMod());
        } else {
            data.removeTag("lock");
            data.removeTag("lockMod");
        }
    }

    private void applyRadiationData(NBTTagCompound root) {
        if (totalRadiation > 0D) {
            root.setDouble(BlockStorageCrate.CRATE_RAD_KEY, totalRadiation);
        } else {
            root.removeTag(BlockStorageCrate.CRATE_RAD_KEY);
        }
    }

    private boolean requiresByteAudit() {
        return !boundItem.isEmpty() || this.isLocked() || ServerConfig.CRATE_KEEP_CONTENTS.get();
    }

    private static String getSlotKey(int slot) {
        return "slot" + slot;
    }

    private void ejectAndClearInventory() {
        InventoryHelper.dropInventoryItems(world, pos, this);
        suppressInventoryCallbacks = true;
        try {
            for (int i = 0; i < inventory.getSlots(); i++) {
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        } finally {
            suppressInventoryCallbacks = false;
        }
        rebuildCachedState();
        sizeCheckQueued = false;
        super.markDirty();
        syncBoundItemAll();
        if (world != null && !world.isRemote && world.getTileEntity(pos) == this) {
            networkPackNT(10);
        }
        MainRegistry.logger.debug("Crate at {} was oversized and has been emptied to prevent data corruption.", pos);
    }

    public long getSize() {
        return Library.getCompressedNbtSize(assembleDropTag(buildDropData()));
    }

    private CrateDropData buildDropData() {
        NBTTagCompound persistentData = persistentInventoryData.copy();
        applyLockData(persistentData);
        return new CrateDropData(persistentData, totalRadiation);
    }

    private static NBTTagCompound assembleDropTag(CrateDropData data) {
        NBTTagCompound root = new NBTTagCompound();
        if (!data.persistentData.isEmpty()) {
            root.setTag(NBT_PERSISTENT_KEY, data.persistentData.copy());
        }
        if (data.radiation > 0D) {
            root.setDouble(BlockStorageCrate.CRATE_RAD_KEY, data.radiation);
        }
        return root;
    }

    private static void applyDropData(NBTTagCompound nbt, CrateDropData data) {
        if (!data.persistentData.isEmpty()) nbt.setTag(NBT_PERSISTENT_KEY, data.persistentData);
        else nbt.removeTag(NBT_PERSISTENT_KEY);

        if (data.radiation > 0D) nbt.setDouble(BlockStorageCrate.CRATE_RAD_KEY, data.radiation);
        else nbt.removeTag(BlockStorageCrate.CRATE_RAD_KEY);
    }

    @Override
    public boolean canAccess(EntityPlayer player) {

        if (!this.isLocked() || player == null) {
            return true;
        } else {
            ItemStack stack = player.getHeldItemMainhand();

            if (stack.getItem() instanceof ItemKeyPin && ItemKeyPin.getPins(stack) == this.lock) {
                world.playSound(null, player.posX, player.posY, player.posZ, HBMSoundHandler.lockOpen,
                        SoundCategory.BLOCKS, 1.0F, 1.0F);
                return true;
            }

            if (stack.getItem() == ModItems.key_red) {
                world.playSound(null, player.posX, player.posY, player.posZ, HBMSoundHandler.lockOpen,
                        SoundCategory.BLOCKS, 1.0F, 1.0F);
                return true;
            }

            return this.tryPick(player);
        }
    }

    @Override
    public @NotNull String getName() {
        return this.hasCustomName() ? this.customName : name;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        suppressInventoryCallbacks = true;
        try {
            super.readFromNBT(compound);
        } finally {
            suppressInventoryCallbacks = false;
        }
        rebuildCachedState();
        scheduleAsyncSizeCheck();
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setFloat("fill", fillPercentage);
        return compound;
    }

    @Override
    public void writeNBT(NBTTagCompound nbt) {
        CrateDropData data = buildDropData();
        NBTTagCompound dropTag = assembleDropTag(data);
        if (world != null && !world.isRemote && Library.getCompressedNbtSize(dropTag) > MachineConfig.crateByteSize) {
            ejectAndClearInventory();
            applyDropData(nbt, new CrateDropData(new NBTTagCompound(), 0D));
            return;
        }
        applyDropData(nbt, data);
    }

    @Override
    public void readNBT(NBTTagCompound nbt) {
        NBTTagCompound data = nbt.hasKey(NBT_PERSISTENT_KEY) ? nbt.getCompoundTag(NBT_PERSISTENT_KEY) : nbt;
        if (data.hasKey("lock")) {
            this.setPins(data.getInteger("lock"));
            this.setMod(data.getDouble("lockMod"));
            this.lock();
        }
        suppressInventoryCallbacks = true;
        try {
            for (int i = 0; i < inventory.getSlots(); i++) {
                String key = "slot" + i;
                if (data.hasKey(key)) {
                    inventory.setStackInSlot(i, new ItemStack(data.getCompoundTag(key)));
                } else {
                    inventory.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        } finally {
            suppressInventoryCallbacks = false;
        }
        rebuildCachedState();
        scheduleAsyncSizeCheck();
    }

    @Override
    public void setDestroyedByCreativePlayer() {
        destroyedByCreativePlayer = true;
    }

    @Override
    public boolean isDestroyedByCreativePlayer() {
        return destroyedByCreativePlayer;
    }

    @Override
    public void serialize(ByteBuf buf) {
        buf.writeFloat(this.fillPercentage);
    }

    @Override
    public void deserialize(ByteBuf buf) {
        this.fillPercentage = buf.readFloat();
    }

    @Override
    protected boolean checkLock(EnumFacing facing) {
        return facing == null || !isLocked();
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        if (boundItem != null && !boundItem.isEmpty()) {
            return player.getHeldItemMainhand() == boundItem;
        }
        return super.isUseableByPlayer(player);
    }
}
