package com.naverene.ae2craftingeta.mixins.late;

import net.minecraft.inventory.InventoryCrafting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.naverene.ae2craftingeta.data.CpuTracker;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class MixinCraftingCPUCluster {

    @Shadow
    protected CraftingCPUCluster.finalOutput finalOutput;

    @Shadow
    protected long elapsedTime;

    @WrapOperation(
        method = "executeCrafting",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z"))
    private boolean ae2craftingeta$trackPush(ICraftingMedium medium, ICraftingPatternDetails details,
        InventoryCrafting table, Operation<Boolean> original) {
        boolean pushed = original.call(medium, details, table);
        if (pushed) {
            CpuTracker.of(this)
                .onPush(details);
        }
        return pushed;
    }

    @Inject(method = "recordReturnedOutputs", at = @At("HEAD"))
    private void ae2craftingeta$trackReturn(IAEStack<?> returnedStack, CallbackInfo ci) {
        CpuTracker.of(this)
            .onReturn(returnedStack);
    }

    // After completeJob's early return for unfinished jobs, before AE2 resets the elapsed time.
    @Inject(method = "completeJob", at = @At(value = "INVOKE", target = "Lappeng/core/AELog;isCraftingLogEnabled()Z"))
    private void ae2craftingeta$trackComplete(CallbackInfo ci) {
        CpuTracker.of(this)
            .onComplete(this.finalOutput.getOriginalOutput(), this.finalOutput.getOriginalCount(), this.elapsedTime);
    }

    @Inject(method = "cancel", at = @At("HEAD"))
    private void ae2craftingeta$trackCancel(CallbackInfo ci) {
        CpuTracker.of(this)
            .onCancel();
    }
}
