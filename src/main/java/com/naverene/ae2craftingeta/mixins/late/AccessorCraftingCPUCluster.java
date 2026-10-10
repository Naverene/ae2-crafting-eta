package com.naverene.ae2craftingeta.mixins.late;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cluster.implementations.CraftingCPUCluster;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public interface AccessorCraftingCPUCluster {

    @Accessor("tasks")
    Map<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> ae2craftingeta$getTasks();
}
