package com.naverene.ae2craftingeta.mixins.late;

import java.util.concurrent.TimeUnit;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.naverene.ae2craftingeta.client.ClientTimings;

import appeng.api.storage.data.IAEStack;
import appeng.core.sync.packets.PacketCraftingCompleteNotification;

/** AE2 tells players who followed a craft how long it took; remember that for tooltips when the server can't. */
@Mixin(value = PacketCraftingCompleteNotification.class, remap = false)
public abstract class MixinPacketCraftingCompleteNotification {

    @Shadow
    @Final
    private IAEStack<?> stack;

    @Shadow
    @Final
    private long numsOfOutput;

    @Shadow
    @Final
    private long elapsedTime;

    @Inject(method = "clientPacketData", at = @At("HEAD"))
    private void ae2craftingeta$recordJob(CallbackInfo ci) {
        ClientTimings.recordJob(this.stack, this.numsOfOutput, TimeUnit.NANOSECONDS.toMillis(this.elapsedTime));
        ClientTimings.saveLocal();
    }
}
