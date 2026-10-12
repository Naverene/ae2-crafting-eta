package com.naverene.ae2craftingeta.client;

import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.naverene.ae2craftingeta.machine.MachineStatus;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

/**
 * Extra tooltip lines on the crafting status screen: what each machine making the item is doing, and why it is waiting
 * when it is stuck.
 */
public final class MachineTooltip {

    private static final int MAX_LINES = 4;

    private MachineTooltip() {}

    /** Lines to append, each starting with a newline, or null when there is nothing to add for this item. */
    public static String lines(IAEStack<?> stack) {
        if (!(stack instanceof IAEItemStack item)) {
            return null;
        }
        List<MachineStatus> machines = ClientMachineState.machinesFor(item.getItemStack());
        List<String> blocked = ClientMachineState.blockedFor(item.getItemStack());
        if (machines.isEmpty() && blocked.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String reason : blocked) {
            out.append('\n')
                .append(EnumChatFormatting.YELLOW)
                .append(reason);
        }
        if (machines.isEmpty()) {
            return out.toString();
        }
        out.append('\n')
            .append(EnumChatFormatting.GRAY)
            .append("Machines:");
        int shown = Math.min(machines.size(), MAX_LINES);
        for (int i = 0; i < shown; i++) {
            MachineStatus status = machines.get(i);
            out.append('\n')
                .append(EnumChatFormatting.GRAY)
                .append("  ")
                .append(status.machine.getDisplayName())
                .append(": ")
                .append(describe(status));
        }
        if (machines.size() > shown) {
            out.append('\n')
                .append(EnumChatFormatting.GRAY)
                .append("  and ")
                .append(machines.size() - shown)
                .append(" more");
        }
        return out.toString();
    }

    private static String describe(MachineStatus status) {
        if (status.detail != null) {
            return status.detail;
        }
        if (status.shutdownReason != null) {
            return "shut down (" + status.shutdownReason + ")";
        }
        if (!status.isWorking()) {
            return "idle";
        }
        int percent = (int) (100L * status.progress / status.maxProgress);
        return percent + "%, " + TimeFormat.format(status.ticksLeft() / 20.0) + " left";
    }
}
