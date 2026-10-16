package com.maza.client.module.modules.client.notifications;

import com.maza.client.module.modules.client.NotificationManager;
import com.maza.client.setting.SettingColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class Notification {
    private final String title;
    private final String message;
    private final NotificationType type;
    private long startTime;
    private long duration;
    private boolean fadingOut = false;
    private float animationProgress = 0f;

    public Notification(String title, String message, NotificationType type, long duration) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.duration = duration;
        this.startTime = System.currentTimeMillis();
    }

    public void update(float tickDelta) {
        long elapsed = System.currentTimeMillis() - startTime;
        float animTime = NotificationManager.INSTANCE.animationTime.get();

        if (!fadingOut) {
            animationProgress = Math.min(1f, animationProgress + tickDelta / animTime);
        } else {
            float fadeTime = NotificationManager.INSTANCE.fadeTime.get();
            animationProgress = Math.max(0f, animationProgress - tickDelta * 2 / fadeTime);
        }
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - startTime > duration;
    }

    public boolean isFadingOut() {
        return fadingOut;
    }

    public void startFadeOut() {
        fadingOut = true;
    }

    public float getAlpha() {
        if (fadingOut) return animationProgress;
        return animationProgress;
    }

    public float getAnimationProgress() {
        return animationProgress;
    }

    public int getHeight(TextRenderer renderer) {
        int titleHeight = title.isEmpty() ? 0 : renderer.fontHeight + 2;
        int messageHeight = renderer.fontHeight;
        return titleHeight + messageHeight + 12;
    }

    public void render(DrawContext context, int x, int y, int width, int height) {
        int alpha = (int)(255 * getAlpha());
        if (alpha <= 0) return;

        SettingColor bgColor = NotificationManager.INSTANCE.bgColor.get();
        SettingColor borderColor = NotificationManager.INSTANCE.borderColor.get();

        int bgPacked = bgColor.getPacked();
        int borderPacked = borderColor.getPacked();

        int bgWithAlpha = (bgPacked & 0x00FFFFFF) | ((bgColor.a * alpha / 255) << 24);
        int borderWithAlpha = (borderPacked & 0x00FFFFFF) | ((borderColor.a * alpha / 255) << 24);

        context.fill(x, y, x + width, y + height, bgWithAlpha);

        context.fill(x, y, x + width, y + 1, borderWithAlpha);
        context.fill(x, y + height - 1, x + width, y + height, borderWithAlpha);
        context.fill(x, y, x + 1, y + height, borderWithAlpha);
        context.fill(x + width - 1, y, x + width, y + height, borderWithAlpha);

        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;

        int textY = y + 6;
        int textX = x + 8;

        if (!title.isEmpty()) {
            context.drawTextWithShadow(renderer, title, textX, textY, 0xFFFFFFFF);
            textY += renderer.fontHeight + 2;
        }

        SettingColor typeColor = type.getColor();
        int typePacked = (typeColor.getPacked() & 0x00FFFFFF) | ((typeColor.a * alpha / 255) << 24);

        context.drawTextWithShadow(renderer, message, textX, textY, typePacked);
    }
}