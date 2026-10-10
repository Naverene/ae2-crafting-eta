package com.naverene.ae2craftingeta.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.api.networking.crafting.ICraftingJob;
import appeng.container.implementations.ContainerCraftConfirm;

@Mixin(value = ContainerCraftConfirm.class, remap = false)
public interface AccessorContainerCraftConfirm {

    @Accessor("result")
    ICraftingJob<?> ae2craftingeta$getResult();
}
