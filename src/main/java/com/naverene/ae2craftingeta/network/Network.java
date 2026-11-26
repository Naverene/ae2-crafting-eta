package com.naverene.ae2craftingeta.network;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.naverene.ae2craftingeta.AE2CraftingEta;
import com.naverene.ae2craftingeta.Tags;
import com.naverene.ae2craftingeta.data.TimingStats;
import com.naverene.ae2craftingeta.data.TimingStore;

import appeng.api.storage.data.IAEStack;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class Network {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(AE2CraftingEta.MODID);

    private Network() {}

    public static void init() {
        CHANNEL.registerMessage(PacketEta.Handler.class, PacketEta.class, 0, Side.CLIENT);
        CHANNEL.registerMessage(PacketStats.Handler.class, PacketStats.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(PacketCpuStatus.Handler.class, PacketCpuStatus.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(PacketHello.Handler.class, PacketHello.class, 3, Side.SERVER);
    }

    /** Players whose client said hello with this exact version: only they get our packets. */
    private static final Set<UUID> CLIENTS = ConcurrentHashMap.newKeySet();
    /** Players that said hello since the last server tick and still need the full timing table. */
    private static final Set<UUID> NEED_STATS = ConcurrentHashMap.newKeySet();

    /**
     * The mod is optional on clients, so only talk to players whose client runs this exact version: a client
     * without the mod has no handler for the channel, and another version may read the packets differently.
     */
    public static boolean canSendTo(EntityPlayerMP player) {
        return player != null && CLIENTS.contains(player.getUniqueID());
    }

    /** Called from the network thread when a client says hello. */
    static void onHello(EntityPlayerMP player, String version) {
        if (player == null) {
            return;
        }
        if (Tags.VERSION.equals(version)) {
            CLIENTS.add(player.getUniqueID());
            NEED_STATS.add(player.getUniqueID());
        } else {
            CLIENTS.remove(player.getUniqueID());
            AE2CraftingEta.LOG.info(
                "{} runs AE2 Crafting ETA {} but the server runs {}; not sending estimates to them",
                player.getCommandSenderName(),
                version,
                Tags.VERSION);
        }
    }

    public static void forget(EntityPlayerMP player) {
        if (player != null) {
            CLIENTS.remove(player.getUniqueID());
            NEED_STATS.remove(player.getUniqueID());
        }
    }

    /** On the server thread: send the full timing table to players that just said hello. */
    public static void sendPendingStats(TimingStore store) {
        if (NEED_STATS.isEmpty() || store == null) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }
        for (Iterator<UUID> it = NEED_STATS.iterator(); it.hasNext();) {
            UUID id = it.next();
            it.remove();
            for (Object o : server.getConfigurationManager().playerEntityList) {
                if (o instanceof EntityPlayerMP player && id.equals(player.getUniqueID())) {
                    sendAllStats(store, player);
                }
            }
        }
    }

    public static void sendTo(IMessage message, EntityPlayerMP player) {
        if (canSendTo(player)) {
            CHANNEL.sendTo(message, player);
        }
    }

    public static void sendStatsToAll(IAEStack<?> key, TimingStats stats) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }
        PacketStats packet = new PacketStats(Collections.singletonMap(key, stats), false);
        for (Object o : server.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP player) {
                sendTo(packet, player);
            }
        }
    }

    public static void sendAllStats(TimingStore store, EntityPlayerMP player) {
        if (!canSendTo(player)) {
            return;
        }
        Map<IAEStack<?>, TimingStats> all = store.table()
            .all();
        CHANNEL.sendTo(new PacketStats(all, true), player);
    }
}
