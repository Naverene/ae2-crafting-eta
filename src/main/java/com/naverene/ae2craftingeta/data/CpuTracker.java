package com.naverene.ae2craftingeta.data;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import net.minecraft.server.MinecraftServer;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;

/**
 * Watches one crafting CPU: when it pushes a batch to a machine and when the output comes back. Called from the mixin
 * on CraftingCPUCluster, always on the server thread.
 */
public class CpuTracker {

    private static final Map<Object, CpuTracker> TRACKERS = new WeakHashMap<>();

    /** Batches pushed but not yet fully returned, oldest first, per output. */
    private final Map<IAEStack<?>, ArrayDeque<Batch>> inFlight = new HashMap<>();
    /** Per output, when this job's first and last outputs of it came back. */
    private final Map<IAEStack<?>, Span> spans = new HashMap<>();

    public static CpuTracker of(Object cpu) {
        return TRACKERS.computeIfAbsent(cpu, k -> new CpuTracker());
    }

    public void onPush(ICraftingPatternDetails details) {
        IAEStack<?> output = StackKeys.primaryOutput(details);
        IAEStack<?> key = StackKeys.of(output);
        if (key == null) {
            return;
        }
        long now = now();
        ArrayDeque<Batch> queue = inFlight.computeIfAbsent(key, k -> new ArrayDeque<>());
        // Only a batch pushed while nothing else of the same kind was waiting measures the machine itself;
        // otherwise it also includes time spent queued behind earlier batches.
        queue.addLast(new Batch(now, output.getStackSize(), queue.isEmpty()));
        Span span = spans.computeIfAbsent(key, k -> new Span());
        span.batchSize = output.getStackSize();
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
