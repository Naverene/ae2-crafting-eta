package com.naverene.ae2craftingeta.eta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import net.minecraft.world.World;

import com.google.common.collect.ImmutableCollection;
import com.naverene.ae2craftingeta.Config;
import com.naverene.ae2craftingeta.data.CpuTracker;
import com.naverene.ae2craftingeta.data.StackKeys;
import com.naverene.ae2craftingeta.data.TimingStats;
import com.naverene.ae2craftingeta.data.TimingStore;
import com.naverene.ae2craftingeta.machine.MachineStatus;
import com.naverene.ae2craftingeta.machine.MachineTimes;
import com.naverene.ae2craftingeta.mixins.late.AccessorCraftingCPUCluster;
import com.naverene.ae2craftingeta.mixins.late.AccessorTaskProgress;

import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.v2.CraftingJobV2;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.resolvers.CraftableItemResolver.CraftFromPatternTask;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.crafting.TileMolecularAssembler;

/**
 * Estimates how long a set of pattern batches will take.
 *
 * <p>
 * Each pattern is a step with a latency L (ticks for one batch) and a throughput r (items per tick with all its
 * machines busy). A step can start once each ingredient step has delivered its first batch, and cannot finish until
 * its last ingredients have arrived plus one more batch. The CPU itself can push at most (co-processors + 1) batches
 * per tick. The estimate is the latest step finish, or the CPU's push limit if that is slower.
 */
public final class EtaCalculator {

    private static final int[] ASSEMBLER_SPEED = TileMolecularAssembler.SPEED;

    private EtaCalculator() {}

    public static EtaResult forPlan(ICraftingJob<?> job, ICraftingGrid grid, World world, int coProcessors) {
        IItemList<IAEStack<?>> plan = AEApi.instance()
            .storage()
            .createAEStackList();
        job.populatePlan(plan);

        Map<ICraftingPatternDetails, Long> batches = new IdentityHashMap<>();
        for (IAEStack<?> stack : plan) {
            long toCraft = stack.getCountRequestable();
            if (toCraft <= 0) {
                continue;
            }
            ImmutableCollection<ICraftingPatternDetails> patterns = grid.getCraftingFor(stack, null, 0, world);
            if (patterns == null || patterns.isEmpty()) {
                continue;
            }
            ICraftingPatternDetails details = patterns.iterator()
                .next();
            long perBatch = outputAmount(details, stack);
            if (perBatch <= 0) {
                continue;
            }
            long needed = (toCraft + perBatch - 1) / perBatch;
            // A pattern with several outputs shows up once per output; it runs as many times as its largest need.
            batches.merge(details, needed, Math::max);
        }
        return estimate(batches, Collections.emptyMap(), grid, coProcessors, serverLookup());
    }

    /**
     * Estimate from the crafting tree AE2 sends to the client for the plan screen. Used when the server does not have
     * this mod, so there is no grid to ask about machines.
     */
    public static EtaResult forTree(CraftingJobV2<?> job, int coProcessors, Function<IAEStack<?>, TimingStats> lookup) {
        Map<ICraftingPatternDetails, Long> batches = new HashMap<>();
        if (job.originalRequest != null) {
            collectTree(job.originalRequest, batches, Collections.newSetFromMap(new IdentityHashMap<>()));
        }
        return estimate(batches, Collections.emptyMap(), null, coProcessors, lookup);
    }

    private static void collectTree(CraftingRequest request, Map<ICraftingPatternDetails, Long> batches,
        Set<CraftingRequest> seen) {
        if (!seen.add(request)) {
            return;
        }
        for (CraftingRequest.UsedResolverEntry entry : request.usedResolvers) {
            if (entry.task instanceof CraftFromPatternTask task) {
                if (task.getTotalCraftsDone() > 0) {
                    batches.merge(task.pattern, task.getTotalCraftsDone(), Long::sum);
                }
                for (CraftingRequest child : task.getChildRequests()) {
                    collectTree(child, batches, seen);
                }
            }
        }
    }

    public static EtaResult forRunningCpu(CraftingCPUCluster cpu) {
        Map<ICraftingPatternDetails, Long> batches = new IdentityHashMap<>();
        for (Map.Entry<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> e : ((AccessorCraftingCPUCluster) cpu)
            .ae2craftingeta$getTasks()
            .entrySet()) {
            long remaining = ((AccessorTaskProgress) e.getValue()).ae2craftingeta$getValue();
            if (remaining > 0) {
                batches.put(e.getKey(), remaining);
            }
        }
        // Batches already pushed are off the task list, but a machine handed a stack still runs once per item, so
        // they count as work left, minus however long they have already been in there.
        CpuTracker tracker = CpuTracker.of(cpu);
        Map<ICraftingPatternDetails, long[]> started = new IdentityHashMap<>();
        for (CpuTracker.InFlight flight : tracker.inFlight()) {
            IAEStack<?> output = StackKeys.primaryOutput(flight.details);
            long perBatch = output == null ? 1 : Math.max(1, output.getStackSize());
            long pushed = (flight.items + perBatch - 1) / perBatch;
            long[] entry = started.computeIfAbsent(flight.details, k -> new long[2]);
            entry[0] += pushed;
            entry[1] = Math.max(entry[1], flight.ticksSincePush);
        }
        ICraftingGrid grid = cpu.getGrid() == null ? null
            : cpu.getGrid()
                .getCache(ICraftingGrid.class);
        EtaResult queued = estimate(batches, started, grid, cpu.getCoProcessors(), serverLookup());

        // The machine itself says how long its current run has left; never show less than that.
        long inMachines = 0;
        int busyOutputs = 0;
        for (List<MachineStatus> statuses : tracker.machines()
            .values()) {
            busyOutputs++;
            for (MachineStatus status : statuses) {
                inMachines = Math.max(inMachines, status.ticksLeft());
            }
        }
        if (inMachines <= queued.ticks) {
            return queued;
        }
        return new EtaResult(inMachines, queued.unknownSteps, Math.max(queued.totalSteps, busyOutputs));
    }

