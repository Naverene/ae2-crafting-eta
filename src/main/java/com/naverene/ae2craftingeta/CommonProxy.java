package com.naverene.ae2craftingeta;

import com.naverene.ae2craftingeta.eta.EtaServerUpdater;
import com.naverene.ae2craftingeta.network.Network;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        Network.init();
    }

    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance().bus().register(new EtaServerUpdater());
    }
}
