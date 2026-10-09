package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingColor;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.orbit.EventHandler;

/**
 * Maza Region Map HUD module. This is a standalone recreation for DonutSMP-style
 * region visualization; it does not depend on the separate Region Map JAR.
 */
public class RegionMapModule extends Module {
    private final Setting<Boolean> showGrid = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("show-grid").description("Show the region grid.").defaultValue(true).build());
    private final Setting<Boolean> showPlayer = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("show-player").description("Show your approximate region position.").defaultValue(true).build());
    private final Setting<Double> cellSize = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("cell-size").description("Size of each region cell.").defaultValue(9).min(5).max(16).sliderMin(5).sliderMax(16).build());
    private final Setting<SettingColor> borderColor = settings.getDefaultGroup().add(new ColorSetting.Builder()
        .name("border-color").defaultValue(new SettingColor(8, 12, 22, 235)).build());
    private final Setting<SettingColor> playerColor = settings.getDefaultGroup().add(new ColorSetting.Builder()
        .name("player-color").defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private static final Color[] COLORS = {
        new Color(44, 103, 160, 225), new Color(46, 135, 105, 225),
        new Color(130, 75, 165, 225), new Color(184, 112, 39, 225),
        new Color(160, 54, 66, 225), new Color(51, 122, 148, 225),
        new Color(118, 132, 48, 225), new Color(73, 82, 157, 225),
        new Color(157, 91, 116, 225)
    };

    public RegionMapModule() {
        super(MazaCategory.INSTANCE, "region-map", "Displays a colorful region grid and your approximate position.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;

        final int grid = 36;
        final double size = cellSize.get();
        final double left = 12;
        final double top = 34;

        if (showGrid.get()) {
            Renderer2D.COLOR.begin();
            for (int z = 0; z < grid; z++) {
                for (int x = 0; x < grid; x++) {
                    int palette = Math.floorMod((x / 4) + (z / 4) * 2 + (x + z) % 3, COLORS.length);
                    double x1 = left + x * size;
                    double y1 = top + z * size;
                    double x2 = x1 + size - 1;
                    double y2 = y1 + size - 1;
                    Renderer2D.COLOR.quad(x1, y1, x2, y2, COLORS[palette]);
                    Renderer2D.COLOR.quad(x1, y1, x2, y1 + 0.7, borderColor.get());
                    Renderer2D.COLOR.quad(x1, y2 - 0.7, x2, y2, borderColor.get());
                    Renderer2D.COLOR.quad(x1, y1, x1 + 0.7, y2, borderColor.get());
                    Renderer2D.COLOR.quad(x2 - 0.7, y1, x2, y2, borderColor.get());
                }
            }
            Renderer2D.COLOR.render(null);
        }

        if (showPlayer.get()) {
            // A repeating coordinate projection for the marker, not an official server-region lookup.
            int px = Math.floorMod((int) Math.floor(mc.player.getX() / 256.0), grid);
            int pz = Math.floorMod((int) Math.floor(mc.player.getZ() / 256.0), grid);
            double cx = left + px * size + size / 2;
            double cy = top + pz * size + size / 2;
            double r = Math.max(2, size * 0.30);
            Renderer2D.COLOR.begin();
            Renderer2D.COLOR.quad(cx - r, cy - 1, cx + r, cy + 1, playerColor.get());
            Renderer2D.COLOR.quad(cx - 1, cy - r, cx + 1, cy + r, playerColor.get());
            Renderer2D.COLOR.render(null);
        }
    }
}
