package com.naverene.ae2craftingeta.network;

import java.nio.charset.StandardCharsets;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client to server, once per connection: "I run AE2 Crafting ETA, this version". The mod is optional on clients, so
 * the server only sends its packets to players that said hello with the server's own version.
 */
public class PacketHello implements IMessage {

    private String version = "";

    public PacketHello() {}

    public PacketHello(String version) {
        this.version = version == null ? "" : version;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int len = Math.min(buf.readUnsignedShort(), buf.readableBytes());
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        version = new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] bytes = version.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    public static class Handler implements IMessageHandler<PacketHello, IMessage> {

        @Override
        public IMessage onMessage(PacketHello message, MessageContext ctx) {
            Network.onHello(ctx.getServerHandler().playerEntity, message.version);
            return null;
        }
    }
}
