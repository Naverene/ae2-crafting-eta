package com.naverene.ae2craftingeta.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import com.naverene.ae2craftingeta.Config;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.client.gui.implementations.GuiCraftingCPU;

/**
 * The "(i)" on AE2's crafting screens that holds everything this mod shows there. Drawn from each screen's foreground
 * pass, so coordinates are relative to the window.
 */
public final class EtaInfoIcon {

    private static final String ICON = "(i)";
    private static final int ICON_COLOR = 0x3F76E4;
    private static final int GAP = 4;
    // Both screens draw their title row at y = 7.
    private static final int TITLE_TOP = 7;
    // GuiCraftingCPU right-aligns its remaining-operations count against x = 8 + 128.
    private static final int CPU_OPERATIONS_RIGHT = 8 + 128;
    // GuiCraftConfirm puts its search field at xSize - 101.
    private static final int PLAN_SEARCH_FROM_RIGHT = 101;

    private EtaInfoIcon() {}

    /** Status screen: in the gap between AE2's elapsed time and the remaining-operations count. */
    public static void drawOnCpuScreen(GuiCraftingCPU gui, int remainingOperations, int mouseX, int mouseY) {
        if (!Config.showCraftingScreenEta) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int right = CPU_OPERATIONS_RIGHT - font.getStringWidth(String.valueOf(remainingOperations)) - GAP;
        draw(gui, CraftingScreenInfo.INSTANCE.cpuInfoLines(gui), right, mouseX, mouseY);
    }

    /** Plan screen: just left of the search field. */
    public static void drawOnPlanScreen(GuiCraftConfirm gui, int mouseX, int mouseY) {
        if (!Config.showCraftingScreenEta) {
            return;
        }
        // AE2 centers its windows, so the window's width follows from guiLeft.
        int windowWidth = gui.width - 2 * gui.getGuiLeft();
        draw(gui, CraftingScreenInfo.INSTANCE.planInfoLines(gui), windowWidth - PLAN_SEARCH_FROM_RIGHT - GAP, mouseX, mouseY);
    }

    private static void draw(AEBaseGui gui, String[] lines, int right, int mouseX, int mouseY) {
        if (lines == null) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int width = font.getStringWidth(ICON);
        int x = right - width;
        font.drawString(ICON, x, TITLE_TOP, ICON_COLOR);

        int relX = mouseX - gui.getGuiLeft();
        int relY = mouseY - gui.getGuiTop();
        boolean hovered = relX >= x && relX < x + width && relY >= TITLE_TOP && relY < TITLE_TOP + font.FONT_HEIGHT;
        if (hovered) {
            gui.drawTooltip(relX, relY, lines);
        }
    }
}
