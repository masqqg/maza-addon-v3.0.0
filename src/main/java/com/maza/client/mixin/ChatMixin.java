package com.maza.client.mixin;

import com.maza.client.module.CommandManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ChatMixin {
    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void onSendChatMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
        String message = packet.chatMessage();
        if (message.startsWith("/") || message.startsWith(".")) {
            if (CommandManager.INSTANCE.handleCommand(message)) {
                ci.cancel();
            }
        }
    }
}
