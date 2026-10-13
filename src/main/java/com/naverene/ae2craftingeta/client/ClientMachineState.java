package com.naverene.ae2craftingeta.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.naverene.ae2craftingeta.machine.MachineStatus;

/**
 * The machines working on, and the reasons for waiting of, each item of the open crafting status screen, as last sent
 * by the server. Plain Java so the dedicated server can load it.
 */
public final class ClientMachineState {

    private static volatile Map<ItemStack, List<MachineStatus>> machines = Collections.emptyMap();
    private static volatile Map<ItemStack, List<String>> blocked = Collections.emptyMap();

    private ClientMachineState() {}

    public static void set(Map<ItemStack, List<MachineStatus>> latestMachines,
        Map<ItemStack, List<String>> latestBlocked) {
        machines = latestMachines;
        blocked = latestBlocked;
    }

    public static void clear() {
        machines = Collections.emptyMap();
        blocked = Collections.emptyMap();
    }

    public static Map<ItemStack, List<MachineStatus>> allMachines() {
        return machines;
    }

    public static Map<ItemStack, List<String>> allBlocked() {
        return blocked;
    }
}
