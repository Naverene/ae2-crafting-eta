package com.naverene.ae2craftingeta.eta;

public final class EtaResult {

    public final long ticks;
    /** Steps that have never been measured, so a guessed time was used. */
    public final int unknownSteps;
    public final int totalSteps;

    public EtaResult(long ticks, int unknownSteps, int totalSteps) {
        this.ticks = ticks;
        this.unknownSteps = unknownSteps;
        this.totalSteps = totalSteps;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EtaResult other && other.ticks == ticks
                && other.unknownSteps == unknownSteps
                && other.totalSteps == totalSteps;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(ticks) * 31 * 31 + unknownSteps * 31 + totalSteps;
    }
}
