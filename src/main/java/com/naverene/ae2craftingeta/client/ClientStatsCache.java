package com.naverene.ae2craftingeta.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.naverene.ae2craftingeta.data.StackKeys;
import com.naverene.ae2craftingeta.data.TimingStats;

import appeng.api.storage.data.IAEStack;

/** The server's learned timings, as last synced, for tooltips. */
public final class ClientStatsCache {

    private static final Map<IAEStack<?>, TimingStats> STATS = new ConcurrentHashMap<>();

    private ClientStatsCache() {}

    public static void update(Map<IAEStack<?>, TimingStats> stats, boolean replaceAll) {
        if (replaceAll) {
            STATS.clear();
        }
        STATS.putAll(stats);
    }

    public static TimingStats find(IAEStack<?> stack) {
        IAEStack<?> key = StackKeys.of(stack);
        return key == null ? null : STATS.get(key);
    }
}
