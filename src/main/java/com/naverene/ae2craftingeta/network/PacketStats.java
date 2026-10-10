package com.naverene.ae2craftingeta.network;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.naverene.ae2craftingeta.client.ClientStatsCache;
import com.naverene.ae2craftingeta.data.StackKeys;
import com.naverene.ae2craftingeta.data.TimingStats;

import appeng.api.storage.data.IAEStack;
import appeng.util.Platform;
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
        stats = new HashMap<>();
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        if (tag == null) {
            return;
        }
        NBTTagList list = tag.getTagList("Entries", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            IAEStack<?> key = StackKeys.of(Platform.readStackNBT(entry.getCompoundTag("Stack")));
            if (key != null) {
                stats.put(key, TimingStats.fromNBT(entry));
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(replaceAll);
        NBTTagList list = new NBTTagList();
        for (Map.Entry<IAEStack<?>, TimingStats> e : stats.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setTag(
                "Stack",
                e.getKey()
                    .toNBTGeneric());
            e.getValue()
                .writeToNBT(entry);
            list.appendTag(entry);
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("Entries", list);
        ByteBufUtils.writeTag(buf, tag);
    }

    public static class Handler implements IMessageHandler<PacketStats, IMessage> {

        @Override
        public IMessage onMessage(PacketStats message, MessageContext ctx) {
            ClientStatsCache.update(message.stats, message.replaceAll);
            return null;
        }
    }
}
