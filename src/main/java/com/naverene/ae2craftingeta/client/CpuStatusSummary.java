package com.naverene.ae2craftingeta.client;

import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.naverene.ae2craftingeta.Config;
import com.naverene.ae2craftingeta.machine.MachineStatus;

/** The "Stuck" and "Machines" sections of the crafting status screen's info tooltip. */
public final class CpuStatusSummary {

    private CpuStatusSummary() {}

    public static void addTo(List<String> lines) {
        int max = Config.maxItemsPerSection;
        Map<ItemStack, List<String>> blocked = ClientMachineState.allBlocked();
        if (Config.showStuckSection && !blocked.isEmpty()) {
            lines.add(EnumChatFormatting.YELLOW + "Stuck:");
            int shown = 0;
            for (Map.Entry<ItemStack, List<String>> e : blocked.entrySet()) {
                if (shown++ == max) {
                    lines.add(EnumChatFormatting.GRAY + "  and " + (blocked.size() - max) + " more");
                    break;
                }
                String item = e.getKey()
                    .getDisplayName();
                for (String reason : e.getValue()) {
                    lines.add(EnumChatFormatting.GRAY + "  " + item + ": " + EnumChatFormatting.YELLOW + reason);
                }
            }
        }

        Map<ItemStack, List<MachineStatus>> machines = ClientMachineState.allMachines();
        if (Config.showMachinesSection && !machines.isEmpty()) {
            lines.add(EnumChatFormatting.AQUA + "Machines:");
            int shown = 0;
            for (Map.Entry<ItemStack, List<MachineStatus>> e : machines.entrySet()) {
                if (shown++ == max) {
                    lines.add(EnumChatFormatting.GRAY + "  and " + (machines.size() - max) + " more");
                    break;
                }
                String item = e.getKey()
                    .getDisplayName();
                for (MachineStatus status : e.getValue()) {
                    lines.add(
                        EnumChatFormatting.GRAY + "  "
                            + item
                            + ": "
                            + status.machine.getDisplayName()
                            + ", "
                            + describe(status));
                }
            }
        }
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
