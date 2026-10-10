package com.naverene.ae2craftingeta.client;

import net.minecraftforge.client.event.GuiOpenEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/** Loads and saves the client's own learned timings around each server connection. */
public class ClientConnectionHandler {

    @SubscribeEvent
    public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        ClientTimings.onConnect();
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ClientTimings.saveLocal();
    }

    /** Saves whenever a screen changes, so a crash loses at most what one screen learned. */
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        ClientTimings.saveLocal();
    }
}
