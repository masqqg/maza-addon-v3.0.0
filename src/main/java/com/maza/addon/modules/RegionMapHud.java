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

    /** The world border of the main world reaches 225000 blocks each way. */
    private static final double WORLD_RADIUS = 225000;
    private static final double PADDING = 4;

    private RegionMapModule regionMap;

    // Where the map itself starts, set every frame by render() for the marker pass.
    private double mapX;
    private double mapY;

    public static boolean renderingFromModule = false;

    public RegionMapHud() {
        super(INFO);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void render(HudRenderer renderer) {
        // Meteor's own HUD pass is skipped outside the editor, the module draws it instead
        // so the map does not need the HUD to be switched on.
        if (!renderingFromModule && !isInEditor()) return;

        if (regionMap == null) regionMap = Modules.get().get(RegionMapModule.class);
        if (regionMap == null) return;

        boolean ocean = regionMap.oceanStyle.get();
        boolean title = ocean && regionMap.showTitle.get();
        boolean key = regionMap.showKey.get();

        double cell = regionMap.cellSize.get();
        double mapSize = GRID * cell;
        double lineHeight = renderer.textHeight();

        double frame = ocean ? PADDING : 0;
        double titleHeight = title ? lineHeight + 3 : 0;
        double keyHeight = key ? REGIONS.size() * (lineHeight + 3) - 3 : 0;
        double keyGap = key ? PADDING : 0;

        double width = mapSize + frame * 2;
        double height = frame + titleHeight + mapSize + keyGap + keyHeight + frame;
        setSize(width, height); // the whole element, so it can be grabbed and moved properly

        mapX = x + frame;
        mapY = y + frame + titleHeight;

        // Hidden unless the module is on. In the HUD editor a faint placeholder is drawn
        // instead so the element can still be moved around.
        if (!regionMap.isActive()) {
            if (isInEditor()) renderer.quad(x, y, width, height, new Color(255, 255, 255, 30));
            return;
        }

        if (ocean) drawOceanPanel(renderer, width, height);

        // Title
        if (title) {
            renderer.text("DonutSMP Regions", mapX, y + frame, regionMap.frameColor.get(), regionMap.textShadow.get());
        }

        // Cells. Colours are looked up once, not once per box.
        Map<String, Color> colors = new HashMap<>();
        for (String region : REGIONS.keySet()) {
            Color base = regionMap.regionColors.get(region).get();
            // A little see-through in ocean mode, so the water stripes show under the land.
            colors.put(region, ocean ? new Color(base.r, base.g, base.b, 228) : base);
        }

        for (int r = 0; r < GRID; r++) {
            for (int c = 0; c < GRID; c++) {
                String region = CELLS[r][c];
                if (region == null) continue;
                renderer.quad(mapX + c * cell, mapY + r * cell, cell, cell, colors.get(region));
            }
        }

        // Borders. Shard borders first so region borders draw on top of them.
        boolean shardBorders = regionMap.shardBorders.get();

        if (shardBorders) drawBorders(renderer, cell, false, regionMap.shardBorderColor.get());
        drawBorders(renderer, cell, true, new SettingColor(0, 0, 0, 200));

        if (ocean) {
            drawWaveOverlay(renderer, mapSize);
        } else if (shardBorders) {
            Color bc = new SettingColor(0, 0, 0, 200);
            renderer.quad(mapX, mapY, mapSize, 1, bc);
            renderer.quad(mapX, mapY + mapSize - 1, mapSize, 1, bc);
            renderer.quad(mapX, mapY, 1, mapSize, bc);
            renderer.quad(mapX + mapSize - 1, mapY, 1, mapSize, bc);
        }

        // Shard numbers, centred and shrunk to fit each shard
        if (regionMap.showNumbers.get()) {
            boolean shadow = regionMap.textShadow.get();
            double scale = regionMap.numberScale.get();

            for (Shard shard : SHARDS) {
                String text = Integer.toString(shard.number());

                double sw = shard.width() * cell;
                double sh = shard.height() * cell;

                double tw = renderer.textWidth(text, shadow, scale);
                double th = renderer.textHeight(shadow, scale);

                renderer.text(text,
                    mapX + shard.col() * cell + (sw - tw) / 2,
                    mapY + shard.row() * cell + (sh - th) / 2,
                    regionMap.numberColor.get(), shadow, scale);
            }
        }

        // Key
        if (key) {
            double kx = mapX;
            double ky = mapY + mapSize + keyGap;

            for (String region : REGIONS.keySet()) {
                if (ocean) renderer.quad(kx - 1, ky - 1, lineHeight + 2, lineHeight + 2, regionMap.frameColor.get());
                renderer.quad(kx, ky, lineHeight, lineHeight, regionMap.regionColors.get(region).get());
                renderer.text(region, kx + lineHeight + 5, ky, regionMap.textColor.get(), regionMap.textShadow.get());
                ky += lineHeight + 3;
            }
        }
    }

    /**
     * Ocean backdrop: deep blue base, lighter horizontal wave stripes that fade with depth,
     * a bright frame and small corner accents.
     */
    private void drawOceanPanel(HudRenderer renderer, double width, double height) {
        renderer.quad(x, y, width, height, regionMap.oceanColor.get());

        double stripe = regionMap.stripeSize.get();
        SettingColor wave = regionMap.waveColor.get();
        int index = 0;

        for (double yy = 0; yy < height; yy += stripe, index++) {
            if ((index & 1) != 0) continue;

            // Surface water is lighter, the bottom of the panel is darker.
            double depth = 1.0 - (yy / height) * 0.65;
            Color band = new Color(wave.r, wave.g, wave.b, (int) Math.max(8, wave.a * depth));
            renderer.quad(x, y + yy, width, Math.min(stripe, height - yy), band);
        }

        SettingColor frame = regionMap.frameColor.get();
        renderer.quad(x, y, width, 1, frame);
        renderer.quad(x, y + height - 1, width, 1, frame);
        renderer.quad(x, y, 1, height, frame);
        renderer.quad(x + width - 1, y, 1, height, frame);

        // Corner accents
        double a = 6;
        renderer.quad(x, y, a, 2, frame);
        renderer.quad(x, y, 2, a, frame);
        renderer.quad(x + width - a, y + height - 2, a, 2, frame);
        renderer.quad(x + width - 2, y + height - a, 2, a, frame);
    }

    /** Blue tint and fine stripes over the map, so land and sea read as one water-coloured picture. */
    private void drawWaveOverlay(HudRenderer renderer, double mapSize) {
        renderer.quad(mapX, mapY, mapSize, mapSize, regionMap.tintColor.get());

        double stripe = regionMap.stripeSize.get();
        Color line = new Color(255, 255, 255, 22);
        int index = 0;

        for (double yy = 0; yy < mapSize; yy += stripe, index++) {
            if ((index & 1) != 0) continue;
            renderer.quad(mapX, mapY + yy, mapSize, Math.min(stripe, mapSize - yy), line);
        }

        Color frame = regionMap.frameColor.get();
        renderer.quad(mapX, mapY, mapSize, 1, frame);
        renderer.quad(mapX, mapY + mapSize - 1, mapSize, 1, frame);
        renderer.quad(mapX, mapY, 1, mapSize, frame);
        renderer.quad(mapX + mapSize - 1, mapY, 1, mapSize, frame);
    }

    /**
     * Draws a line on every box edge where the two sides differ.
     * regionLevel = true compares regions, false compares shards within the same region.
     */
    private void drawBorders(HudRenderer renderer, double cell, boolean regionLevel, Color color) {
        for (int r = 0; r < GRID; r++) {
            for (int c = 0; c < GRID; c++) {
                if (c < GRID - 1 && differs(r, c, r, c + 1, regionLevel)) {
                    renderer.quad(mapX + (c + 1) * cell - 0.5, mapY + r * cell, 1, cell, color);
                }
                if (r < GRID - 1 && differs(r, c, r + 1, c, regionLevel)) {
                    renderer.quad(mapX + c * cell, mapY + (r + 1) * cell - 0.5, cell, 1, color);
                }
            }
        }
    }

    private static boolean differs(int r1, int c1, int r2, int c2, boolean regionLevel) {
        boolean sameRegion = Objects.equals(CELLS[r1][c1], CELLS[r2][c2]);
        if (regionLevel) return !sameRegion;
        return sameRegion && SHARD_IDS[r1][c1] != SHARD_IDS[r2][c2];
    }

    /** Your position and the way you look, drawn after the text so it stays on top. */
    public void renderMarker(HudRenderer renderer) {
        if (regionMap == null || !regionMap.showMarker.get()) return;
        if (mc.player == null || mc.world == null || !inMainWorld()) return;

        double mapSize = GRID * regionMap.cellSize.get();
        double fx = Math.max(0, Math.min(1, (mc.player.getX() + WORLD_RADIUS) / (WORLD_RADIUS * 2)));
        double fz = Math.max(0, Math.min(1, (mc.player.getZ() + WORLD_RADIUS) / (WORLD_RADIUS * 2)));

        double mx = mapX + fx * mapSize;
        double my = mapY + fz * mapSize;

        // Minecraft yaw: 0 = south (+Z), 90 = west (-X). Map has +X right, +Z down.
        double yaw = Math.toRadians(mc.player.getYaw());
        double dx = -Math.sin(yaw);
        double dy = Math.cos(yaw);

        double length = regionMap.markerLength.get();
        double t = regionMap.markerThickness.get();
        Color lineColor = regionMap.markerColor.get();

        for (double d = 0; d <= length; d += 0.5) {
            renderer.quad(mx + dx * d - t / 2, my + dy * d - t / 2, t, t, lineColor);
        }

        renderer.quad(mx - t / 2, my - t / 2, t, t, regionMap.pivotColor.get());
    }

    /**
     * The map shows the main world. DonutSMP names its worlds itself ("worlds:smp_overworld"),
     * so test for "not nether, not end" instead of comparing with the vanilla overworld key.
     */
    private static boolean inMainWorld() {
        if (mc.world.getRegistryKey() == World.OVERWORLD) return true;

        String id = mc.world.getRegistryKey().getValue().getPath();
        return !id.contains("nether") && !id.contains("end");
    }
}
