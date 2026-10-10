package com.naverene.ae2craftingeta.network;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

import com.naverene.ae2craftingeta.client.ClientTimings;
import com.naverene.ae2craftingeta.data.TimingStats;
import com.naverene.ae2craftingeta.data.TimingTable;

import appeng.api.storage.data.IAEStack;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Learned timings for terminal tooltips: everything on login, then single entries as they change. */
public class PacketStats implements IMessage {

    private Map<IAEStack<?>, TimingStats> stats;
    private boolean replaceAll;

    public PacketStats() {}

    public PacketStats(Map<IAEStack<?>, TimingStats> stats, boolean replaceAll) {
        this.stats = stats;
        this.replaceAll = replaceAll;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        replaceAll = buf.readBoolean();
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        stats = tag == null ? new HashMap<>() : TimingTable.readEntries(tag);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(replaceAll);
        NBTTagCompound tag = new NBTTagCompound();
        TimingTable.writeEntries(stats, tag);
        ByteBufUtils.writeTag(buf, tag);
    }

    public static class Handler implements IMessageHandler<PacketStats, IMessage> {

        @Override
        public IMessage onMessage(PacketStats message, MessageContext ctx) {
            ClientTimings.onServerStats(message.stats, message.replaceAll);
            return null;
        }
    }
}
