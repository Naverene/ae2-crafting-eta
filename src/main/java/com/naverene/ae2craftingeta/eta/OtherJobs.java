package com.naverene.ae2craftingeta.eta;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.naverene.ae2craftingeta.data.CpuTracker;
import com.naverene.ae2craftingeta.data.StackKeys;
import com.naverene.ae2craftingeta.mixins.late.AccessorCraftingCPUCluster;
import com.naverene.ae2craftingeta.mixins.late.AccessorTaskProgress;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;

/**
 * Work that the other busy crafting CPUs on the network have for each interface. A job sharing machines with other
 * jobs has to wait for them: what is already inside the machines goes first, and work still queued on both CPUs takes
 * turns, since AE2 lets every CPU push each tick.
 */
final class OtherJobs {

    static final OtherJobs NONE = new OtherJobs();

    /** Per interface, {machine ticks of work already pushed into it, machine ticks of work still to be pushed}. */
    private final Map<ICraftingMedium, double[]> load = new IdentityHashMap<>();
    private int jobs;

    private OtherJobs() {}

    /**
     * @param except the CPU the estimate is for, or null for a job that has not started yet
     * @param latency machine ticks for one batch of a pattern, given the interfaces it can go to
     */
    static OtherJobs on(CraftingGridCache cache, Object except, PatternLatency latency) {
        OtherJobs result = new OtherJobs();
        for (ICraftingCPU cpu : cache.getCpus()) {
            if (cpu == except || !(cpu instanceof CraftingCPUCluster cluster) || !cluster.isBusy()) {
                continue;
            }
            if (result.add(cluster, cache, latency)) {
                result.jobs++;
            }
        }
        return result;
    }

    /** Adds one CPU's work. Returns whether it had any. */
    private boolean add(CraftingCPUCluster cpu, CraftingGridCache cache, PatternLatency latency) {
        boolean any = false;
        for (Map.Entry<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> e : ((AccessorCraftingCPUCluster) cpu)
            .ae2craftingeta$getTasks()
            .entrySet()) {
            long left = ((AccessorTaskProgress) e.getValue()).ae2craftingeta$getValue();
            List<ICraftingMedium> mediums = cache.getMediums(e.getKey());
            if (left <= 0 || mediums == null || mediums.isEmpty()) {
                continue;
            }
            spread(mediums, left * latency.ticks(e.getKey(), mediums), 1);
            any = true;
        }
        for (CpuTracker.InFlight flight : CpuTracker.of(cpu)
            .inFlight()) {
            Set<ICraftingMedium> mediums = flight.mediums;
            if (mediums.isEmpty()) {
                continue;
            }
            List<ICraftingMedium> all = cache.getMediums(flight.details);
            double perBatch = latency.ticks(flight.details, all);
            IAEStack<?> output = StackKeys.primaryOutput(flight.details);
            long batchSize = output == null ? 1 : Math.max(1, output.getStackSize());
            long batches = (flight.items + batchSize - 1) / batchSize;
            // The machines have been working since the oldest batch was pushed, so that much of it is already done.
            double perMachine = Math.max(0, batches * perBatch / mediums.size() - flight.ticksSincePush);
            spread(mediums, perMachine * mediums.size(), 0);
            any = true;
        }
        return any;
    }

    private void spread(Collection<ICraftingMedium> mediums, double ticks, int slot) {
        if (ticks <= 0) {
            return;
        }
        double each = ticks / mediums.size();
        for (ICraftingMedium medium : mediums) {
            load.computeIfAbsent(medium, k -> new double[2])[slot] += each;
        }
    }

    /** How many other jobs have work anywhere on the network. */
    int jobs() {
        return jobs;
    }

    /**
     * Ticks a step has to wait for other jobs on the interfaces it uses, spread over all of them.
     *
     * @return {ticks of other jobs already in those machines, ticks of other jobs still to be pushed to them}
     */
    double[] waitFor(List<ICraftingMedium> mediums) {
        double[] wait = new double[2];
        if (load.isEmpty() || mediums == null || mediums.isEmpty()) {
            return wait;
        }
        for (ICraftingMedium medium : mediums) {
            double[] work = load.get(medium);
            if (work != null) {
                wait[0] += work[0];
                wait[1] += work[1];
            }
        }
        wait[0] /= mediums.size();
        wait[1] /= mediums.size();
        return wait;
    }

    interface PatternLatency {

        double ticks(ICraftingPatternDetails details, List<ICraftingMedium> mediums);
    }
}
