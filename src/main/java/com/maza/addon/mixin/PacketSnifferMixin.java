package com.maza.addon.mixin;

import com.maza.addon.utils.PacketLogger;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class PacketSnifferMixin {

    @Inject(method = "channelRead0", at = @At("HEAD"))
    private void onPacketReceived(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        PacketLogger.onPacket(packet);
    }
}
