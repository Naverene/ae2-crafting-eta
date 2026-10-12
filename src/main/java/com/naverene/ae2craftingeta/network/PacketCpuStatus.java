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

/**
 * For the crafting status screen the player has open: what the machines are doing for each item in progress, and why
 * waiting items are stuck. Keyed by item; fluid outputs are left out because the screen's tooltip is keyed by item.
 */
public class PacketCpuStatus implements IMessage {

    private static final int MAX_MACHINES_PER_ITEM = 8;

    private final Map<ItemStack, List<MachineStatus>> machines = new LinkedHashMap<>();
    private final Map<ItemStack, List<String>> blocked = new LinkedHashMap<>();

    public PacketCpuStatus() {}

    public PacketCpuStatus(Map<IAEStack<?>, List<MachineStatus>> machinesByOutput,
        Map<IAEStack<?>, List<String>> blockedByOutput) {
        for (Map.Entry<IAEStack<?>, List<MachineStatus>> e : machinesByOutput.entrySet()) {
            if (e.getKey() instanceof IAEItemStack item) {
                machines.put(item.getItemStack(), e.getValue());
            }
        }
        for (Map.Entry<IAEStack<?>, List<String>> e : blockedByOutput.entrySet()) {
            if (e.getKey() instanceof IAEItemStack item) {
                blocked.put(item.getItemStack(), e.getValue());
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
                String shutdown = buf.readBoolean() ? ByteBufUtils.readUTF8String(buf) : null;
                String detail = buf.readBoolean() ? ByteBufUtils.readUTF8String(buf) : null;
                list.add(new MachineStatus(machine, progress, maxProgress, active, shutdown, detail));
            }
            if (output != null) {
                machines.put(output, list);
            }
        }
        int waiting = buf.readShort();
        for (int i = 0; i < waiting; i++) {
            ItemStack output = ByteBufUtils.readItemStack(buf);
            int count = buf.readByte();
            List<String> reasons = new ArrayList<>(count);
            for (int j = 0; j < count; j++) {
                reasons.add(ByteBufUtils.readUTF8String(buf));
            }
            if (output != null) {
                blocked.put(output, reasons);
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
                buf.writeBoolean(status.shutdownReason != null);
                if (status.shutdownReason != null) {
                    ByteBufUtils.writeUTF8String(buf, status.shutdownReason);
                }
                buf.writeBoolean(status.detail != null);
                if (status.detail != null) {
                    ByteBufUtils.writeUTF8String(buf, status.detail);
                }
            }
        }
        buf.writeShort(blocked.size());
        for (Map.Entry<ItemStack, List<String>> e : blocked.entrySet()) {
            ByteBufUtils.writeItemStack(buf, e.getKey());
            buf.writeByte(
                e.getValue()
                    .size());
            for (String reason : e.getValue()) {
                ByteBufUtils.writeUTF8String(buf, reason);
            }
        }
    }

    public static class Handler implements IMessageHandler<PacketCpuStatus, IMessage> {

        @Override
        public IMessage onMessage(PacketCpuStatus message, MessageContext ctx) {
            ClientMachineState.set(message.machines, message.blocked);
            return null;
        }
    }
}
