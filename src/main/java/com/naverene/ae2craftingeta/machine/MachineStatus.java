package com.naverene.ae2craftingeta.machine;

import net.minecraft.item.ItemStack;

/** What one machine was doing when it was looked at. Progress is in ticks. */
public final class MachineStatus {

    /** The machine as an item, for its name. */
    public final ItemStack machine;
    public final int progress;
    public final int maxProgress;
    public final boolean active;

    public MachineStatus(ItemStack machine, int progress, int maxProgress, boolean active) {
        this.machine = machine;
        this.progress = progress;
        this.maxProgress = maxProgress;
        this.active = active;
    }

    public boolean isWorking() {
        return active && maxProgress > 0;
    }

    public int ticksLeft() {
        return isWorking() ? Math.max(0, maxProgress - progress) : 0;
    }
}
