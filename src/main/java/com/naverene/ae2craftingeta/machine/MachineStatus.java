package com.naverene.ae2craftingeta.machine;

import net.minecraft.item.ItemStack;

/** What one machine was doing when it was looked at. Progress is in ticks. */
public final class MachineStatus {

    /** The machine as an item, for its name. */
    public final ItemStack machine;
    public final int progress;
    public final int maxProgress;
    public final boolean active;
    /** Why the machine stopped, if it was forced to (power loss and the like), otherwise null. */
    public final String shutdownReason;

    public MachineStatus(ItemStack machine, int progress, int maxProgress, boolean active, String shutdownReason) {
        this.machine = machine;
        this.progress = progress;
        this.maxProgress = maxProgress;
        this.active = active;
        this.shutdownReason = shutdownReason;
    }

    public boolean isWorking() {
        return active && maxProgress > 0;
    }

    public int ticksLeft() {
        return isWorking() ? Math.max(0, maxProgress - progress) : 0;
    }
}
