package com.maza.client.module.modules.render;

import com.maza.client.event.EventHandler;
import com.maza.client.event.Render2DEvent;
import com.maza.client.module.Category;
import com.maza.client.module.Module;
import com.maza.client.setting.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.World;

import java.util.*;

/**
 * Region Map Module - Standalone Fabric
 * Shows DonutSMP shard region map on HUD
 */
public class RegionMapModule extends Module {
    public static final int GRID = 36;
    public static final Map<String, int[][]> REGIONS = new LinkedHashMap<>();

    static {
        REGIONS.put("NA West", new int[][] {
            {4, 0, 2, 4}, {6, 0, 2, 4}, {8, 0, 2, 4}, {10, 0, 2, 4}, {12, 0, 2, 4},
            {14, 0, 2, 4}, {4, 4, 2, 4}, {6, 4, 2, 4}, {8, 4, 2, 4}, {10, 4, 2, 4},
            {12, 4, 2, 4}, {14, 4, 2, 4}, {4, 8, 2, 4}, {6, 8, 2, 4}, {8, 8, 2, 4},
            {10, 8, 2, 4}, {12, 8, 2, 4}, {14, 8, 2, 4}, {12, 12, 2, 4}, {14, 12, 2, 4},
        });
        REGIONS.put("NA East", new int[][] {
            {16, 0, 2, 4}, {18, 0, 2, 4}, {20, 0, 2, 4}, {22, 0, 2, 4}, {24, 0, 4, 4},
            {28, 0, 2, 4}, {30, 0, 2, 4}, {32, 0, 2, 4}, {34, 0, 2, 4}, {16, 4, 2, 2},
            {18, 4, 2, 2}, {20, 4, 2, 2}, {22, 4, 2, 2}, {24, 4, 2, 2}, {26, 4, 2, 4},
            {28, 4, 2, 2}, {30, 4, 2, 2}, {32, 4, 2, 4}, {34, 4, 2, 4}, {16, 6, 2, 2},
            {18, 6, 2, 2}, {20, 6, 2, 2}, {22, 6, 2, 2}, {24, 6, 2, 2}, {28, 6, 2, 2},
            {30, 6, 2, 2}, {16, 8, 2, 4}, {18, 8, 2, 4}, {20, 8, 4, 4}, {24, 8, 2, 4},
            {26, 8, 2, 4}, {28, 8, 2, 4}, {30, 8, 2, 4}, {32, 8, 2, 4}, {34, 8, 2, 4},
            {16, 12, 2, 4}, {18, 12, 2, 4}, {20, 12, 4, 4}, {24, 12, 2, 4}, {26, 12, 2, 4},
            {28, 12, 2, 4}, {30, 12, 2, 4}, {32, 12, 2, 4}, {34, 12, 2, 4}, {16, 16, 4, 4},
            {20, 16, 2, 4}, {22, 16, 2, 2}, {24, 16, 4, 4}, {28, 16, 2, 4}, {30, 16, 2, 4},
            {32, 16, 2, 4}, {34, 16, 2, 4}, {22, 18, 2, 2}, {32, 20, 2, 4}, {34, 20, 2, 4},
            {32, 24, 2, 4}, {34, 24, 2, 4},
        });
        REGIONS.put("Oceania", new int[][] {
            {0, 0, 2, 2}, {2, 0, 2, 4}, {0, 2, 2, 2}, {0, 4, 2, 2}, {2, 4, 2, 4},
            {0, 6, 2, 2}, {0, 8, 2, 2}, {2, 8, 2, 4}, {0, 10, 2, 2}, {0, 12, 2, 2},
            {2, 12, 2, 4}, {4, 12, 2, 4}, {6, 12, 2, 4}, {8, 12, 2, 4}, {10, 12, 2, 4},
            {0, 14, 2, 2},
        });
        REGIONS.put("Asia", new int[][] {
            {0, 16, 2, 4}, {2, 16, 2, 4}, {4, 16, 2, 4}, {6, 16, 2, 4}, {8, 16, 2, 2},
            {10, 16, 2, 2}, {12, 16, 2, 2}, {14, 16, 2, 2}, {8, 18, 2, 2}, {10, 18, 2, 2},
            {12, 18, 2, 2}, {14, 18, 2, 2}, {0, 20, 2, 4}, {2, 20, 2, 4}, {0, 24, 2, 4},
            {2, 24, 2, 4},
        });
        REGIONS.put("EU West", new int[][] {
            {4, 20, 2, 4}, {6, 20, 2, 4}, {8, 20, 2, 4}, {10, 20, 2, 4}, {4, 24, 2, 4},
            {6, 24, 2, 4}, {8, 24, 2, 4}, {10, 24, 2, 4}, {4, 28, 2, 4}, {6, 28, 2, 4},
            {4, 32, 2, 4}, {6, 32, 2, 2}, {8, 32, 2, 2}, {10, 32, 2, 2}, {12, 32, 2, 2},
            {14, 32, 2, 4}, {16, 32, 2, 4}, {18, 32, 2, 2}, {20, 32, 2, 4}, {22, 32, 2, 4},
            {24, 32, 2, 4}, {26, 32, 2, 4}, {6, 34, 2, 2}, {8, 34, 2, 2}, {10, 34, 2, 2},
            {12, 34, 2, 2}, {18, 34, 2, 2},
        });
        REGIONS.put("EU Central", new int[][] {
            {12, 20, 2, 4}, {14, 20, 2, 4}, {16, 20, 2, 4}, {18, 20, 2, 4}, {20, 20, 2, 4},
            {22, 20, 2, 2}, {26, 20, 2, 4}, {28, 20, 2, 4}, {30, 20, 2, 2}, {22, 22, 2, 2},
            {24, 22, 1, 2}, {25, 22, 1, 2}, {12, 24, 2, 4}, {14, 24, 2, 4}, {16, 24, 2, 4},
            {18, 24, 2, 4}, {20, 24, 2, 4}, {22, 24, 2, 4}, {24, 24, 2, 4}, {28, 24, 2, 2},
            {30, 24, 2, 4}, {26, 26, 2, 2}, {0, 28, 4, 1}, {8, 28, 2, 2}, {10, 28, 2, 4},
            {12, 28, 2, 2}, {14, 28, 2, 2}, {16, 28, 2, 2}, {18, 28, 2, 2}, {20, 28, 2, 2},
            {22, 28, 2, 2}, {24, 28, 2, 2}, {26, 28, 2, 4}, {28, 28, 2, 2}, {30, 28, 2, 2},
            {32, 28, 2, 2}, {0, 29, 4, 1}, {0, 30, 2, 2}, {2, 30, 2, 2}, {16, 30, 2, 2},
            {18, 30, 2, 2}, {22, 30, 2, 2}, {24, 30, 2, 2}, {32, 30, 2, 2}, {34, 30, 2, 2},
            {0, 32, 2, 2}, {28, 32, 2, 4}, {32, 32, 2, 4}, {0, 34, 4, 2}, {30, 34, 2, 2},
            {34, 34, 2, 2}, {26, 24, 2, 2},
        });
        REGIONS.put("Europe", new int[][] {
            {24, 20, 2, 2}, {30, 22, 2, 2}, {28, 26, 2, 2}, {34, 28, 2, 2}, {8, 30, 2, 2},
            {12, 30, 2, 2}, {14, 30, 2, 2}, {20, 30, 2, 2}, {28, 30, 2, 2}, {30, 30, 2, 2},
            {2, 32, 2, 2}, {30, 32, 2, 2}, {34, 32, 2, 2},
        });
    }

