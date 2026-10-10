package com.naverene.ae2craftingeta.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

import com.naverene.ae2craftingeta.network.Network;

import appeng.api.storage.data.IAEStack;

/** Learned timings for the whole save, stored in the overworld's data folder. */
public class TimingStore extends WorldSavedData {

    public static final String NAME = "ae2craftingeta";

    private final TimingTable table = new TimingTable();

    public TimingStore(String name) {
        super(name);
    }

    /** Returns the store for the running server, or null when no world is loaded. */
    public static TimingStore get() {
        World world = DimensionManager.getWorld(0);
        if (world == null || world.mapStorage == null) {
            return null;
        }
        TimingStore store = (TimingStore) world.mapStorage.loadData(TimingStore.class, NAME);
        if (store == null) {
            store = new TimingStore(NAME);
            world.mapStorage.setData(NAME, store);
        }
        return store;
    }

    public TimingTable table() {
        return table;
    }

    public void recordLatency(IAEStack<?> stack, long ticks) {
        if (table.recordLatency(stack, ticks) != null) {
            markDirty();
        }
    }

    public void recordRate(IAEStack<?> stack, double itemsPerTick) {
        TimingStats entry = table.recordRate(stack, itemsPerTick);
        if (entry != null) {
            markDirty();
            Network.sendStatsToAll(StackKeys.of(stack), entry);
        }
    }

    public void recordJob(IAEStack<?> stack, long amount, long millis) {
        TimingStats entry = table.recordJob(stack, amount, millis);
        if (entry != null) {
            markDirty();
            Network.sendStatsToAll(StackKeys.of(stack), entry);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        table.readFromNBT(tag);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        table.writeToNBT(tag);
    }
}
