package com.maza.client.utils;

import com.maza.client.setting.SettingColor;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.VisibilityConfigurable;

public class Render3DUtils {

    public static void drawBox(Box box, SettingColor sideColor, SettingColor lineColor, ShapeMode shapeMode, boolean ignoreOcclusion) {
        if (box == null) return;
        DrawStyle style;
        int fill = sideColor != null ? sideColor.getPacked() : 0;
        int stroke = lineColor != null ? lineColor.getPacked() : -1;

        if (shapeMode == ShapeMode.Lines) {
            style = DrawStyle.stroked(stroke, 1.5f);
        } else if (shapeMode == ShapeMode.Sides) {
            style = DrawStyle.filled(fill);
        } else {
            style = DrawStyle.filledAndStroked(fill, 1.5f, stroke);
        }

        try {
            VisibilityConfigurable gizmo = GizmoDrawing.box(box, style);
            if (ignoreOcclusion && gizmo != null) {
                gizmo.ignoreOcclusion();
            }
        } catch (Exception ignored) {}
    }

    public static void drawBox(Box box, SettingColor sideColor, SettingColor lineColor, ShapeMode shapeMode) {
        drawBox(box, sideColor, lineColor, shapeMode, true);
    }

    public static void drawBlock(BlockPos pos, SettingColor sideColor, SettingColor lineColor, ShapeMode shapeMode, boolean ignoreOcclusion) {
        if (pos == null) return;
        Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        drawBox(box, sideColor, lineColor, shapeMode, ignoreOcclusion);
    }

    public static void drawBlock(BlockPos pos, SettingColor sideColor, SettingColor lineColor, ShapeMode shapeMode) {
        drawBlock(pos, sideColor, lineColor, shapeMode, true);
    }

    public static void drawLine(Vec3d start, Vec3d end, SettingColor color, float width, boolean ignoreOcclusion) {
        if (start == null || end == null) return;
        int packed = color != null ? color.getPacked() : -1;
        try {
            VisibilityConfigurable gizmo = GizmoDrawing.line(start, end, packed, width);
            if (ignoreOcclusion && gizmo != null) {
                gizmo.ignoreOcclusion();
            }
        } catch (Exception ignored) {}
    }

    public static void drawLine(Vec3d start, Vec3d end, SettingColor color, float width) {
        drawLine(start, end, color, width, true);
    }
}
