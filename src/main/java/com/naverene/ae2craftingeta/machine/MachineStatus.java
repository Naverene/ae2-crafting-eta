package com.naverene.ae2craftingeta.machine;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** What one machine (or buffer inventory) was doing when it was looked at. Progress is in ticks. */
public final class MachineStatus {

    /** The machine as an item, for its name. */
    public final ItemStack machine;
    public final int progress;
    public final int maxProgress;
    public final boolean active;
    /** Why the machine stopped, if it was forced to (power loss and the like), otherwise null. */
    public final String shutdownReason;
    /** For something that is not a machine, such as a buffer chest, what it holds; otherwise null. */
    public final String detail;
    /**
     * What the machine's current recipe will output when it finishes, read on the server only (not sent to clients).
     * Null when the machine could not be read that far.
     */
    public ItemStack[] outputItems;
    public FluidStack[] outputFluids;

    public MachineStatus(ItemStack machine, int progress, int maxProgress, boolean active, String shutdownReason,
        String detail) {
        this.machine = machine;
        this.progress = progress;
        this.maxProgress = maxProgress;
        this.active = active;
        this.shutdownReason = shutdownReason;
        this.detail = detail;
    }

    public static MachineStatus buffer(ItemStack block, String detail) {
        return new MachineStatus(block, 0, 0, false, null, detail);
    }

    public boolean isWorking() {
        return active && maxProgress > 0;
    }

    public int ticksLeft() {
        return isWorking() ? Math.max(0, maxProgress - progress) : 0;
    }
}
