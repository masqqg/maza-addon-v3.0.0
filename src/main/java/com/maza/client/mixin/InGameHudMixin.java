package com.maza.client.mixin;

import com.maza.client.event.EventManager;
import com.maza.client.event.Render2DEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() != null) {
            float tickDelta = tickCounter.getTickProgress(true);
            int width = mc.getWindow().getScaledWidth();
            int height = mc.getWindow().getScaledHeight();
            EventManager.INSTANCE.post(new Render2DEvent(context, tickDelta, width, height));
        }
    }
}
