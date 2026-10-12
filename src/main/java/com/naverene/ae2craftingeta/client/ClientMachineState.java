package com.naverene.ae2craftingeta.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.naverene.ae2craftingeta.machine.MachineStatus;

/**
 * The machines working on each item of the open crafting status screen, as last sent by the server. Plain Java so the
 * dedicated server can load it.
 */
public final class ClientMachineState {

    private static volatile Map<ItemStack, List<MachineStatus>> machines = Collections.emptyMap();

    private ClientMachineState() {}

    public static void set(Map<ItemStack, List<MachineStatus>> latest) {
        machines = latest;
    }

    public static void clear() {
        machines = Collections.emptyMap();
    }

    /** Machines working on this item, or an empty list. */
    public static List<MachineStatus> forItem(ItemStack item) {
        if (item == null) {
            return Collections.emptyList();
        }
        for (Map.Entry<ItemStack, List<MachineStatus>> e : machines.entrySet()) {
            if (ItemStack.areItemStacksEqual(withSize1(e.getKey()), withSize1(item))) {
                return e.getValue();
            }
        }
        return Collections.emptyList();
    }

    private static ItemStack withSize1(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }
}
