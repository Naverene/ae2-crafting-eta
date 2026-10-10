package com.naverene.ae2craftingeta.data;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;

import com.naverene.ae2craftingeta.network.Network;

import appeng.api.storage.data.IAEStack;
import appeng.util.Platform;

/** Learned timings for the whole save, stored in the overworld's data folder. */
public class TimingStore extends WorldSavedData {

    public static final String NAME = "ae2craftingeta";

    private final Map<IAEStack<?>, TimingStats> stats = new HashMap<>();

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

    public TimingStats find(IAEStack<?> stack) {
        IAEStack<?> key = StackKeys.of(stack);
        return key == null ? null : stats.get(key);
    }

    public Map<IAEStack<?>, TimingStats> all() {
        return stats;
    }

    public void recordLatency(IAEStack<?> stack, long ticks) {
        IAEStack<?> key = StackKeys.of(stack);
        if (key == null || ticks <= 0) {
            return;
        }
        stats.computeIfAbsent(key, k -> new TimingStats())
            .addLatency(ticks);
        markDirty();
    }

    public void recordRate(IAEStack<?> stack, double itemsPerTick) {
        IAEStack<?> key = StackKeys.of(stack);
        if (key == null || itemsPerTick <= 0) {
            return;
        }
        TimingStats entry = stats.computeIfAbsent(key, k -> new TimingStats());
        entry.addRate(itemsPerTick);
        markDirty();
        Network.sendStatsToAll(key, entry);
    }

    public void recordJob(IAEStack<?> stack, long amount, long millis) {
        IAEStack<?> key = StackKeys.of(stack);
        if (key == null || amount <= 0 || millis <= 0) {
            return;
        }
        TimingStats entry = stats.computeIfAbsent(key, k -> new TimingStats());
        entry.lastJobAmount = amount;
        entry.lastJobMillis = millis;
        markDirty();
        Network.sendStatsToAll(key, entry);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        stats.clear();
        NBTTagList list = tag.getTagList("Entries", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            IAEStack<?> key = StackKeys.of(Platform.readStackNBT(entry.getCompoundTag("Stack")));
            if (key != null) {
                stats.put(key, TimingStats.fromNBT(entry));
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<IAEStack<?>, TimingStats> e : stats.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setTag(
                "Stack",
                e.getKey()
                    .toNBTGeneric());
            e.getValue()
                .writeToNBT(entry);
            list.appendTag(entry);
        }
        tag.setTag("Entries", list);
    }
}
