package com.naverene.ae2craftingeta.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
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
 * Draws the estimate just above the crafting plan and crafting status windows. Uses the server's estimate when the
 * server has this mod, and works it out on the client otherwise.
 */
public class EtaOverlay {

    private static final int COLOR = 0xFFFFFF;
    private static final long RUNNING_REFRESH_MILLIS = 500;

    private Object cachedJob;
    private int cachedCoProcessors;
    private EtaResult cachedPlan;
    private EtaResult cachedRunning;
    private long runningComputedAt;

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!Config.showCraftingScreenEta) {
            return;
        }
        String label;
        EtaResult eta;
        if (event.gui instanceof GuiCraftConfirm confirm) {
            label = "Estimated time: ";
            eta = planEta(confirm);
        } else if (event.gui instanceof GuiCraftingCPU) {
            label = "Time remaining: about ";
            eta = runningEta();
        } else {
            return;
        }
        if (eta == null || eta.totalSteps == 0) {
            return;
        }
        AEBaseGui gui = (AEBaseGui) event.gui;
        String text = label + TimeFormat.format(eta.ticks / 20.0);
        String note = eta.unknownSteps > 0
            ? "(" + eta.unknownSteps + " of " + eta.totalSteps + " steps not measured yet)"
            : null;
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

    private static void drawCentered(FontRenderer font, String text, int centerX, int y) {
        font.drawStringWithShadow(text, centerX - font.getStringWidth(text) / 2, y, COLOR);
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
            return ClientEtaState.mode == PacketEta.RUNNING ? ClientEtaState.result() : null;
        }
        long now = System.currentTimeMillis();
        if (cachedRunning == null || now - runningComputedAt > RUNNING_REFRESH_MILLIS) {
            cachedRunning = EtaCalculator.forCpuEntries(ClientCpuWatcher.counts(), ClientTimings::find);
            runningComputedAt = now;
        }
        return cachedRunning;
    }
}
