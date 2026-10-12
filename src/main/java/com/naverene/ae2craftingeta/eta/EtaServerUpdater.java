package com.naverene.ae2craftingeta.eta;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.server.MinecraftServer;

import com.naverene.ae2craftingeta.AE2CraftingEta;
import com.naverene.ae2craftingeta.data.CpuTracker;
import com.naverene.ae2craftingeta.data.TimingStore;
import com.naverene.ae2craftingeta.mixins.late.AccessorContainerCraftConfirm;
import com.naverene.ae2craftingeta.network.Network;
import com.naverene.ae2craftingeta.network.PacketEta;
import com.naverene.ae2craftingeta.network.PacketMachines;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Sends players the estimate for whichever crafting screen they have open, and the tooltip data on login. */
public class EtaServerUpdater {

    private static final int INTERVAL_TICKS = 10;

    private final Map<EntityPlayerMP, Sent> lastSent = new WeakHashMap<>();
    private int tick;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % INTERVAL_TICKS != 0) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }
        for (Object o : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) o;
            try {
                update(player);
            } catch (RuntimeException e) {
                // Never let an estimate break the server tick.
                AE2CraftingEta.LOG.debug("Could not estimate crafting time", e);
            }
        }
    }

    private void update(EntityPlayerMP player) {
        Container container = player.openContainer;
        Sent previous = lastSent.get(player);
        if (container instanceof ContainerCraftConfirm confirm) {
            ICraftingJob<?> job = ((AccessorContainerCraftConfirm) confirm).ae2craftingeta$getResult();
            IGrid grid = confirm.getGrid();
            if (job == null || grid == null) {
                send(player, previous, PacketEta.NONE, null, container);
                return;
            }
            int coProcessors = confirm.getCpuCoProcessors();
            // Planning is the expensive part; reuse the estimate until the job or the selected CPU changes.
            if (previous != null && previous.container == container
                && previous.source == job
                && previous.coProcessors == coProcessors) {
                return;
            }
            EtaResult eta = EtaCalculator
                .forPlan(job, grid.getCache(ICraftingGrid.class), player.worldObj, coProcessors);
            Sent sent = send(player, previous, PacketEta.PLAN, eta, container);
            sent.source = job;
            sent.coProcessors = coProcessors;
        } else if (container instanceof ContainerCraftingCPU status) {
            CraftingCPUCluster cpu = status.getCpu();
            if (cpu == null || !cpu.isBusy()) {
                send(player, previous, PacketEta.NONE, null, container);
                return;
            }
            send(player, previous, PacketEta.RUNNING, EtaCalculator.forRunningCpu(cpu), container);
            // Machine progress changes every tick, so this goes out on every update rather than only on change.
            Network.CHANNEL.sendTo(
                new PacketMachines(
                    CpuTracker.of(cpu)
                        .machines()),
                player);
        } else if (previous != null && previous.mode != PacketEta.NONE) {
            send(player, previous, PacketEta.NONE, null, container);
        }
    }

    private Sent send(EntityPlayerMP player, Sent previous, int mode, EtaResult eta, Container container) {
        boolean same = previous != null && previous.mode == mode
            && previous.container == container
            && (eta == null ? previous.eta == null : eta.equals(previous.eta));
        if (same) {
            return previous;
        }
        Network.CHANNEL.sendTo(new PacketEta(mode, eta), player);
        Sent sent = new Sent(mode, eta, container);
        lastSent.put(player, sent);
        return sent;
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        TimingStore store = TimingStore.get();
        if (store != null && event.player instanceof EntityPlayerMP player) {
            Network.sendAllStats(store, player);
        }
    }

    private static final class Sent {

        final int mode;
        final EtaResult eta;
        final Container container;
        Object source;
        int coProcessors;

        Sent(int mode, EtaResult eta, Container container) {
            this.mode = mode;
            this.eta = eta;
            this.container = container;
        }
    }
}
