package com.naverene.ae2craftingeta.machine;

import java.util.HashMap;
import java.util.Map;

import appeng.api.storage.data.IAEStack;

/**
 * Recipe times read off machines while they worked on a CPU's batch, per output. Kept in memory only: it is a first
 * guess for steps whose push-to-return time has not been measured yet, and is replaced by that measurement.
 *
 * <p>
 * Stored as ticks per item made, not per recipe run: a batch of 64 dusts pushed into a furnace takes 64 runs, not one.
 */
public final class MachineTimes {

    private static final Map<IAEStack<?>, Double> TICKS_PER_ITEM = new HashMap<>();

    private MachineTimes() {}

    /**
     * Remembers one run of the machine's recipe taking the given ticks and making itemsPerRun of the output. When
     * itemsPerRun is unknown (0 or less), one item per run is assumed.
     */
    public static synchronized void record(IAEStack<?> key, int ticks, long itemsPerRun) {
        if (key != null && ticks > 0) {
            TICKS_PER_ITEM.put(key, (double) ticks / Math.max(1, itemsPerRun));
        }
    }

    /** Recipe ticks per item last seen for this output, or 0 if never seen. */
    public static synchronized double ticksPerItem(IAEStack<?> key) {
        Double ticks = key == null ? null : TICKS_PER_ITEM.get(key);
        return ticks == null ? 0 : ticks;
    }
}
