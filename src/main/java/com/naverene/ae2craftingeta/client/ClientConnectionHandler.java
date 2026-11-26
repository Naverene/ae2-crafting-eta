package com.naverene.ae2craftingeta.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiOpenEvent;

import com.naverene.ae2craftingeta.Tags;
import com.naverene.ae2craftingeta.network.Network;
import com.naverene.ae2craftingeta.network.PacketHello;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/** Loads and saves the client's own learned timings around each server connection. */
public class ClientConnectionHandler {

    /** Set on connect; the hello goes out once the player exists, so the server knows this client runs the mod. */
    private volatile boolean helloPending;

    @SubscribeEvent
    public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        ClientTimings.onConnect();
        helloPending = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !helloPending
            || Minecraft.getMinecraft().thePlayer == null
            || Minecraft.getMinecraft()
                .getNetHandler() == null) {
            return;
        }
        helloPending = false;
        Network.CHANNEL.sendToServer(new PacketHello(Tags.VERSION));
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        helloPending = false;
        ClientTimings.saveLocal();
    }

    /** Saves whenever a screen changes, so a crash loses at most what one screen learned. */
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        ClientTimings.saveLocal();
        ClientMachineState.clear();
    }
}
