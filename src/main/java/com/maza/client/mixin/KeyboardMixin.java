package com.maza.client.mixin;

import com.maza.client.event.EventManager;
import com.maza.client.event.KeyEvent;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true, require = 0)
    private void onKeyHook(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle()) {
            KeyEvent event = new KeyEvent(key, scancode, action, modifiers);
            EventManager.INSTANCE.post(event);
            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }
}
