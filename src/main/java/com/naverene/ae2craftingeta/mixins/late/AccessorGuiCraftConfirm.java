package com.naverene.ae2craftingeta.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.crafting.v2.CraftingJobV2;

@Mixin(value = GuiCraftConfirm.class, remap = false)
public interface AccessorGuiCraftConfirm {

    @Accessor("jobTree")
    CraftingJobV2<?> ae2craftingeta$getJobTree();
}
