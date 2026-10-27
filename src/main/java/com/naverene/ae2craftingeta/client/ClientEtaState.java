package com.naverene.ae2craftingeta.client;

import com.naverene.ae2craftingeta.eta.EtaResult;

/** Latest estimate from the server for the open crafting screen. Plain Java so the dedicated server can load it. */
public final class ClientEtaState {

    public static volatile int mode;
    private static volatile EtaResult result = new EtaResult(0, 0, 0);

    private ClientEtaState() {}

    public static void set(int mode, EtaResult result) {
        ClientEtaState.result = result;
        ClientEtaState.mode = mode;
    }

    public static EtaResult result() {
        return result;
    }
}
