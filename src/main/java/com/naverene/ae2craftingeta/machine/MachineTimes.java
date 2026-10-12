package com.naverene.ae2craftingeta.machine;

import java.util.HashMap;
import java.util.Map;

import appeng.api.storage.data.IAEStack;

/**
 * Recipe times read off machines while they worked on a CPU's batch, per output. Kept in memory only: it is a first
 * guess for steps whose push-to-return time has not been measured yet, and is replaced by that measurement.
 */
public final class MachineTimes {

    private static final Map<IAEStack<?>, Integer> TICKS = new HashMap<>();

    private MachineTimes() {}

    public static synchronized void record(IAEStack<?> key, int ticks) {
        if (key != null && ticks > 0) {
            TICKS.put(key, ticks);
        }
    }

    /** Recipe ticks last seen for this output, or 0 if never seen. */
    public static synchronized int get(IAEStack<?> key) {
        Integer ticks = key == null ? null : TICKS.get(key);
        return ticks == null ? 0 : ticks;
    }
}
