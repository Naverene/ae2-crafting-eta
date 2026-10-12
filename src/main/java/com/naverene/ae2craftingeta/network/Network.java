package com.naverene.ae2craftingeta.network;

import java.util.Collections;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;

import com.naverene.ae2craftingeta.AE2CraftingEta;
import com.naverene.ae2craftingeta.data.TimingStats;
import com.naverene.ae2craftingeta.data.TimingStore;

import appeng.api.storage.data.IAEStack;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class Network {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(AE2CraftingEta.MODID);

    private Network() {}

    public static void init() {
        CHANNEL.registerMessage(PacketEta.Handler.class, PacketEta.class, 0, Side.CLIENT);
        CHANNEL.registerMessage(PacketStats.Handler.class, PacketStats.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(PacketMachines.Handler.class, PacketMachines.class, 2, Side.CLIENT);
    }

    public static void sendStatsToAll(IAEStack<?> key, TimingStats stats) {
        CHANNEL.sendToAll(new PacketStats(Collections.singletonMap(key, stats), false));
    }

    public static void sendAllStats(TimingStore store, EntityPlayerMP player) {
        Map<IAEStack<?>, TimingStats> all = store.table()
            .all();
        CHANNEL.sendTo(new PacketStats(all, true), player);
    }
}
