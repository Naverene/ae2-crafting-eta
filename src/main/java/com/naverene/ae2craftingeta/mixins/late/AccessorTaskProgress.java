package com.naverene.ae2craftingeta.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.me.cluster.implementations.CraftingCPUCluster;

@Mixin(value = CraftingCPUCluster.TaskProgress.class, remap = false)
public interface AccessorTaskProgress {

    @Accessor("value")
    long ae2craftingeta$getValue();
}
