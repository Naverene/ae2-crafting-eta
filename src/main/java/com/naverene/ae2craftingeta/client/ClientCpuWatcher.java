package com.naverene.ae2craftingeta.client;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;

import com.naverene.ae2craftingeta.data.StackKeys;

import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.CraftingCpuEntry;

/**
 * Learns timings from the crafting status screen when the server doesn't have this mod. AE2 sends the client each
 * item's scheduled and in-progress counts; a drop in scheduled is a batch being sent to a machine, and in-progress
 * falling by less than that rise means output came back.
 */
public final class ClientCpuWatcher {

    /** Per item, {scheduled, in progress}, as last shown on the screen. */
    private static final Map<IAEStack<?>, long[]> COUNTS = new ConcurrentHashMap<>();
    private static final Map<IAEStack<?>, ArrayDeque<Batch>> IN_FLIGHT = new HashMap<>();
    private static final Map<IAEStack<?>, Span> SPANS = new HashMap<>();

    private ClientCpuWatcher() {}

    public static Map<IAEStack<?>, long[]> counts() {
        return COUNTS;
    }

    public static synchronized void onUpdate(CraftingCpuEntry[] updates, boolean clearFirst) {
        if (clearFirst) {
            // A new screen or a new job: earlier pushes can't be matched to what comes back now.
            COUNTS.clear();
            IN_FLIGHT.clear();
            SPANS.clear();
        }
        if (updates == null) {
            return;
        }
        boolean learn = !ClientTimings.serverHasMod();
        long now = now();
        for (CraftingCpuEntry update : updates) {
            if (update == null || update.getStack() == null) {
                continue;
            }
            IAEStack<?> key = StackKeys.of(update.getStack());
            long[] old = COUNTS.getOrDefault(key, new long[2]);
            boolean gone = update.getTotalAmount() <= 0;
            long scheduled = gone ? 0 : update.getPendingAmount();
            long active = gone ? 0 : update.getActiveAmount();
            if (gone) {
                COUNTS.remove(key);
            } else {
                COUNTS.put(key, new long[] { scheduled, active });
            }
            if (learn && !clearFirst) {
                long pushed = Math.max(0, old[0] - scheduled);
                long returned = Math.max(0, pushed - (active - old[1]));
                track(key, pushed, returned, scheduled == 0 && active == 0, now);
            }
        }
    }

    private static void track(IAEStack<?> key, long pushed, long returned, boolean finished, long now) {
        ArrayDeque<Batch> queue = IN_FLIGHT.computeIfAbsent(key, k -> new ArrayDeque<>());
        if (pushed > 0) {
            queue.addLast(new Batch(now, pushed, queue.isEmpty()));
        }
        if (returned > 0) {
            Span span = SPANS.computeIfAbsent(key, k -> new Span());
            if (span.produced == 0) {
                span.firstReturn = now;
                span.firstChunk = returned;
            }
            span.produced += returned;
            span.lastReturn = now;

            long remaining = returned;
            while (remaining > 0 && !queue.isEmpty()) {
                Batch batch = queue.peekFirst();
                long used = Math.min(remaining, batch.remaining);
                batch.remaining -= used;
                remaining -= used;
                if (batch.remaining <= 0) {
                    queue.pollFirst();
                    if (batch.machineWasIdle) {
                        ClientTimings.recordLatency(key, now - batch.pushedAt);
                    }
                }
            }
        }
        if (finished) {
            Span span = SPANS.remove(key);
            IN_FLIGHT.remove(key);
            if (span != null && span.lastReturn > span.firstReturn && span.produced > span.firstChunk) {
                double rate = (double) (span.produced - span.firstChunk) / (span.lastReturn - span.firstReturn);
                ClientTimings.recordRate(key, rate);
            }
        }
    }

    private static long now() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld == null ? 0L : mc.theWorld.getTotalWorldTime();
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
        long firstChunk;
    }
}
