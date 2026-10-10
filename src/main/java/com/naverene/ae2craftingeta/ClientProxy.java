package com.naverene.ae2craftingeta;

import net.minecraftforge.common.MinecraftForge;

import com.naverene.ae2craftingeta.client.EtaOverlay;
import com.naverene.ae2craftingeta.client.TerminalTooltipHandler;

import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(new EtaOverlay());
        MinecraftForge.EVENT_BUS.register(new TerminalTooltipHandler());
    }
}
