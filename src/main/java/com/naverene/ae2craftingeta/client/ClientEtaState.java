package com.naverene.ae2craftingeta.client;

/** Latest estimate from the server for the open crafting screen. Plain Java so the dedicated server can load it. */
public final class ClientEtaState {

    public static volatile int mode;
    public static volatile long ticks;
    public static volatile int unknownSteps;
    public static volatile int totalSteps;

    private ClientEtaState() {}

    public static void set(int mode, long ticks, int unknownSteps, int totalSteps) {
        ClientEtaState.ticks = ticks;
        ClientEtaState.unknownSteps = unknownSteps;
        ClientEtaState.totalSteps = totalSteps;
        ClientEtaState.mode = mode;
    }
}
