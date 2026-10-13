package com.naverene.ae2craftingeta.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.naverene.ae2craftingeta.client.EtaInfoIcon;

import appeng.client.gui.implementations.GuiCraftConfirm;

@Mixin(value = GuiCraftConfirm.class, remap = false)
public abstract class MixinGuiCraftConfirm {

    @Inject(method = "drawFG", at = @At("TAIL"))
    private void ae2craftingeta$drawInfoIcon(int offsetX, int offsetY, int mouseX, int mouseY, CallbackInfo ci) {
        EtaInfoIcon.drawOnPlanScreen((GuiCraftConfirm) (Object) this, mouseX, mouseY);
    }
}
