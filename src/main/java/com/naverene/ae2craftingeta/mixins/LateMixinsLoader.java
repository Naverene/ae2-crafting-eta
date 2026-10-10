package com.naverene.ae2craftingeta.mixins;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

@LateMixin
public class LateMixinsLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.ae2craftingeta.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        return Arrays.asList(
            "MixinCraftingCPUCluster",
            "AccessorCraftingCPUCluster",
            "AccessorTaskProgress",
            "AccessorContainerCraftConfirm");
    }
}
