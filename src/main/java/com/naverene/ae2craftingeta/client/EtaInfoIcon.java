package com.naverene.ae2craftingeta.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import com.naverene.ae2craftingeta.Config;

import appeng.client.gui.implementations.GuiCraftingCPU;

/**
 * An "(i)" in the gap between AE2's elapsed time and the remaining-operations count on the crafting status screen.
 * Hovering it shows the time remaining. Drawn from the status screen's foreground pass, so coordinates are relative to
 * the window.
 */
public final class EtaInfoIcon {

    private static final String ICON = "(i)";
    private static final int ICON_COLOR = 0x3F76E4;
    // Where GuiCraftingCPU draws its title row and right-aligns the remaining-operations count.
    private static final int TITLE_TOP = 7;
    private static final int OPERATIONS_RIGHT = 8 + 128;
    private static final int GAP = 4;

    private EtaInfoIcon() {}

    public static void draw(GuiCraftingCPU gui, int remainingOperations, int mouseX, int mouseY) {
        if (!Config.showCraftingScreenEta) {
            return;
        }
        String[] lines = EtaOverlay.INSTANCE.cpuInfoLines(gui);
        if (lines == null) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int width = font.getStringWidth(ICON);
        int x = OPERATIONS_RIGHT - font.getStringWidth(String.valueOf(remainingOperations)) - GAP - width;
        font.drawString(ICON, x, TITLE_TOP, ICON_COLOR);

        int relX = mouseX - gui.getGuiLeft();
        int relY = mouseY - gui.getGuiTop();
        boolean hovered = relX >= x && relX < x + width && relY >= TITLE_TOP && relY < TITLE_TOP + font.FONT_HEIGHT;
        if (hovered) {
            gui.drawTooltip(relX, relY, lines);
        }
    }
}
