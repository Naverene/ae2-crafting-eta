package com.naverene.ae2craftingeta.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;

import com.naverene.ae2craftingeta.Config;
import com.naverene.ae2craftingeta.eta.EtaCalculator;
import com.naverene.ae2craftingeta.eta.EtaResult;
import com.naverene.ae2craftingeta.mixins.late.AccessorGuiCraftConfirm;
import com.naverene.ae2craftingeta.network.PacketEta;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.crafting.v2.CraftingJobV2;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * The estimate for the crafting plan screen (drawn above the window) and the crafting status screen (shown by the info
 * icon next to AE2's elapsed time, see {@link EtaInfoIcon}). Uses the server's estimate when the server has this mod,
 * and works it out on the client otherwise.
 */
public class EtaOverlay {

    public static final EtaOverlay INSTANCE = new EtaOverlay();

    private static final int COLOR = 0xFFFFFF;
    private static final long RUNNING_REFRESH_MILLIS = 500;

    private Object cachedJob;
    private int cachedCoProcessors;
    private EtaResult cachedPlan;
    private EtaResult cachedRunning;
    private long runningComputedAt;

    // The running estimate only changes when the CPU makes progress, so between changes it is counted down here.
    private Object countdownGui;
    private EtaResult countdownEstimate;
    private long countdownEndMillis;

    private EtaOverlay() {}

    /** Plan screen: the estimate is drawn centered above the window. */
    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!Config.showCraftingScreenEta || !(event.gui instanceof GuiCraftConfirm confirm)) {
            return;
        }
        EtaResult eta = planEta(confirm);
        if (eta == null || eta.totalSteps == 0) {
            return;
        }
        AEBaseGui gui = (AEBaseGui) event.gui;
        String text = "Estimated time: " + TimeFormat.format(eta.ticks / 20.0);
        String note = unmeasuredNote(eta);
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        // AE2 centers its windows, so the window's middle is the screen's middle and its width follows from guiLeft.
        int centerX = gui.width / 2;
        int windowWidth = gui.width - 2 * gui.getGuiLeft();
        int y = gui.getGuiTop() - 10;
        if (note != null && font.getStringWidth(text + " " + note) <= windowWidth) {
            text += " " + note;
            note = null;
        }
        if (note != null) {
            drawCentered(font, note, centerX, y);
            y = Math.max(0, y - 10);
        }
        drawCentered(font, text, centerX, y);
    }

    /**
     * Status screen: the tooltip lines for the info icon next to AE2's elapsed time, or null when there is no estimate
     * to show.
     */
    public String[] cpuInfoLines(GuiCraftingCPU gui) {
        EtaResult eta = runningEta();
        if (eta == null || eta.totalSteps == 0) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        lines.add("Time remaining: " + countdown(gui, eta));
        String note = unmeasuredNote(eta);
        if (note != null) {
            lines.add(EnumChatFormatting.GRAY + note);
        }
        if (ClientTimings.serverHasMod()) {
            lines.add(EnumChatFormatting.GRAY + "Hover an item to see its machines or why it is waiting");
        }
        return lines.toArray(new String[0]);
    }

    private static String unmeasuredNote(EtaResult eta) {
        return eta.unknownSteps > 0 ? "(" + eta.unknownSteps + " of " + eta.totalSteps + " steps not measured yet)"
            : null;
    }

    private static void drawCentered(FontRenderer font, String text, int centerX, int y) {
        font.drawStringWithShadow(text, centerX - font.getStringWidth(text) / 2, y, COLOR);
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
