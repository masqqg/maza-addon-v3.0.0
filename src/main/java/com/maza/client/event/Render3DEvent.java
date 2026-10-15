package com.maza.client.event;

import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;

public class Render3DEvent extends Event {
    public final MatrixStack matrixStack;
    public final Camera camera;
    public final float tickDelta;

    public Render3DEvent(MatrixStack matrixStack, Camera camera, float tickDelta) {
        this.matrixStack = matrixStack;
        this.camera = camera;
        this.tickDelta = tickDelta;
    }
}
