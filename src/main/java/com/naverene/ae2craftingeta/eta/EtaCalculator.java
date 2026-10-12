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
        return estimate(batches, grid, coProcessors, serverLookup());
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
        return estimate(batches, null, coProcessors, lookup);
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
        ICraftingGrid grid = cpu.getGrid() == null ? null
            : cpu.getGrid()
                .getCache(ICraftingGrid.class);
        EtaResult queued = estimate(batches, grid, cpu.getCoProcessors(), serverLookup());

        // Batches already in a machine are not in the task list; the machine itself says how long they have left.
        long inMachines = 0;
        int busyOutputs = 0;
        for (List<MachineStatus> statuses : CpuTracker.of(cpu)
            .machines()
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
                ticks = latency + scheduled / stats.itemsPerTick;
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

    static EtaResult estimate(Map<ICraftingPatternDetails, Long> batches, ICraftingGrid grid, int coProcessors,
        Function<IAEStack<?>, TimingStats> lookup) {
        if (batches.isEmpty()) {
            return new EtaResult(0, 0, 0);
        }

        // Which step produces each item, so ingredients can be linked to the steps that make them.
        Map<IAEStack<?>, Step> producers = new HashMap<>();
        List<Step> steps = new ArrayList<>();
        for (Map.Entry<ICraftingPatternDetails, Long> e : batches.entrySet()) {
            Step step = new Step(e.getKey(), e.getValue());
            steps.add(step);
            for (IAEStack<?> out : e.getKey()
                .getCondensedAEOutputs()) {
                IAEStack<?> key = StackKeys.of(out);
                if (key != null) {
                    producers.putIfAbsent(key, step);
                }
            }
        }

        int unknown = 0;
        long totalBatches = 0;
        for (Step step : steps) {
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
        } else if (MachineTimes.get(StackKeys.of(output)) > 0) {
            // Not measured yet, but a machine was seen running this recipe.
            step.latency = MachineTimes.get(StackKeys.of(output));
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
        step.duration = step.latency + (step.batches - 1) * perBatch / step.itemsPerTick;
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
        double firstOut = start + step.latency;
        double lastOut = Math.max(start + step.duration, inputsDone + step.latency);
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
        final long batches;
        final List<Step> inputs = new ArrayList<>();
        double latency;
        double itemsPerTick;
        double duration;

        Step(ICraftingPatternDetails details, long batches) {
            this.details = details;
            this.batches = batches;
        }
    }
}
