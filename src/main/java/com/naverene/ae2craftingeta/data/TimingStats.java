package com.naverene.ae2craftingeta.data;

import net.minecraft.nbt.NBTTagCompound;

/** What has been learned about producing one kind of item. */
public class TimingStats {

    /** Ticks from pushing one batch into an idle machine until its output came back. */
    public double latencyTicks;
    public int latencySamples;

    /** Items produced per tick while a job kept every machine for this step busy. */
    public double itemsPerTick;
    public int rateSamples;

    /** The last whole crafting job whose final output was this item. */
    public long lastJobAmount;
    public long lastJobMillis;

    public void addLatency(double ticks) {
        latencyTicks = blend(latencyTicks, ticks, latencySamples);
        latencySamples++;
    }

    public void addRate(double perTick) {
        itemsPerTick = blend(itemsPerTick, perTick, rateSamples);
        rateSamples++;
    }

    /** Plain average for the first few samples, then an exponential moving average so the value follows upgrades. */
    private static double blend(double current, double sample, int samples) {
        if (samples <= 0) {
            return sample;
        }
        double weight = samples < 5 ? 1.0 / (samples + 1) : 0.2;
        return current + (sample - current) * weight;
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setDouble("LatencyTicks", latencyTicks);
        tag.setInteger("LatencySamples", latencySamples);
        tag.setDouble("ItemsPerTick", itemsPerTick);
        tag.setInteger("RateSamples", rateSamples);
        tag.setLong("LastJobAmount", lastJobAmount);
        tag.setLong("LastJobMillis", lastJobMillis);
    }

    public static TimingStats fromNBT(NBTTagCompound tag) {
        TimingStats stats = new TimingStats();
        stats.latencyTicks = tag.getDouble("LatencyTicks");
        stats.latencySamples = tag.getInteger("LatencySamples");
        stats.itemsPerTick = tag.getDouble("ItemsPerTick");
        stats.rateSamples = tag.getInteger("RateSamples");
        stats.lastJobAmount = tag.getLong("LastJobAmount");
        stats.lastJobMillis = tag.getLong("LastJobMillis");
        return stats;
    }
}
