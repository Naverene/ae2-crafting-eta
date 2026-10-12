package com.naverene.ae2craftingeta.machine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.naverene.ae2craftingeta.data.CpuTracker;
import com.naverene.ae2craftingeta.data.StackKeys;
import com.naverene.ae2craftingeta.mixins.late.AccessorCraftingCPUCluster;
import com.naverene.ae2craftingeta.mixins.late.AccessorTaskProgress;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import appeng.util.ScheduledReason;

/**
 * Works out why items on a CPU are not moving: an ingredient that is still being made, an interface held by another
 * CPU's job, or a machine that is shut down or idle with its interface still full. AE2 itself only says which kind
 * of wait it is, not what is causing it.
 */
public final class BlockedReasons {

    private static final int MAX_REASONS = 4;

    private BlockedReasons() {}

    /** Per output that has work left but nothing in a machine, the reasons it is waiting. */
    public static Map<IAEStack<?>, List<String>> forCpu(CraftingCPUCluster cpu) {
        Map<IAEStack<?>, List<String>> result = new LinkedHashMap<>();
        IGrid grid = cpu.getGrid();
        if (grid == null || !(grid.getCache(ICraftingGrid.class) instanceof CraftingGridCache cache)) {
            return result;
        }
        CpuTracker tracker = CpuTracker.of(cpu);
        Map<ICraftingPatternDetails, Long> remaining = new LinkedHashMap<>();
        Set<IAEStack<?>> stillToMake = new HashSet<>();
        for (Map.Entry<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> e : ((AccessorCraftingCPUCluster) cpu)
            .ae2craftingeta$getTasks()
            .entrySet()) {
            long left = ((AccessorTaskProgress) e.getValue()).ae2craftingeta$getValue();
            IAEStack<?> key = StackKeys.of(StackKeys.primaryOutput(e.getKey()));
            if (key == null) {
                continue;
            }
            if (left > 0) {
                remaining.put(e.getKey(), left);
            }
            if (left > 0 || tracker.isInFlight(key)) {
                stillToMake.add(key);
            }
        }

        for (ICraftingPatternDetails details : remaining.keySet()) {
            IAEStack<?> output = StackKeys.primaryOutput(details);
            IAEStack<?> key = StackKeys.of(output);
            if (tracker.isInFlight(key)) {
                continue;
            }
            Set<String> reasons = new LinkedHashSet<>();
            if (cpu.getScheduledReason(output) == ScheduledReason.NOT_ENOUGH_INGREDIENTS) {
                for (IAEStack<?> input : details.getCondensedAEInputs()) {
                    IAEStack<?> inputKey = StackKeys.of(input);
                    if (inputKey != null && !inputKey.equals(key) && stillToMake.contains(inputKey)) {
                        reasons.add("Waiting for " + Platform.getItemDisplayName(input) + " to be made first");
                    }
                }
                if (reasons.isEmpty()) {
                    reasons.add("Waiting for ingredients");
                }
            }
            for (ICraftingMedium medium : cache.getMediums(details)) {
                if (medium.isBusy()) {
                    addMediumReasons(cpu, medium, reasons);
                }
            }
            if (!reasons.isEmpty()) {
                List<String> list = new ArrayList<>(reasons);
                result.put(key, list.subList(0, Math.min(list.size(), MAX_REASONS)));
            }
        }
        return result;
    }

    private static void addMediumReasons(CraftingCPUCluster cpu, ICraftingMedium medium, Set<String> reasons) {
        List<MachineStatus> machines = MachineProbe.probe(medium);
        String name = machines.isEmpty() ? "An interface" : machines.get(0).machine.getDisplayName();
        if (CpuTracker.cpuUsing(medium, cpu) instanceof ICraftingCPU other) {
            reasons.add(name + " is busy with another job: " + describeJob(other));
            return;
        }
        for (MachineStatus status : machines) {
            String machine = status.machine.getDisplayName();
            if (status.shutdownReason != null) {
                reasons.add(machine + " has shut down: " + status.shutdownReason);
            } else if (!status.isWorking()) {
                reasons.add(machine + " is idle but its interface is still full; check its output and power");
            }
        }
    }

    private static String describeJob(ICraftingCPU cpu) {
        IAEStack<?> output = cpu.getFinalMultiOutput();
        String job = output == null ? "another request"
            : "making " + cpu.getStartItemCount() + " " + Platform.getItemDisplayName(output);
        String name = cpu.getName();
        return name == null || name.isEmpty() ? job : job + " on " + name;
    }
}
