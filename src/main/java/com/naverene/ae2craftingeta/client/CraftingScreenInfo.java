package com.naverene.ae2craftingeta.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.naverene.ae2craftingeta.eta.EtaCalculator;
import com.naverene.ae2craftingeta.eta.EtaResult;
import com.naverene.ae2craftingeta.mixins.late.AccessorGuiCraftConfirm;
import com.naverene.ae2craftingeta.network.PacketEta;

import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.crafting.v2.CraftingJobV2;

/**
 * What the info icon on each crafting screen shows (see {@link EtaInfoIcon}). Uses the server's estimate when the
 * server has this mod, and works it out on the client otherwise.
 */
public class CraftingScreenInfo {

    public static final CraftingScreenInfo INSTANCE = new CraftingScreenInfo();

    private static final long RUNNING_REFRESH_MILLIS = 500;

    // Times are counted in game ticks, so they only match the clock when the server runs at full speed.
    private static final String TPS_NOTE = "Assumes 20 TPS; server lag makes crafts take longer.";

    private Object cachedJob;
    private int cachedCoProcessors;
    private EtaResult cachedPlan;
    private EtaResult cachedRunning;
    private long runningComputedAt;

    // The running estimate only changes when the CPU makes progress, so between changes it is counted down here.
    private Object countdownGui;
    private EtaResult countdownEstimate;
    private long countdownEndMillis;

    private CraftingScreenInfo() {}

    /** Plan screen: the tooltip lines for its info icon, or null when there is no estimate yet. */
    public String[] planInfoLines(GuiCraftConfirm gui) {
        EtaResult eta = planEta(gui);
        if (eta == null || eta.totalSteps == 0) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        lines.add("Estimated time: " + TimeFormat.format(eta.ticks / 20.0));
        String note = unmeasuredNote(eta);
        if (note != null) {
            lines.add(EnumChatFormatting.GRAY + note);
        }
        lines.add(EnumChatFormatting.GRAY + TPS_NOTE);
        return lines.toArray(new String[0]);
    }

    /**
     * Status screen: the tooltip lines for its info icon (time remaining, stuck items, machines), or null when there is
     * nothing to show.
     */
    public String[] cpuInfoLines(GuiCraftingCPU gui) {
        List<String> lines = new ArrayList<>();
        EtaResult eta = runningEta();
        if (eta != null && eta.totalSteps > 0) {
            lines.add("Time remaining: " + countdown(gui, eta));
            String note = unmeasuredNote(eta);
            if (note != null) {
                lines.add(EnumChatFormatting.GRAY + note);
            }
            lines.add(EnumChatFormatting.GRAY + TPS_NOTE);
        }
        CpuStatusSummary.addTo(lines);
        return lines.isEmpty() ? null : lines.toArray(new String[0]);
    }

    private static String unmeasuredNote(EtaResult eta) {
        return eta.unknownSteps > 0 ? "(" + eta.unknownSteps + " of " + eta.totalSteps + " steps not measured yet)"
            : null;
    }

    /** Seconds left on the countdown, restarted from the latest estimate whenever that estimate changes. */
    private String countdown(Object gui, EtaResult eta) {
        long now = System.currentTimeMillis();
        if (ClientTimings.serverHasMod() && ClientEtaState.mode == PacketEta.PAUSED) {
            // Hold the clock; it restarts from the latest estimate once the job moves again.
            countdownEstimate = null;
            return "about " + TimeFormat.formatCountdown(Math.max(1, eta.ticks / 20))
                + ", paused: waiting on another job";
        }
        if (gui != countdownGui || !eta.equals(countdownEstimate)) {
            countdownGui = gui;
            countdownEstimate = eta;
            countdownEndMillis = now + eta.ticks * 50;
        }
        long seconds = (countdownEndMillis - now + 999) / 1000;
        return seconds > 0 ? "about " + TimeFormat.formatCountdown(seconds) : "almost done";
    }

    private EtaResult planEta(GuiCraftConfirm gui) {
        if (ClientTimings.serverHasMod()) {
            return ClientEtaState.mode == PacketEta.PLAN ? ClientEtaState.result() : null;
        }
        CraftingJobV2<?> job = ((AccessorGuiCraftConfirm) gui).ae2craftingeta$getJobTree();
        if (job == null) {
            return null;
        }
        int coProcessors = ((ContainerCraftConfirm) gui.inventorySlots).getCpuCoProcessors();
        if (job != cachedJob || coProcessors != cachedCoProcessors) {
            cachedPlan = EtaCalculator.forTree(job, coProcessors, ClientTimings::find);
            cachedJob = job;
            cachedCoProcessors = coProcessors;
        }
        return cachedPlan;
    }

    private EtaResult runningEta() {
        if (ClientTimings.serverHasMod()) {
            int mode = ClientEtaState.mode;
            return mode == PacketEta.RUNNING || mode == PacketEta.PAUSED ? ClientEtaState.result() : null;
        }
        long now = System.currentTimeMillis();
        if (cachedRunning == null || now - runningComputedAt > RUNNING_REFRESH_MILLIS) {
            cachedRunning = EtaCalculator.forCpuEntries(ClientCpuWatcher.counts(), ClientTimings::find);
            runningComputedAt = now;
        }
        return cachedRunning;
    }
}