    /**
     * Rough estimate for a running CPU seen only from the client: AE2 sends per-item scheduled and in-progress counts
     * but not which pattern feeds which, so this is the slowest single item rather than the full chain.
     *
     * @param counts per item, {scheduled, in progress}
     */
    public static EtaResult forCpuEntries(Map<IAEStack<?>, long[]> counts, Function<IAEStack<?>, TimingStats> lookup) {
        double slowest = 0;
        int unknown = 0;
        int steps = 0;
        for (Map.Entry<IAEStack<?>, long[]> e : counts.entrySet()) {
            long scheduled = e.getValue()[0];
            long active = e.getValue()[1];
            if (scheduled <= 0 && active <= 0) {
                continue;
            }
            steps++;
            TimingStats stats = lookup.apply(e.getKey());
            double ticks;
            if (stats != null && stats.rateSamples > 0) {
                double latency = stats.latencySamples > 0 ? stats.latencyTicks : 0;
                // Items in progress have been handed to a machine, not finished: they still take machine time.
                ticks = latency + (scheduled + active) / stats.itemsPerTick;
            } else if (stats != null && stats.latencySamples > 0) {
                ticks = stats.latencyTicks;
            } else {
                ticks = Config.unknownBatchSeconds * 20.0;
                unknown++;
            }
            slowest = Math.max(slowest, ticks);
        }
        return new EtaResult((long) Math.ceil(slowest), unknown, steps);
    }

    private static Function<IAEStack<?>, TimingStats> serverLookup() {
        TimingStore store = TimingStore.get();
        return store == null ? stack -> null : store.table()::find;
    }

    /**
     * @param batches batches still to push, per pattern
     * @param started per pattern, {batches pushed to machines but not back yet, ticks since the oldest was pushed}
     */
    static EtaResult estimate(Map<ICraftingPatternDetails, Long> batches, Map<ICraftingPatternDetails, long[]> started,
        ICraftingGrid grid, int coProcessors, Function<IAEStack<?>, TimingStats> lookup) {
        if (batches.isEmpty() && started.isEmpty()) {
            return new EtaResult(0, 0, 0);
        }

        // Which step produces each item, so ingredients can be linked to the steps that make them.
        Map<IAEStack<?>, Step> producers = new HashMap<>();
        List<Step> steps = new ArrayList<>();
        Set<ICraftingPatternDetails> patterns = Collections.newSetFromMap(new IdentityHashMap<>());
        patterns.addAll(batches.keySet());
        patterns.addAll(started.keySet());
        for (ICraftingPatternDetails details : patterns) {
            Long queued = batches.get(details);
            long[] inFlight = started.get(details);
            Step step = new Step(
                details,
                queued == null ? 0 : queued,
                inFlight == null ? 0 : inFlight[0],
                inFlight == null ? 0 : inFlight[1]);
            if (step.batches + step.pushed <= 0) {
                continue;
            }
            steps.add(step);
            for (IAEStack<?> out : details.getCondensedAEOutputs()) {
                IAEStack<?> key = StackKeys.of(out);
                if (key != null) {
                    producers.putIfAbsent(key, step);
                }
            }
        }
        if (steps.isEmpty()) {
            return new EtaResult(0, 0, 0);
        }

        int unknown = 0;
        long totalBatches = 0;
        for (Step step : steps) {
            // Only batches the CPU still has to push count against its push limit.
            totalBatches += step.batches;
            for (IAEStack<?> in : step.details.getCondensedAEInputs()) {
                Step producer = producers.get(StackKeys.of(in));
                if (producer != null && producer != step) {
                    step.inputs.add(producer);
                }
            }
            if (!timeStep(step, lookup, grid)) {
                unknown++;
            }
        }

        double finish = 0;
        Map<Step, double[]> memo = new IdentityHashMap<>();
        for (Step step : steps) {
            finish = Math.max(finish, schedule(step, memo, new HashSet<>())[1]);
        }
        double cpuLimit = (double) totalBatches / (Math.max(0, coProcessors) + 1);
        return new EtaResult((long) Math.ceil(Math.max(finish, cpuLimit)), unknown, steps.size());
    }