    public static final String[][] CELLS = new String[GRID][GRID];
    public static final int[][] SHARD_IDS = new int[GRID][GRID];

    public record Shard(String region, int number, int col, int row, int width, int height) {}

    public static final List<Shard> SHARDS = new ArrayList<>();

    static {
        for (int[] row : SHARD_IDS) Arrays.fill(row, -1);

        for (Map.Entry<String, int[][]> entry : REGIONS.entrySet()) {
            String region = entry.getKey();
            int[][] rects = entry.getValue().clone();
            Arrays.sort(rects, Comparator.<int[]>comparingInt(r -> r[1]).thenComparingInt(r -> r[0]));

            int number = 1;
            for (int[] rect : rects) {
                int col = rect[0], row = rect[1], w = rect[2], h = rect[3];
                int id = SHARDS.size();
                SHARDS.add(new Shard(region, number++, col, row, w, h));

                for (int r = row; r < row + h; r++) {
                    for (int c = col; c < col + w; c++) {
                        CELLS[r][c] = region;
                        SHARD_IDS[r][c] = id;
                    }
                }
            }
        }
    }

    public static String getRegion(int col, int row) {
        if (col < 0 || row < 0 || col >= GRID || row >= GRID) return null;
        return CELLS[row][col];
    }

    public static final Map<String, SettingColor> DEFAULT_COLORS = Map.of(
        "Europe",     new SettingColor(218, 112, 214),
        "EU Central", new SettingColor(146, 208, 80),
        "EU West",    new SettingColor(56, 118, 29),
        "Asia",       new SettingColor(255, 192, 0),
        "Oceania",    new SettingColor(237, 125, 49),
        "NA West",    new SettingColor(68, 114, 196),
        "NA East",    new SettingColor(91, 155, 213)
    );

