package com.naverene.ae2craftingeta.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.naverene.ae2craftingeta.client.ClientMachineState;
import com.naverene.ae2craftingeta.machine.MachineStatus;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** What the machines are doing for each item in progress on the crafting status screen the player has open. */
public class PacketMachines implements IMessage {

    private static final int MAX_MACHINES_PER_ITEM = 8;

    private final Map<ItemStack, List<MachineStatus>> machines = new LinkedHashMap<>();

    public PacketMachines() {}

    public PacketMachines(Map<IAEStack<?>, List<MachineStatus>> byOutput) {
        for (Map.Entry<IAEStack<?>, List<MachineStatus>> e : byOutput.entrySet()) {
            // Machines making fluids are skipped; the status screen tooltip is keyed by item.
            if (e.getKey() instanceof IAEItemStack item) {
                machines.put(item.getItemStack(), e.getValue());
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int outputs = buf.readShort();
        for (int i = 0; i < outputs; i++) {
            ItemStack output = ByteBufUtils.readItemStack(buf);
            int count = buf.readByte();
            List<MachineStatus> list = new ArrayList<>(count);
            for (int j = 0; j < count; j++) {
                ItemStack machine = ByteBufUtils.readItemStack(buf);
                int progress = buf.readInt();
                int maxProgress = buf.readInt();
                boolean active = buf.readBoolean();
                list.add(new MachineStatus(machine, progress, maxProgress, active));
            }
            if (output != null) {
                machines.put(output, list);
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(machines.size());
        for (Map.Entry<ItemStack, List<MachineStatus>> e : machines.entrySet()) {
            ByteBufUtils.writeItemStack(buf, e.getKey());
            List<MachineStatus> list = e.getValue();
            int count = Math.min(list.size(), MAX_MACHINES_PER_ITEM);
            buf.writeByte(count);
            for (int j = 0; j < count; j++) {
                MachineStatus status = list.get(j);
                ByteBufUtils.writeItemStack(buf, status.machine);
                buf.writeInt(status.progress);
                buf.writeInt(status.maxProgress);
                buf.writeBoolean(status.active);
            }
        }
    }

    public static class Handler implements IMessageHandler<PacketMachines, IMessage> {

        @Override
        public IMessage onMessage(PacketMachines message, MessageContext ctx) {
            ClientMachineState.set(message.machines);
            return null;
        }
    }
}
