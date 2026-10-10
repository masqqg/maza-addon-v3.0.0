package com.maza.addon.hud;

import com.maza.addon.Addon;
import com.maza.addon.modules.RegionMapModule;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * DonutSMP shard region map as a HUD element.
 *
 * Map data and the numbering scheme come from the CC0 "Region Map" addon by Jeff
 * (Meteor addon template, CC0 1.0). The ocean look, the element size fix and the
 * dimension check are new.
 */
public class RegionMapHud extends HudElement {
    public static final HudElementInfo<RegionMapHud> INFO = new HudElementInfo<>(
        Addon.HUD_GROUP, "donut-region-map", "Renders DonutSMP's shard region map.", RegionMapHud::new
    );

    // ------------------------------------------------------------------
    // Map data
    // ------------------------------------------------------------------

    /** Width and height of the grid, in boxes. */
    public static final int GRID = 36;

    /**
     * Region name -> array of shards.
     * Each shard is one rectangle {col, row, width, height} in grid boxes (0,0 = top-left).
     * Shards never overlap, so the order of regions doesn't matter.
     */
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

    /** Region name per box, [row][col]. null = unassigned. */
    public static final String[][] CELLS = new String[GRID][GRID];

    /** Shard id per box, [row][col]. -1 = unassigned. */
    public static final int[][] SHARD_IDS = new int[GRID][GRID];

    public record Shard(String region, int number, int col, int row, int width, int height) {}

    /** Every shard, grouped by region, numbered in reading order within each region. */
    public static final List<Shard> SHARDS = new ArrayList<>();

    static {
        for (int[] row : SHARD_IDS) Arrays.fill(row, -1);

        for (Map.Entry<String, int[][]> entry : REGIONS.entrySet()) {
            String region = entry.getKey();

            // Number in reading order: top to bottom, then left to right
            int[][] rects = entry.getValue().clone();
            Arrays.sort(rects, Comparator.<int[]>comparingInt(r -> r[1]).thenComparingInt(r -> r[0]));

            int number = 1;
            for (int[] rect : rects) {
                int col = rect[0], row = rect[1], w = rect[2], h = rect[3];

                if (col < 0 || row < 0 || col + w > GRID || row + h > GRID) {
                    throw new IllegalStateException("Shard out of bounds in " + region + ": "
                        + col + "," + row + " " + w + "x" + h);
                }

                int id = SHARDS.size();
                SHARDS.add(new Shard(region, number++, col, row, w, h));

                for (int r = row; r < row + h; r++) {
                    for (int c = col; c < col + w; c++) {
                        if (CELLS[r][c] != null) {
                            throw new IllegalStateException("Shards overlap at " + c + "," + r + " ("
                                + CELLS[r][c] + " / " + region + ")");
                        }
                        CELLS[r][c] = region;
                        SHARD_IDS[r][c] = id;
                    }
                }
            }
        }
    }

    /** Region at a grid box, or null if unassigned / out of range. */
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

    /** World-to-map scale used by the original DonutSMP map. */
    private static final double WORLD_RADIUS = 225000;
    private static final double BLOCKS_PER_CELL = (WORLD_RADIUS * 2.0) / GRID;
    private static final double PADDING = 5;

    private RegionMapModule regionMap;
    private double mapX, mapY;
    private int viewStartCol, viewStartRow, viewCells;
    public static boolean renderingFromModule = false;

    public RegionMapHud() { super(INFO); }

