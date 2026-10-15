package com.maza.client.event;

import net.minecraft.client.gui.DrawContext;

public class Render2DEvent extends Event {
    public final DrawContext context;
    public final DrawContext drawContext;
    public final float tickDelta;
    public final int width;
    public final int height;

    public Render2DEvent(DrawContext context, float tickDelta, int width, int height) {
        this.context = context;
        this.drawContext = context;
        this.tickDelta = tickDelta;
        this.width = width;
        this.height = height;
    }
}
