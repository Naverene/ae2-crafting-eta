package com.naverene.ae2craftingeta.mixins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import cpw.mods.fml.relauncher.FMLLaunchHandler;

@LateMixin
public class LateMixinsLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.ae2craftingeta.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<>(
            Arrays.asList(
                "MixinCraftingCPUCluster",
                "AccessorCraftingCPUCluster",
                "AccessorTaskProgress",
                "AccessorContainerCraftConfirm"));
        if (FMLLaunchHandler.side()
            .isClient()) {
            // Client-only learning for servers without this mod.
            mixins.add("AccessorGuiCraftConfirm");
            mixins.add("MixinGuiCraftConfirm");
            mixins.add("MixinGuiCraftingCPU");
            mixins.add("MixinPacketCraftingCompleteNotification");
        }
        return mixins;
    }
}