    @Override
    public void render(HudRenderer renderer) {
        if (!renderingFromModule && !isInEditor()) return;
        if (regionMap == null) regionMap = Modules.get().get(RegionMapModule.class);
        if (regionMap == null) return;

        boolean playerAvailable = mc.player != null && mc.world != null && inMainWorld();
        viewCells = regionMap.visibleRadius.get() * 2 + 1;
        double cell = regionMap.cellSize.get();
        double mapSize = viewCells * cell;
        double lineHeight = renderer.textHeight();
        double headerHeight = lineHeight + 5;
        double footerHeight = lineHeight * 2 + 8;
        double width = mapSize + PADDING * 2;
        double height = PADDING * 2 + headerHeight + mapSize + footerHeight;
        setSize(width, height);

        mapX = x + PADDING;
        mapY = y + PADDING + headerHeight;

        if (!regionMap.isActive()) {
            if (isInEditor()) renderer.quad(x, y, width, height, new Color(15, 16, 20, 180));
            return;
        }

        drawDarkPanel(renderer, width, height);
        renderer.text("REGION MAP", mapX, y + PADDING, regionMap.textColor.get(), true);

        if (!playerAvailable) {
            renderer.text("Waiting for overworld...", mapX, mapY + mapSize + 5,
                regionMap.textColor.get(), true);
            return;
        }

        int playerCol = worldToCell(mc.player.getX());
        int playerRow = worldToCell(mc.player.getZ());
        viewStartCol = playerCol - regionMap.visibleRadius.get();
        viewStartRow = playerRow - regionMap.visibleRadius.get();

        // Draw only the cells in the player's local view window.
        for (int vr = 0; vr < viewCells; vr++) {
            for (int vc = 0; vc < viewCells; vc++) {
                int col = viewStartCol + vc;
                int row = viewStartRow + vr;
                double px = mapX + vc * cell;
                double py = mapY + vr * cell;
                String region = getRegion(col, row);
                if (region == null) {
                    renderer.quad(px, py, cell, cell, new Color(20, 22, 27, 235));
                } else {
                    SettingColor base = regionMap.regionColors.get(region).get();
                    renderer.quad(px, py, cell, cell, new Color(base.r, base.g, base.b, 220));
                }
                renderer.quad(px, py, cell, 0.5, new Color(0, 0, 0, 150));
                renderer.quad(px, py, 0.5, cell, new Color(0, 0, 0, 150));
            }
        }

        // Outline visible shard boundaries and label shards that have enough room.
        if (regionMap.shardBorders.get()) {
            for (int r = 0; r < GRID; r++) {
                for (int c = 0; c < GRID; c++) {
                    if (c < GRID - 1 && differs(r, c, r, c + 1, false)) {
                        drawLocalVertical(renderer, c + 1, r, cell);
                    }
                    if (r < GRID - 1 && differs(r, c, r + 1, c, false)) {
                        drawLocalHorizontal(renderer, c, r + 1, cell);
                    }
                }
            }
        }
        // Region boundaries are slightly brighter than shard boundaries.
        for (int r = 0; r < GRID; r++) {
            for (int c = 0; c < GRID; c++) {
                if (c < GRID - 1 && differs(r, c, r, c + 1, true)) drawLocalVerticalBright(renderer, c + 1, r, cell);
                if (r < GRID - 1 && differs(r, c, r + 1, c, true)) drawLocalHorizontalBright(renderer, c, r + 1, cell);
            }
        }

        if (regionMap.showNumbers.get()) {
            for (Shard shard : SHARDS) {
                int left = Math.max(shard.col(), viewStartCol);
                int top = Math.max(shard.row(), viewStartRow);
                int right = Math.min(shard.col() + shard.width(), viewStartCol + viewCells);
                int bottom = Math.min(shard.row() + shard.height(), viewStartRow + viewCells);
                if (left >= right || top >= bottom) continue;
                // Avoid clutter: only label when the visible part is at least 2x2 cells.
                if (right - left < 2 || bottom - top < 2) continue;
                String label = Integer.toString(shard.number());
                double scale = regionMap.numberScale.get();
                double tw = renderer.textWidth(label, true, scale);
                double th = renderer.textHeight(true, scale);
                double px = mapX + (left - viewStartCol) * cell;
                double py = mapY + (top - viewStartRow) * cell;
                double pw = (right - left) * cell;
                double ph = (bottom - top) * cell;
                renderer.text(label, px + (pw - tw) / 2, py + (ph - th) / 2,
                    regionMap.numberColor.get(), true, scale);
            }
        }

        // Player marker and direction line.
        if (regionMap.showMarker.get()) renderMarker(renderer);

        String region = getRegion(playerCol, playerRow);
        int shardId = (playerCol >= 0 && playerRow >= 0 && playerCol < GRID && playerRow < GRID)
            ? SHARD_IDS[playerRow][playerCol] : -1;
        String shardText = shardId >= 0 ? Integer.toString(SHARDS.get(shardId).number()) : "--";
        double infoY = mapY + mapSize + 5;
        renderer.text("REGION  " + (region == null ? "Unknown" : region) + "  /  SHARD " + shardText,
            mapX, infoY, regionMap.textColor.get(), true);
        renderer.text(String.format(java.util.Locale.ROOT, "X %.0f   Y %.0f   Z %.0f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ()),
            mapX, infoY + lineHeight + 2, regionMap.textColor.get(), true);
    }

    private void drawDarkPanel(HudRenderer renderer, double width, double height) {
        renderer.quad(x, y, width, height, new Color(10, 11, 14, 225));
        Color edge = new Color(90, 94, 105, 230);
        renderer.quad(x, y, width, 1, edge);
        renderer.quad(x, y + height - 1, width, 1, edge);
        renderer.quad(x, y, 1, height, edge);
        renderer.quad(x + width - 1, y, 1, height, edge);
    }

    private void drawLocalVertical(HudRenderer renderer, int globalX, int globalY, double cell) {
        if (globalX <= viewStartCol || globalX >= viewStartCol + viewCells ||
            globalY < viewStartRow || globalY >= viewStartRow + viewCells) return;
        renderer.quad(mapX + (globalX - viewStartCol) * cell - 0.5,
            mapY + (globalY - viewStartRow) * cell, 1, cell, new Color(0, 0, 0, 190));
    }
    private void drawLocalHorizontal(HudRenderer renderer, int globalX, int globalY, double cell) {
        if (globalY <= viewStartRow || globalY >= viewStartRow + viewCells ||
            globalX < viewStartCol || globalX >= viewStartCol + viewCells) return;
        renderer.quad(mapX + (globalX - viewStartCol) * cell,
            mapY + (globalY - viewStartRow) * cell - 0.5, cell, 1, new Color(0, 0, 0, 190));
    }
    private void drawLocalVerticalBright(HudRenderer renderer, int globalX, int globalY, double cell) {
        if (globalX <= viewStartCol || globalX >= viewStartCol + viewCells ||
            globalY < viewStartRow || globalY >= viewStartRow + viewCells) return;
        renderer.quad(mapX + (globalX - viewStartCol) * cell - 0.5,
            mapY + (globalY - viewStartRow) * cell, 1, cell, new Color(235, 238, 245, 220));
    }
    private void drawLocalHorizontalBright(HudRenderer renderer, int globalX, int globalY, double cell) {
        if (globalY <= viewStartRow || globalY >= viewStartRow + viewCells ||
            globalX < viewStartCol || globalX >= viewStartCol + viewCells) return;
        renderer.quad(mapX + (globalX - viewStartCol) * cell,
            mapY + (globalY - viewStartRow) * cell - 0.5, cell, 1, new Color(235, 238, 245, 220));
    }

    public void renderMarker(HudRenderer renderer) {
        if (regionMap == null || !regionMap.showMarker.get()) return;
        if (mc.player == null || mc.world == null || !inMainWorld()) return;

        double cell = regionMap.cellSize.get();
        double fx = (mc.player.getX() + WORLD_RADIUS) / BLOCKS_PER_CELL - viewStartCol;
        double fz = (mc.player.getZ() + WORLD_RADIUS) / BLOCKS_PER_CELL - viewStartRow;
        double mx = mapX + fx * cell;
        double my = mapY + fz * cell;
        if (fx < 0 || fz < 0 || fx > viewCells || fz > viewCells) return;

        double yaw = Math.toRadians(mc.player.getYaw());
        double dx = -Math.sin(yaw), dy = Math.cos(yaw);
        double length = Math.min(regionMap.markerLength.get(), cell * 1.5);
        double t = regionMap.markerThickness.get();
        Color lineColor = regionMap.markerColor.get();
        for (double d = 0; d <= length; d += 0.5) {
            renderer.quad(mx + dx * d - t / 2, my + dy * d - t / 2, t, t, lineColor);
        }
        renderer.quad(mx - t, my - t, t * 2, t * 2, regionMap.pivotColor.get());
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
        if (mc.world.getRegistryKey() == World.OVERWORLD) return true;
        String id = mc.world.getRegistryKey().getValue().getPath();
        return !id.contains("nether") && !id.contains("end");
    }
}
