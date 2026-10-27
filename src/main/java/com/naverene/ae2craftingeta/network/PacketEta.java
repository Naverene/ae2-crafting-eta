package com.naverene.ae2craftingeta.network;

import com.naverene.ae2craftingeta.client.ClientEtaState;
import com.naverene.ae2craftingeta.eta.EtaResult;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** The estimate for the crafting screen the player has open. */
public class PacketEta implements IMessage {

    public static final int NONE = 0;
    /** Crafting plan screen, before the job starts. */
    public static final int PLAN = 1;
    /** Crafting status screen of a busy CPU. */
    public static final int RUNNING = 2;
    /** Crafting status screen of a busy CPU that is stalled behind another CPU's job. */
    public static final int PAUSED = 3;

    private int mode;
    private long ticks;
    private int unknownSteps;
    private int totalSteps;
    private long waitTicks;
    private int otherJobs;

    public PacketEta() {}

    public PacketEta(int mode, EtaResult eta) {
        this.mode = eta == null ? NONE : mode;
        if (eta != null) {
            this.ticks = eta.ticks;
            this.unknownSteps = eta.unknownSteps;
            this.totalSteps = eta.totalSteps;
            this.waitTicks = eta.waitTicks;
            this.otherJobs = eta.otherJobs;
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        mode = buf.readByte();
        ticks = buf.readLong();
        unknownSteps = buf.readInt();
        totalSteps = buf.readInt();
        waitTicks = buf.readLong();
        otherJobs = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode);
        buf.writeLong(ticks);
        buf.writeInt(unknownSteps);
        buf.writeInt(totalSteps);
        buf.writeLong(waitTicks);
        buf.writeInt(otherJobs);
    }

    public static class Handler implements IMessageHandler<PacketEta, IMessage> {

        @Override
        public IMessage onMessage(PacketEta message, MessageContext ctx) {
            ClientEtaState.set(
                message.mode,
                new EtaResult(
                    message.ticks,
                    message.unknownSteps,
                    message.totalSteps,
                    message.waitTicks,
                    message.otherJobs));
            return null;
        }
    }
}
