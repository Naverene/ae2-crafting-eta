package com.naverene.ae2craftingeta.eta;

public final class EtaResult {

    public final long ticks;
    /** Steps that have never been measured, so a guessed time was used. */
    public final int unknownSteps;
    public final int totalSteps;
    /** Part of {@link #ticks} spent waiting for other CPUs' jobs on shared machines. */
    public final long waitTicks;
    /** Other busy CPUs on the network when the estimate was made. */
    public final int otherJobs;

    public EtaResult(long ticks, int unknownSteps, int totalSteps) {
        this(ticks, unknownSteps, totalSteps, 0, 0);
    }

    public EtaResult(long ticks, int unknownSteps, int totalSteps, long waitTicks, int otherJobs) {
        this.ticks = ticks;
        this.unknownSteps = unknownSteps;
        this.totalSteps = totalSteps;
        this.waitTicks = Math.max(0, Math.min(waitTicks, ticks));
        this.otherJobs = otherJobs;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EtaResult other && other.ticks == ticks
            && other.unknownSteps == unknownSteps
            && other.totalSteps == totalSteps
            && other.waitTicks == waitTicks
            && other.otherJobs == otherJobs;
    }

    @Override
    public int hashCode() {
        return ((Long.hashCode(ticks) * 31 + unknownSteps) * 31 + totalSteps) * 31 + Long.hashCode(waitTicks) * 31
            + otherJobs;
    }
}
