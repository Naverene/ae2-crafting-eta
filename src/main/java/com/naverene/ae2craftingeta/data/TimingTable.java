package com.naverene.ae2craftingeta.data;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import appeng.api.storage.data.IAEStack;
import appeng.util.Platform;

/** Learned timings per item. Used by the server's world data and by the client when the server lacks the mod. */
public class TimingTable {

    private final Map<IAEStack<?>, TimingStats> stats = new HashMap<>();

    public TimingStats find(IAEStack<?> stack) {
        IAEStack<?> key = StackKeys.of(stack);
        return key == null ? null : stats.get(key);
    }

    public Map<IAEStack<?>, TimingStats> all() {
        return stats;
    }

    /** Returns the updated entry, or null if the sample was ignored. */
    public TimingStats recordLatency(IAEStack<?> stack, long ticks) {
        IAEStack<?> key = StackKeys.of(stack);
        if (key == null || ticks <= 0) {
            return null;
        }
        TimingStats entry = stats.computeIfAbsent(key, k -> new TimingStats());
        entry.addLatency(ticks);
        return entry;
    }

    /** Returns the updated entry, or null if the sample was ignored. */
    public TimingStats recordRate(IAEStack<?> stack, double itemsPerTick) {
        IAEStack<?> key = StackKeys.of(stack);
        if (key == null || itemsPerTick <= 0) {
            return null;
        }
        TimingStats entry = stats.computeIfAbsent(key, k -> new TimingStats());
        entry.addRate(itemsPerTick);
        return entry;
    }

    /** Returns the updated entry, or null if the job was ignored. */
    public TimingStats recordJob(IAEStack<?> stack, long amount, long millis) {
        IAEStack<?> key = StackKeys.of(stack);
        if (key == null || amount <= 0 || millis <= 0) {
            return null;
        }
        TimingStats entry = stats.computeIfAbsent(key, k -> new TimingStats());
        entry.lastJobAmount = amount;
        entry.lastJobMillis = millis;
        return entry;
    }

    public void putAll(Map<IAEStack<?>, TimingStats> entries, boolean replaceAll) {
        if (replaceAll) {
            stats.clear();
        }
        stats.putAll(entries);
    }

    public void readFromNBT(NBTTagCompound tag) {
        putAll(readEntries(tag), true);
    }

    public void writeToNBT(NBTTagCompound tag) {
        writeEntries(stats, tag);
    }

    public static Map<IAEStack<?>, TimingStats> readEntries(NBTTagCompound tag) {
        Map<IAEStack<?>, TimingStats> entries = new HashMap<>();
        NBTTagList list = tag.getTagList("Entries", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            IAEStack<?> key = StackKeys.of(Platform.readStackNBT(entry.getCompoundTag("Stack")));
            if (key != null) {
                entries.put(key, TimingStats.fromNBT(entry));
            }
        }
        return entries;
    }

    public static void writeEntries(Map<IAEStack<?>, TimingStats> entries, NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<IAEStack<?>, TimingStats> e : entries.entrySet()) {
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
