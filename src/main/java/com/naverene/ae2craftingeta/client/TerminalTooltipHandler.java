package com.naverene.ae2craftingeta.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.naverene.ae2craftingeta.Config;
import com.naverene.ae2craftingeta.data.StackKeys;
import com.naverene.ae2craftingeta.data.TimingStats;

import appeng.client.gui.implementations.GuiMEMonitorable;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Adds "Last crafted 64 in 2m 10s" to item tooltips inside ME terminals. */
public class TerminalTooltipHandler {

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        if (!Config.showTerminalTooltips || !(Minecraft.getMinecraft().currentScreen instanceof GuiMEMonitorable)) {
            return;
        }
        TimingStats stats = ClientStatsCache.find(StackKeys.of(event.itemStack));
        if (stats == null) {
            return;
        }
        if (stats.lastJobAmount > 0 && stats.lastJobMillis > 0) {
            double seconds = stats.lastJobMillis / 1000.0;
            event.toolTip.add(
                    EnumChatFormatting.GRAY + "Last crafted "
                            + stats.lastJobAmount
                            + " in "
                            + TimeFormat.format(seconds)
                            + " (about "
                            + TimeFormat.format(seconds / stats.lastJobAmount)
                            + " each)");
        } else if (stats.rateSamples > 0 && stats.itemsPerTick > 0) {
            event.toolTip.add(
                    EnumChatFormatting.GRAY + "Crafts at about "
                            + TimeFormat.format(1.0 / (stats.itemsPerTick * 20.0))
                            + " each");
        }
    }
}
