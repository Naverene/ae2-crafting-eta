package com.naverene.ae2craftingeta.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import net.minecraft.server.MinecraftServer;

import com.naverene.ae2craftingeta.machine.MachineProbe;
import com.naverene.ae2craftingeta.machine.MachineStatus;
import com.naverene.ae2craftingeta.machine.MachineTimes;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;

/**
 * Watches one crafting CPU: when it pushes a batch to a machine and when the output comes back. Called from the mixin
 * on CraftingCPUCluster, always on the server thread.
 */
public class CpuTracker {

    private static final Map<Object, CpuTracker> TRACKERS = new WeakHashMap<>();
    private static final int MACHINE_SAMPLE_TICKS = 20;

    /** Batches pushed but not yet fully returned, oldest first, per output. */
    private final Map<IAEStack<?>, ArrayDeque<Batch>> inFlight = new HashMap<>();
    /** Per output, when this job's first and last outputs of it came back. */
    private final Map<IAEStack<?>, Span> spans = new HashMap<>();
    /** Per output with batches in flight, the interfaces those batches went to. */
    private final Map<IAEStack<?>, Set<ICraftingMedium>> mediums = new HashMap<>();
    private long lastMachineSample;

    public static CpuTracker of(Object cpu) {
        return TRACKERS.computeIfAbsent(cpu, k -> new CpuTracker());
    }

    /** The CPU, other than the one given, that has a batch in flight through this interface, or null. */
    public static Object cpuUsing(ICraftingMedium medium, Object except) {
        for (Map.Entry<Object, CpuTracker> e : TRACKERS.entrySet()) {
            CpuTracker tracker = e.getValue();
            if (e.getKey() != except && tracker.uses(medium)) {
                return e.getKey();
            }
        }
        return null;
    }

    private boolean uses(ICraftingMedium medium) {
        for (Set<ICraftingMedium> used : mediums.values()) {
            if (used.contains(medium)) {
                return true;
            }
        }
        return false;
    }

    private boolean usedByAnotherCpu(ICraftingMedium medium) {
        for (CpuTracker other : TRACKERS.values()) {
            if (other != this && other.uses(medium)) {
                return true;
            }
        }
        return false;
    }

    /** Whether any batch of this job is in a machine right now. */
    public boolean hasAnythingInFlight() {
        for (ArrayDeque<Batch> queue : inFlight.values()) {
            if (!queue.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Whether a batch making this output is in a machine right now. */
    public boolean isInFlight(IAEStack<?> key) {
        ArrayDeque<Batch> queue = inFlight.get(key);
        return queue != null && !queue.isEmpty();
    }

    public void onPush(ICraftingPatternDetails details, ICraftingMedium medium) {
        IAEStack<?> output = StackKeys.primaryOutput(details);
        IAEStack<?> key = StackKeys.of(output);
        if (key == null) {
            return;
        }
        long now = now();
        ArrayDeque<Batch> queue = inFlight.computeIfAbsent(key, k -> new ArrayDeque<>());
        // Only a batch pushed while nothing else was waiting in that machine measures the machine itself; otherwise
        // it also includes time spent queued behind earlier batches, this job's or another CPU's.
        boolean idle = queue.isEmpty() && !usedByAnotherCpu(medium);
        queue.addLast(new Batch(now, output.getStackSize(), idle));
        Span span = spans.computeIfAbsent(key, k -> new Span());
        span.batchSize = output.getStackSize();
        mediums.computeIfAbsent(key, k -> Collections.newSetFromMap(new IdentityHashMap<>()))
            .add(medium);
        if (now - lastMachineSample >= MACHINE_SAMPLE_TICKS) {
            lastMachineSample = now;
            machines();
        }
    }

    /**
     * What the machines working on each in-flight output are doing right now. Also remembers the recipe time of each
     * busy machine as a first guess for that output.
     */
    public Map<IAEStack<?>, List<MachineStatus>> machines() {
        Map<IAEStack<?>, List<MachineStatus>> result = new LinkedHashMap<>();
        for (Map.Entry<IAEStack<?>, Set<ICraftingMedium>> e : mediums.entrySet()) {
            List<MachineStatus> statuses = new ArrayList<>();
            for (ICraftingMedium medium : e.getValue()) {
                List<MachineStatus> probed = MachineProbe.probe(medium);
                statuses.addAll(probed);
                // With several machines behind one interface (a subnet), any of them may be on another recipe.
                MachineStatus only = probed.size() == 1 ? probed.get(0) : null;
                if (only != null && only.isWorking()) {
                    MachineTimes.record(e.getKey(), only.maxProgress);
                }
            }
            if (!statuses.isEmpty()) {
                result.put(e.getKey(), statuses);
            }
        }
        return result;
    }

    public void onReturn(IAEStack<?> returned) {
        IAEStack<?> key = StackKeys.of(returned);
        if (key == null || returned.getStackSize() <= 0) {
            return;
        }
        ArrayDeque<Batch> queue = inFlight.get(key);
        if (queue == null || queue.isEmpty()) {
            return;
        }
        long now = now();
        long remaining = returned.getStackSize();
        TimingStore store = TimingStore.get();
        while (remaining > 0 && !queue.isEmpty()) {
            Batch batch = queue.peekFirst();
            long used = Math.min(remaining, batch.remaining);
            batch.remaining -= used;
            remaining -= used;
            Span span = spans.get(key);
            if (span != null) {
                if (span.produced == 0) {
                    span.firstReturn = now;
                }
                span.produced += used;
                span.lastReturn = now;
            }
            if (batch.remaining <= 0) {
                queue.pollFirst();
                if (batch.machineWasIdle && store != null) {
                    store.recordLatency(key, now - batch.pushedAt);
                }
            }
        }
        if (queue.isEmpty()) {
            mediums.remove(key);
        }
    }

    /** The job finished: turn each output's busy span into a throughput sample and remember the whole job. */
    public void onComplete(IAEStack<?> finalOutput, long amount, long elapsedNanos) {
        TimingStore store = TimingStore.get();
        if (store != null) {
            for (Map.Entry<IAEStack<?>, Span> e : spans.entrySet()) {
                Span span = e.getValue();
                // Measured from the first output onwards, so the first batch's latency is not counted twice.
                // A single batch says nothing about throughput; its latency was already recorded.
                long ticks = span.lastReturn - span.firstReturn;
                if (ticks > 0 && span.produced > span.batchSize) {
                    store.recordRate(e.getKey(), (double) (span.produced - span.batchSize) / ticks);
                }
            }
            if (finalOutput != null) {
                store.recordJob(finalOutput, amount, TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
            }
        }
        reset();
    }

    /** The job was cancelled: partial spans would understate throughput, so drop them. */
    public void onCancel() {
        reset();
    }

    private void reset() {
        inFlight.clear();
        spans.clear();
        mediums.clear();
    }

    private static long now() {
        MinecraftServer server = MinecraftServer.getServer();
        return server == null ? 0L : server.getTickCounter();
    }

    private static final class Batch {

        final long pushedAt;
        long remaining;
        final boolean machineWasIdle;

        Batch(long pushedAt, long remaining, boolean machineWasIdle) {
            this.pushedAt = pushedAt;
            this.remaining = remaining;
            this.machineWasIdle = machineWasIdle;
        }
    }

    private static final class Span {

        long firstReturn;
        long lastReturn;
        long produced;
        long batchSize;
    }
}
