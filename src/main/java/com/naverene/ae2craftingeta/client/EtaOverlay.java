package com.naverene.ae2craftingeta.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.client.event.GuiScreenEvent;

import com.naverene.ae2craftingeta.Config;
import com.naverene.ae2craftingeta.network.PacketEta;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.client.gui.implementations.GuiCraftingCPU;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Draws the estimate just above the crafting plan and crafting status windows. */
public class EtaOverlay {

    private static final int COLOR = 0xFFFFFF;

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!Config.showCraftingScreenEta) {
            return;
        }
        int mode = ClientEtaState.mode;
        String label;
        if (event.gui instanceof GuiCraftConfirm && mode == PacketEta.PLAN) {
            label = "Estimated time: ";
        } else if (event.gui instanceof GuiCraftingCPU && mode == PacketEta.RUNNING) {
            label = "Time remaining: about ";
        } else {
            return;
        }
        AEBaseGui gui = (AEBaseGui) event.gui;
        String text = label + TimeFormat.format(ClientEtaState.ticks / 20.0);
        int unknown = ClientEtaState.unknownSteps;
        if (unknown > 0) {
            text += " (" + unknown + " of " + ClientEtaState.totalSteps + " steps not measured yet)";
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        font.drawStringWithShadow(text, gui.getGuiLeft() + 2, gui.getGuiTop() - 10, COLOR);
    }
}