    private static final double WORLD_RADIUS = 225000;
    private static final double BLOCKS_PER_CELL = (WORLD_RADIUS * 2.0) / GRID;
    private static final double PADDING = 5;

    // Settings
    public final IntSetting xPos = addSetting(new IntSetting.Builder()
        .name("x").defaultValue(4).min(0).max(1000).build());

    public final IntSetting yPos = addSetting(new IntSetting.Builder()
        .name("y").defaultValue(4).min(0).max(1000).build());

    public final IntSetting visibleRadius = addSetting(new IntSetting.Builder()
        .name("visible-radius").defaultValue(4).min(2).max(8).sliderMin(2).sliderMax(8).build());

    public final DoubleSetting cellSize = addSetting(new DoubleSetting.Builder()
        .name("cell-size").defaultValue(4.0).min(1.0).max(12.0).sliderMin(1.0).sliderMax(12.0).build());

    public final BoolSetting showKey = addSetting(new BoolSetting.Builder()
        .name("show-key").defaultValue(true).build());

    public final ColorSetting textColor = addSetting(new ColorSetting.Builder()
        .name("text-color").defaultValue(new SettingColor(220, 245, 255)).build());

    public final BoolSetting textShadow = addSetting(new BoolSetting.Builder()
        .name("text-shadow").defaultValue(true).build());

    public final BoolSetting shardBorders = addSetting(new BoolSetting.Builder()
        .name("goliath-borders").defaultValue(true).build());

    public final ColorSetting shardBorderColor = addSetting(new ColorSetting.Builder()
        .name("goliath-border-color").defaultValue(new SettingColor(0, 0, 0, 90)).build());

    public final BoolSetting showNumbers = addSetting(new BoolSetting.Builder()
        .name("show-numbers").defaultValue(true).build());

    public final ColorSetting numberColor = addSetting(new ColorSetting.Builder()
        .name("number-color").defaultValue(new SettingColor(255, 255, 255)).build());

    public final BoolSetting showMarker = addSetting(new BoolSetting.Builder()
        .name("show-marker").defaultValue(true).build());

    public final ColorSetting markerColor = addSetting(new ColorSetting.Builder()
        .name("marker-color").defaultValue(new SettingColor(170, 0, 0)).build());

    public final ColorSetting pivotColor = addSetting(new ColorSetting.Builder()
        .name("pivot-color").defaultValue(new SettingColor(255, 70, 70)).build());

    public final DoubleSetting markerLength = addSetting(new DoubleSetting.Builder()
        .name("marker-length").defaultValue(8.0).min(1.0).max(30.0).sliderMin(2.0).sliderMax(30.0).build());

    public final Map<String, ColorSetting> regionColors = new HashMap<>();

