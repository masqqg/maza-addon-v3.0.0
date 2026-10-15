package com.maza.client.mixin;

import com.maza.client.event.EventManager;
import com.maza.client.event.Render3DEvent;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Shadow
    public abstract GizmoDrawing.CollectorScope startDrawingGizmos();

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline,
                              Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix,
                              Matrix4f matrix4f2, GpuBufferSlice fog, Vector4f fogColor,
                              boolean shouldRenderSky, CallbackInfo ci) {
        try (GizmoDrawing.CollectorScope scope = startDrawingGizmos()) {
            MatrixStack matrixStack = new MatrixStack();
            matrixStack.multiplyPositionMatrix(positionMatrix);
            EventManager.INSTANCE.post(new Render3DEvent(matrixStack, camera, tickCounter.getTickProgress(true)));
        }
    }
}