    /** Fills in latency and throughput for a step. Returns false when nothing was known and a guess was used. */
    private static boolean timeStep(Step step, Function<IAEStack<?>, TimingStats> lookup, ICraftingGrid grid) {
        IAEStack<?> output = StackKeys.primaryOutput(step.details);
        long perBatch = output == null ? 1 : Math.max(1, output.getStackSize());
        List<ICraftingMedium> mediums = grid instanceof CraftingGridCache cache ? cache.getMediums(step.details) : null;
        int machines = mediums == null ? 1 : Math.max(1, mediums.size());

        TimingStats stats = output == null ? null : lookup.apply(output);
        boolean known = true;
        if (stats != null && stats.latencySamples > 0) {
            step.latency = stats.latencyTicks;
        } else if (step.details.isCraftable()) {
            step.latency = assemblerTicks(mediums);
        } else if (MachineTimes.ticksPerItem(StackKeys.of(output)) > 0) {
            // Not measured yet, but a machine was seen running this recipe. It runs once per recipe's worth of
            // items, so a batch of a whole stack takes the recipe time that many times over.
            step.latency = MachineTimes.ticksPerItem(StackKeys.of(output)) * perBatch;
        } else if (stats != null && stats.rateSamples > 0) {
            step.latency = perBatch / stats.itemsPerTick;
        } else {
            step.latency = Config.unknownBatchSeconds * 20.0;
            known = false;
        }
        step.latency = Math.max(1, step.latency);

        if (stats != null && stats.rateSamples > 0) {
            step.itemsPerTick = stats.itemsPerTick;
        } else {
            step.itemsPerTick = perBatch * machines / step.latency;
        }
        // Batches already in a machine have been working for a while, so the first one is that much closer to done.
        step.firstLatency = Math.max(1, step.latency - step.ticksSincePush);
        long total = step.batches + step.pushed;
        step.duration = step.firstLatency + (total - 1) * perBatch / step.itemsPerTick;
        return known;
    }

    /** Ticks per craft of the fastest Molecular Assembler that can run the pattern. */
    private static double assemblerTicks(List<ICraftingMedium> mediums) {
        int bestSpeed = 0;
        if (mediums != null) {
            for (ICraftingMedium medium : mediums) {
                if (medium instanceof TileMolecularAssembler assembler) {
                    int cards = Math.min(assembler.getInstalledUpgrades(Upgrades.SPEED), ASSEMBLER_SPEED.length - 1);
                    bestSpeed = Math.max(bestSpeed, ASSEMBLER_SPEED[Math.max(0, cards)]);
                }
            }
        }
        if (bestSpeed <= 0) {
            bestSpeed = ASSEMBLER_SPEED[0];
        }
        return Math.ceil(100.0 / bestSpeed);
    }

    /** Returns {first batch out, last batch out} in ticks from the start of the job. */
    private static double[] schedule(Step step, Map<Step, double[]> memo, Set<Step> visiting) {
        double[] cached = memo.get(step);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(step)) {
            // A recipe loop; treat the repeated step as already available.
            return new double[] { 0, 0 };
        }
        double start = 0;
        double inputsDone = 0;
        for (Step input : step.inputs) {
            double[] times = schedule(input, memo, visiting);
            start = Math.max(start, times[0]);
            inputsDone = Math.max(inputsDone, times[1]);
        }
        visiting.remove(step);
        if (step.pushed > 0) {
            // Already running, so it does not wait for its first ingredients.
            start = 0;
        }
        double firstOut = start + step.firstLatency;
        double lastOut = start + step.duration;
        if (step.batches > 0) {
            // The last batch still to push cannot finish before its ingredients arrive and it goes through.
            lastOut = Math.max(lastOut, inputsDone + step.latency);
        }
        double[] result = { firstOut, lastOut };
        memo.put(step, result);
        return result;
    }

    private static long outputAmount(ICraftingPatternDetails details, IAEStack<?> stack) {
        IAEStack<?> key = StackKeys.of(stack);
        for (IAEStack<?> out : details.getCondensedAEOutputs()) {
            if (key != null && key.equals(StackKeys.of(out))) {
                return out.getStackSize();
            }
        }
        return 0;
    }

    private static final class Step {

        final ICraftingPatternDetails details;
        /** Batches the CPU still has to push. */
        final long batches;
        /** Batches already pushed to machines and not back yet. */
        final long pushed;
        final long ticksSincePush;
        final List<Step> inputs = new ArrayList<>();
        /** Ticks for one batch from an idle machine. */
        double latency;
        /** Ticks until the next batch comes out, which is less than latency when it is already in a machine. */
        double firstLatency;
        double itemsPerTick;
        double duration;

        Step(ICraftingPatternDetails details, long batches, long pushed, long ticksSincePush) {
            this.details = details;
            this.batches = batches;
            this.pushed = pushed;
            this.ticksSincePush = ticksSincePush;
        }
    }
}