    public RegionMapModule() {
        super("RegionMap", "Shows the DonutSMP shard region map on screen", Category.RENDER);

        for (String region : REGIONS.keySet()) {
            regionColors.put(region, addSetting(new ColorSetting.Builder()
                .name(region.toLowerCase().replace(' ', '-') + "-color")
                .defaultValue(DEFAULT_COLORS.getOrDefault(region, new SettingColor(255, 255, 255)))
                .build()
            ));
        }
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (mc.options.hudHidden) return;
        DrawContext context = event.context != null ? event.context : event.drawContext;
        if (context == null) return;

        boolean playerAvailable = mc.player != null && mc.world != null && inMainWorld();
        int viewCells = visibleRadius.get() * 2 + 1;
        double cell = cellSize.get();
        double mapSize = viewCells * cell;
        double lineHeight = mc.textRenderer.fontHeight;
        double headerHeight = lineHeight + 5;
        double footerHeight = lineHeight * 2 + 8;
        double width = mapSize + PADDING * 2;
        double height = PADDING * 2 + headerHeight + mapSize + footerHeight;

        double x = xPos.get();
        double y = yPos.get();
        double mapX = x + PADDING;
        double mapY = y + PADDING + headerHeight;

        // Draw dark panel
        context.fill((int)x, (int)y, (int)(x + width), (int)(y + height), 0xE10A0B0E);
        int edge = 0xE65A5E69;
        context.fill((int)x, (int)y, (int)(x + width), (int)y + 1, edge);
        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height), edge);
        context.fill((int)x, (int)y, (int)x + 1, (int)(y + height), edge);
        context.fill((int)(x + width - 1), (int)y, (int)(x + width), (int)(y + height), edge);

        context.drawText(mc.textRenderer, "REGION MAP", (int)mapX, (int)(y + PADDING), textColor.get().getPacked(), textShadow.get());

        if (!playerAvailable) {
            context.drawText(mc.textRenderer, "Waiting for overworld...", (int)mapX, (int)(mapY + mapSize + 5), textColor.get().getPacked(), textShadow.get());
            return;
        }

        int playerCol = worldToCell(mc.player.getX());
        int playerRow = worldToCell(mc.player.getZ());
        int viewStartCol = playerCol - visibleRadius.get();
        int viewStartRow = playerRow - visibleRadius.get();

        for (int vr = 0; vr < viewCells; vr++) {
            for (int vc = 0; vc < viewCells; vc++) {
                int col = viewStartCol + vc;
                int row = viewStartRow + vr;
                int px = (int)(mapX + vc * cell);
                int py = (int)(mapY + vr * cell);
                int pw = (int)(mapX + (vc + 1) * cell) - px;
                int ph = (int)(mapY + (vr + 1) * cell) - py;

                String region = getRegion(col, row);
                if (region == null) {
                    context.fill(px, py, px + pw, py + ph, 0xEB14161B);
                } else {
                    SettingColor base = regionColors.containsKey(region) ? regionColors.get(region).get()
                        : DEFAULT_COLORS.getOrDefault(region, new SettingColor(255, 255, 255));
                    int packed = (220 << 24) | ((base.r & 0xFF) << 16) | ((base.g & 0xFF) << 8) | (base.b & 0xFF);
                    context.fill(px, py, px + pw, py + ph, packed);
                }

                context.fill(px, py, px + pw, py + 1, 0x96000000);
                context.fill(px, py, px + 1, py + ph, 0x96000000);
            }
        }

        // Shard and region borders
        if (shardBorders.get()) {
            int bColor = shardBorderColor.get().getPacked();
            for (int r = 0; r < GRID; r++) {
                for (int c = 0; c < GRID; c++) {
                    if (c < GRID - 1 && differs(r, c, r, c + 1, false)) {
                        drawBorderV(context, mapX, mapY, cell, viewStartCol, viewStartRow, viewCells, c + 1, r, bColor);
                    }
                    if (r < GRID - 1 && differs(r, c, r + 1, c, false)) {
                        drawBorderH(context, mapX, mapY, cell, viewStartCol, viewStartRow, viewCells, c, r + 1, bColor);
                    }
                }
            }
        }

        for (int r = 0; r < GRID; r++) {
            for (int c = 0; c < GRID; c++) {
                if (c < GRID - 1 && differs(r, c, r, c + 1, true)) {
                    drawBorderV(context, mapX, mapY, cell, viewStartCol, viewStartRow, viewCells, c + 1, r, 0xDCEDEBE8);
                }
                if (r < GRID - 1 && differs(r, c, r + 1, c, true)) {
                    drawBorderH(context, mapX, mapY, cell, viewStartCol, viewStartRow, viewCells, c, r + 1, 0xDCEDEBE8);
                }
            }
        }

        if (showNumbers.get()) {
            int nColor = numberColor.get().getPacked();
            for (Shard shard : SHARDS) {
                int left = Math.max(shard.col(), viewStartCol);
                int top = Math.max(shard.row(), viewStartRow);
                int right = Math.min(shard.col() + shard.width(), viewStartCol + viewCells);
                int bottom = Math.min(shard.row() + shard.height(), viewStartRow + viewCells);
                if (left >= right || top >= bottom) continue;
                if (right - left < 2 || bottom - top < 2) continue;

                String label = Integer.toString(shard.number());
                int px = (int)(mapX + (left - viewStartCol) * cell);
                int py = (int)(mapY + (top - viewStartRow) * cell);
                int pw = (int)((right - left) * cell);
                int ph = (int)((bottom - top) * cell);
                int tw = mc.textRenderer.getWidth(label);
                int th = mc.textRenderer.fontHeight;
                context.drawText(mc.textRenderer, label, px + (pw - tw) / 2, py + (ph - th) / 2, nColor, true);
            }
        }

        if (showMarker.get()) {
            double fx = (mc.player.getX() + WORLD_RADIUS) / BLOCKS_PER_CELL - viewStartCol;
            double fz = (mc.player.getZ() + WORLD_RADIUS) / BLOCKS_PER_CELL - viewStartRow;
            double mx = mapX + fx * cell;
            double my = mapY + fz * cell;
            if (fx >= 0 && fz >= 0 && fx <= viewCells && fz <= viewCells) {
                double yaw = Math.toRadians(mc.player.getYaw());
                double dx = -Math.sin(yaw), dy = Math.cos(yaw);
                double length = Math.min(markerLength.get(), cell * 1.5);
                int mColor = markerColor.get().getPacked();
                int pColor = pivotColor.get().getPacked();
                for (double d = 0; d <= length; d += 0.5) {
                    context.fill((int)(mx + dx * d - 1), (int)(my + dy * d - 1), (int)(mx + dx * d + 1), (int)(my + dy * d + 1), mColor);
                }
                context.fill((int)(mx - 2), (int)(my - 2), (int)(mx + 2), (int)(my + 2), pColor);
            }
        }

        String region = getRegion(playerCol, playerRow);
        int shardId = (playerCol >= 0 && playerRow >= 0 && playerCol < GRID && playerRow < GRID)
            ? SHARD_IDS[playerRow][playerCol] : -1;
        String shardText = shardId >= 0 ? Integer.toString(SHARDS.get(shardId).number()) : "--";
        double infoY = mapY + mapSize + 5;
        context.drawText(mc.textRenderer, "REGION  " + (region == null ? "Unknown" : region) + "  /  SHARD " + shardText,
            (int)mapX, (int)infoY, textColor.get().getPacked(), textShadow.get());
        context.drawText(mc.textRenderer, String.format(Locale.ROOT, "X %.0f  Y %.0f  Z %.0f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ()),
            (int)mapX, (int)(infoY + lineHeight + 2), textColor.get().getPacked(), textShadow.get());
    }

    private static void drawBorderV(DrawContext context, double mapX, double mapY, double cell, int startCol, int startRow, int cells, int gx, int gy, int color) {
        if (gx <= startCol || gx >= startCol + cells || gy < startRow || gy >= startRow + cells) return;
        int px = (int)(mapX + (gx - startCol) * cell);
        int py = (int)(mapY + (gy - startRow) * cell);
        context.fill(px - 1, py, px + 1, (int)(py + cell), color);
    }

    private static void drawBorderH(DrawContext context, double mapX, double mapY, double cell, int startCol, int startRow, int cells, int gx, int gy, int color) {
        if (gy <= startRow || gy >= startRow + cells || gx < startCol || gx >= startCol + cells) return;
        int px = (int)(mapX + (gx - startCol) * cell);
        int py = (int)(mapY + (gy - startRow) * cell);
        context.fill(px, py - 1, (int)(px + cell), py + 1, color);
    }

    private static int worldToCell(double coordinate) {
        int value = (int) Math.floor((coordinate + WORLD_RADIUS) / BLOCKS_PER_CELL);
        return Math.max(0, Math.min(GRID - 1, value));
    }

    private static boolean differs(int r1, int c1, int r2, int c2, boolean regionLevel) {
        boolean sameRegion = Objects.equals(CELLS[r1][c1], CELLS[r2][c2]);
        if (regionLevel) return !sameRegion;
        return sameRegion && SHARD_IDS[r1][c1] != SHARD_IDS[r2][c2];
    }

    private static boolean inMainWorld() {
        if (net.minecraft.client.MinecraftClient.getInstance().world == null) return false;
        if (net.minecraft.client.MinecraftClient.getInstance().world.getRegistryKey() == World.OVERWORLD) return true;
        String id = net.minecraft.client.MinecraftClient.getInstance().world.getRegistryKey().getValue().getPath();
        return !id.contains("nether") && !id.contains("end");
    }
}
