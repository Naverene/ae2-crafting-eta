package com.naverene.ae2craftingeta.mixins.late;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.naverene.ae2craftingeta.client.ClientCpuWatcher;
import com.naverene.ae2craftingeta.client.EtaInfoIcon;
import com.naverene.ae2craftingeta.client.MachineTooltip;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.container.implementations.CraftingCpuEntry;

@Mixin(value = GuiCraftingCPU.class, remap = false)
public abstract class MixinGuiCraftingCPU {

    @Shadow
    private int remainingOperations;

    @Inject(method = "postVisualEntryUpdate", at = @At("HEAD"))
    private void ae2craftingeta$watchUpdate(CraftingCpuEntry[] updates, boolean clearFirst, int remainingOperations,
        CallbackInfo ci) {
        ClientCpuWatcher.onUpdate(updates, clearFirst);
    }

    @Inject(method = "drawFG", at = @At("TAIL"))
    private void ae2craftingeta$drawInfoIcon(int offsetX, int offsetY, int mouseX, int mouseY, CallbackInfo ci) {
        EtaInfoIcon.draw((GuiCraftingCPU) (Object) this, this.remainingOperations, mouseX, mouseY);
    }

    // require = 0: if AE2 renames this method, the tooltip just goes without machine lines.
    @Inject(method = "buildTooltipMessage", at = @At("RETURN"), cancellable = true, require = 0)
    private void ae2craftingeta$addMachines(IAEStack<?> refStack, List<String> lineList, boolean stackChanged,
        CallbackInfoReturnable<String> cir) {
        String extra = MachineTooltip.lines(refStack);
        if (extra != null) {
            cir.setReturnValue(cir.getReturnValue() + extra);
        }
    }
}
