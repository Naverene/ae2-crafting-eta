package com.naverene.ae2craftingeta.data;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;

/** Turns stacks into map keys that ignore amount and craftable flags, the same way AE2's own diagnostics do. */
public final class StackKeys {

    private StackKeys() {}

    public static IAEStack<?> of(IAEStack<?> stack) {
        if (stack == null) {
            return null;
        }
        IAEStack<?> key = stack.copy();
        key.reset();
        key.setStackSize(1);
        return key;
    }

    public static IAEStack<?> of(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        return of(AEApi.instance().storage().createItemStack(stack));
    }

    /** The output a pattern is tracked under: its first (main) output. */
    public static IAEStack<?> primaryOutput(ICraftingPatternDetails details) {
        IAEStack<?>[] outputs = details.getCondensedAEOutputs();
        if (outputs == null || outputs.length == 0 || outputs[0] == null) {
            return null;
        }
        return outputs[0];
    }
}
