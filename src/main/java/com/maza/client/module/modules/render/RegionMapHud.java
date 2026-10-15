package com.maza.client.module.modules.render;

import com.maza.client.event.EventHandler;
import com.maza.client.event.Render2DEvent;
import com.maza.client.module.Category;
import com.maza.client.module.Module;
import com.maza.client.module.ModuleManager;
import com.maza.client.setting.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Region Map HUD - Standalone Fabric
 * Renders DonutSMP's shard region map overlay on the HUD.
 */
public class RegionMapHud extends Module {
    public static final int GRID = RegionMapModule.GRID;
    public static final Map<String, int[][]> REGIONS = RegionMapModule.REGIONS;
    public static final String[][] CELLS = RegionMapModule.CELLS;
    public static final int[][] SHARD_IDS = RegionMapModule.SHARD_IDS;
    public static final List<RegionMapModule.Shard> SHARDS = RegionMapModule.SHARDS;
    public static final Map<String, SettingColor> DEFAULT_COLORS = RegionMapModule.DEFAULT_COLORS;

    public static String getRegion(int col, int row) {
        return RegionMapModule.getRegion(col, row);
    }

    private static final double WORLD_RADIUS = 225000;
    private static final double BLOCKS_PER_CELL = (WORLD_RADIUS * 2.0) / GRID;

    // Settings
    public final BoolSetting compact = addSetting(new BoolSetting.Builder()
        .name("compact-bar")
        .description("Show compact shard status bar instead of map")
        .defaultValue(false).build());

    public final IntSetting xPos = addSetting(new IntSetting.Builder()
        .name("x").defaultValue(4).min(0).max(1000).build());

    public final IntSetting yPos = addSetting(new IntSetting.Builder()
        .name("y").defaultValue(40).min(0).max(1000).build());

    public final BoolSetting oceanStyle = addSetting(new BoolSetting.Builder()
        .name("ocean-style").defaultValue(true).build());

    public final ColorSetting textColor = addSetting(new ColorSetting.Builder()
        .name("text-color").defaultValue(new SettingColor(220, 245, 255)).build());

    public final ColorSetting oceanColor = addSetting(new ColorSetting.Builder()
        .name("ocean-color").defaultValue(new SettingColor(6, 30, 62, 225)).build());

    public final ColorSetting frameColor = addSetting(new ColorSetting.Builder()
        .name("frame-color").defaultValue(new SettingColor(90, 210, 255, 235)).build());

    public RegionMapHud() {
        super("RegionMapHud", "Renders DonutSMP shard region map on HUD", Category.RENDER);
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (mc.options.hudHidden) return;
        DrawContext context = event.context != null ? event.context : event.drawContext;
        if (context == null || mc.player == null) return;

        int playerCol = worldToCell(mc.player.getX());
        int playerRow = worldToCell(mc.player.getZ());
        String region = getRegion(playerCol, playerRow);
        int shardId = (playerCol >= 0 && playerRow >= 0 && playerCol < GRID && playerRow < GRID)
            ? SHARD_IDS[playerRow][playerCol] : -1;
        String shardText = shardId >= 0 ? Integer.toString(SHARDS.get(shardId).number()) : "--";

        int x = xPos.get();
        int y = yPos.get();

        if (compact.get()) {
            // Draw compact status box
            int tColor = textColor.get().getPacked();
            String line1 = "§bDonutSMP Shard: §f#" + shardText;
            String line2 = "§7Region: §e" + (region == null ? "Unknown" : region);
            String line3 = String.format(Locale.ROOT, "§7XYZ: §f%.0f, %.0f, %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ());

            int w = Math.max(mc.textRenderer.getWidth(line1), Math.max(mc.textRenderer.getWidth(line2), mc.textRenderer.getWidth(line3))) + 12;
            int h = mc.textRenderer.fontHeight * 3 + 12;

            int bg = oceanStyle.get() ? oceanColor.get().getPacked() : 0xE10A0B0E;
            int frame = oceanStyle.get() ? frameColor.get().getPacked() : 0xE65A5E69;

            context.fill(x, y, x + w, y + h, bg);
            context.fill(x, y, x + w, y + 1, frame);
            context.fill(x, y + h - 1, x + w, y + h, frame);
            context.fill(x, y, x + 1, y + h, frame);
            context.fill(x + w - 1, y, x + w, y + h, frame);

            context.drawText(mc.textRenderer, line1, x + 6, y + 5, tColor, true);
            context.drawText(mc.textRenderer, line2, x + 6, y + 5 + mc.textRenderer.fontHeight + 2, tColor, true);
            context.drawText(mc.textRenderer, line3, x + 6, y + 5 + (mc.textRenderer.fontHeight + 2) * 2, tColor, true);
        } else {
            // If full RegionMapModule is not already rendering or enabled, we can let RegionMapModule handle it
            RegionMapModule mod = ModuleManager.INSTANCE.get(RegionMapModule.class);
            if (mod != null && !mod.isEnabled()) {
                mod.onRender2D(event);
            }
        }
    }

    private static int worldToCell(double coordinate) {
        int value = (int) Math.floor((coordinate + WORLD_RADIUS) / BLOCKS_PER_CELL);
        return Math.max(0, Math.min(GRID - 1, value));
    }
}
