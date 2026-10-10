package com.naverene.ae2craftingeta;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = AE2CraftingEta.MODID,
    version = Tags.VERSION,
    name = "AE2 Crafting ETA",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:appliedenergistics2")
public class AE2CraftingEta {

    public static final String MODID = "ae2craftingeta";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "com.naverene.ae2craftingeta.ClientProxy",
        serverSide = "com.naverene.ae2craftingeta.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
