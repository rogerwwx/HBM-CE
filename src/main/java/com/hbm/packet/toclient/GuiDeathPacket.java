package com.hbm.packet.toclient;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class GuiDeathPacket implements IMessage {

    public GuiDeathPacket() {
    }

    private String deathMessage;

    public GuiDeathPacket(String deathMessage) {
        this.deathMessage = deathMessage;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] msgBytes = deathMessage.getBytes();
        buf.writeInt(msgBytes.length);
        buf.writeBytes(msgBytes);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int len = buf.readInt();
        byte[] msgBytes = new byte[len];
        buf.readBytes(msgBytes);
        deathMessage = new String(msgBytes);
    }

    // 客户端处理
    public static class Handler implements IMessageHandler<GuiDeathPacket, IMessage> {
        @Override
        public IMessage onMessage(GuiDeathPacket message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.world == null) return;
                ITextComponent deathText = new TextComponentString(
                        message.deathMessage != null ? message.deathMessage : "你死了"
                );
                mc.displayGuiScreen(new GuiGameOver(deathText));
            });
            return null;
        }
    }
}
