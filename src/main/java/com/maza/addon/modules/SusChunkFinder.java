package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SusChunkFinder v2
 *
 * Flags chunks that show signs of long-term player presence, using fully grown
 * amethyst as the clock: a geode only finishes growing if its chunk stays
 * loaded for a long time, which normally means a player is nearby.
 *
 * Model (independent implementation of the heat/veto idea):
 *  - Every scanned chunk gets a heat value (grown evidence) and an "ungrown"
 *    flag (a bud still sitting on budding amethyst = still growing).
 *  - Heat spreads over spread-radius; ungrown chunks veto their surroundings
 *    over veto-radius, so a natural, unfinished geode never gets flagged.
 *  - Method "Light" reads block-light data instead of blocks, so it still works
 *    where block data is hidden (anti-xray). A fully grown cluster is a light
 *    source of exactly 5 whose neighbours never exceed 5.
 *  - Flagged chunks are drawn merged (outer border only). The hottest chunk of
 *    every patch gets a heat label.
 */
public class SusChunkFinder extends Module {
    public enum Method { Blocks, Light, Both }

    private static final int GLOW_LIGHT = 5;           // amethyst cluster emission
    private static final int LIGHT_DELAY_TICKS = 8;    // light arrives after chunk data
    private static final int MAX_LIGHT_RETRIES = 6;

    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup detection = settings.createGroup("Detection");
    private final SettingGroup performance = settings.createGroup("Performance");
    private final SettingGroup rendering = settings.createGroup("Render");

    // ---- general
    private final Setting<Method> method = general.add(new EnumSetting.Builder<Method>()
        .name("method")
        .description("Blocks reads block states, Light reads light data (works through anti-xray), Both uses the stronger result.")
        .defaultValue(Method.Both).build());

    private final Setting<Integer> scanRange = general.add(new IntSetting.Builder()
        .name("scan-range")
        .description("Chunk radius around you that is scanned.")
        .defaultValue(8).min(1).sliderMax(32).build());

    private final Setting<Integer> sensitivity = general.add(new IntSetting.Builder()
        .name("sensitivity")
        .description("Heat a chunk needs before it is flagged. Lower = more chunks.")
        .defaultValue(4).min(1).max(50).sliderMin(1).sliderMax(50).build());

    private final Setting<Integer> spreadRadius = general.add(new IntSetting.Builder()
        .name("spread-radius")
        .description("How far (in chunks) the heat of a chunk spreads.")
        .defaultValue(5).min(1).max(16).sliderMin(1).sliderMax(16).build());

    private final Setting<Integer> vetoRadius = general.add(new IntSetting.Builder()
        .name("veto-radius")
        .description("Chunks around a still-growing geode that are never flagged.")
        .defaultValue(2).min(0).max(8).sliderMin(0).sliderMax(8).build());

    private final Setting<Integer> minScanned = general.add(new IntSetting.Builder()
        .name("min-scanned-neighbours")
        .description("A chunk is only flagged once this many of its 8 neighbours were scanned.")
        .defaultValue(3).min(0).max(8).sliderMin(0).sliderMax(8).build());

    private final Setting<Boolean> notify = general.add(new BoolSetting.Builder()
        .name("notify")
        .description("Chat message when a new suspicious patch is found.")
        .defaultValue(true).build());

    private final Setting<Boolean> debug = general.add(new BoolSetting.Builder()
        .name("debug").defaultValue(false).build());

    // ---- detection
    private final Setting<Integer> cellsPerHeat = detection.add(new IntSetting.Builder()
        .name("light-cells-per-heat")
        .description("Light method: glowing cells needed for 1 heat.")
        .defaultValue(1).min(1).max(16).sliderMin(1).sliderMax(16).build());

    private final Setting<Integer> minY = detection.add(new IntSetting.Builder()
        .name("min-y").defaultValue(-64).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Integer> maxY = detection.add(new IntSetting.Builder()
        .name("max-y").defaultValue(70).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    // ---- performance
    private final Setting<Integer> scanBudgetMs = performance.add(new IntSetting.Builder()
        .name("scan-budget-ms")
        .description("Max milliseconds per tick spent scanning chunks.")
        .defaultValue(4).min(1).max(20).sliderMin(1).sliderMax(20).build());

    // ---- render
    private final Setting<Integer> renderRange = rendering.add(new IntSetting.Builder()
        .name("render-range")
        .description("Chunk radius in which flagged chunks are drawn.")
        .defaultValue(8).min(1).sliderMax(32).build());

    private final Setting<SettingColor> fillColor = rendering.add(new ColorSetting.Builder()
        .name("fill-color").defaultValue(new SettingColor(255, 0, 0, 55)).build());

    private final Setting<SettingColor> lineColor = rendering.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(255, 0, 0, 200)).build());

    private final Setting<Boolean> outline = rendering.add(new BoolSetting.Builder()
        .name("outline")
        .description("Draw a border around patches (shared chunk edges are skipped).")
        .defaultValue(true).build());

    private final Setting<Double> borderHeight = rendering.add(new DoubleSetting.Builder()
        .name("border-height")
        .description("Height of the translucent border walls. 0 = flat only.")
        .defaultValue(2.0).min(0.0).sliderMax(8.0).build());

    private final Setting<Boolean> labels = rendering.add(new BoolSetting.Builder()
        .name("heat-labels")
        .description("Show the heat number on the hottest chunk of each patch.")
        .defaultValue(true).build());

    private final Setting<Boolean> renderAtPlayerY = rendering.add(new BoolSetting.Builder()
        .name("render-at-player-y").defaultValue(true).build());

    private final Setting<Integer> fixedY = rendering.add(new IntSetting.Builder()
        .name("fixed-y").defaultValue(0).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Integer> renderHeight = rendering.add(new IntSetting.Builder()
        .name("render-height").defaultValue(1).min(1).sliderMax(8).build());

    // ---- runtime state (client thread only)
    private record Score(int heat, boolean ungrown, int heatRadius, int vetoRadius) {}
    private record BlockScan(int heat, boolean ungrown) {}

    private final Map<Long, Score> scores = new HashMap<>();       // per scanned chunk
    private final Map<Long, Integer> heat = new HashMap<>();       // spread heat
    private final Map<Long, Integer> vetoes = new HashMap<>();     // spread veto counts
    private final Map<Long, Integer> pending = new HashMap<>();    // chunk -> due tick
    private final ArrayDeque<Long> ready = new ArrayDeque<>();
    private final Set<Long> readySet = new HashSet<>();
    private final Map<Long, Integer> retries = new HashMap<>();
    private final Map<Long, Integer> flagged = new HashMap<>();    // chunk -> heat
    private final Map<Long, Integer> peaks = new HashMap<>();      // hottest chunk per patch
    private final Set<Long> announced = new HashSet<>();

    private ClientWorld lastWorld;
    private int tick;
    private boolean dirty;

    public SusChunkFinder() {
        super(
            MazaCategory.INSTANCE,
            "sus-chunk-finder",
            "Finds chunks with signs of long-term player activity from fully grown amethyst."
        );
    }

    @Override
    public void onActivate() {
        clearRuntime();
        lastWorld = null;
    }

    @Override
    public void onDeactivate() {
        clearRuntime();
        lastWorld = null;
    }

    @Override
    public String getInfoString() {
        return peaks.isEmpty() ? null : String.valueOf(peaks.size());
    }

    // ------------------------------------------------------------------ events

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) {
            clearRuntime();
            lastWorld = null;
            return;
        }

        if (mc.world != lastWorld) {
            clearRuntime();
            lastWorld = mc.world;
            sweep();
        }

        tick++;
        if (tick % 10 == 0) sweep();
        if (tick % 20 == 0) prune();

        releaseDue();
        runScans();

        if (dirty) {
            dirty = false;
            recompute();
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null || mc.player == null || event.chunk() == null) return;

        ChunkPos pos = event.chunk().getPos();
        if (!inScanRange(pos.x, pos.z)) return;

        // Light data is applied by the light engine after the chunk itself.
        schedule(ChunkPos.toLong(pos.x, pos.z), method.get() == Method.Blocks ? 1 : LIGHT_DELAY_TICKS);
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null || mc.player == null || event.pos == null) return;
        if (!isGrowthRelated(event.oldState) && !isGrowthRelated(event.newState)) return;

        int cx = event.pos.getX() >> 4;
        int cz = event.pos.getZ() >> 4;
        if (!inScanRange(cx, cz)) return;

        schedule(ChunkPos.toLong(cx, cz), LIGHT_DELAY_TICKS);
    }

    // --------------------------------------------------------------- scheduling

    private void schedule(long key, int delay) {
        pending.merge(key, tick + delay, Math::max);
    }

    /** Queue every loaded, not yet scanned chunk in range. */
    private void sweep() {
        int cx = mc.player.getChunkPos().x;
        int cz = mc.player.getChunkPos().z;
        int range = scanRange.get();

        for (int x = cx - range; x <= cx + range; x++) {
            for (int z = cz - range; z <= cz + range; z++) {
                long key = ChunkPos.toLong(x, z);
                if (scores.containsKey(key) || pending.containsKey(key) || readySet.contains(key)) continue;
                if (!loaded(x, z)) continue;
                schedule(key, method.get() == Method.Blocks ? 0 : 2);
            }
        }
    }

    private void releaseDue() {
        if (pending.isEmpty()) return;

        List<Long> due = null;
        for (Map.Entry<Long, Integer> e : pending.entrySet()) {
            if (e.getValue() <= tick) {
                if (due == null) due = new ArrayList<>();
                due.add(e.getKey());
            }
        }
        if (due == null) return;

        due.sort(Comparator.comparingDouble(this::chunkDistSq));
        for (long key : due) {
            pending.remove(key);
            if (readySet.add(key)) ready.addLast(key);
        }
    }

    private void runScans() {
        if (ready.isEmpty()) return;

        long deadline = System.nanoTime() + scanBudgetMs.get() * 1_000_000L;
        while (!ready.isEmpty()) {
            long key = ready.pollFirst();
            readySet.remove(key);
            scanOne(key);
            if (System.nanoTime() >= deadline) break;
        }
    }

    /** Drop everything that belongs to chunks that are no longer loaded. */
    private void prune() {
        List<Long> gone = new ArrayList<>();
        for (long key : scores.keySet()) {
            ChunkPos cp = new ChunkPos(key);
            if (!loaded(cp.x, cp.z)) gone.add(key);
        }
        for (long key : gone) applyScore(key, null);

        pending.keySet().removeIf(key -> {
            ChunkPos cp = new ChunkPos(key);
            return !loaded(cp.x, cp.z);
        });
        retries.keySet().removeIf(key -> !scores.containsKey(key) && !pending.containsKey(key));
    }

    // ----------------------------------------------------------------- scanning

    private void scanOne(long key) {
        ChunkPos cp = new ChunkPos(key);
        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cp.x, cp.z);
        if (chunk == null) {
            applyScore(key, null);
            return;
        }

        Method m = method.get();
        int blockHeat = 0;
        int lightHeat = 0;
        boolean ungrown = false;

        try {
            if (m != Method.Light) {
                BlockScan scan = scanBlocks(chunk);
                blockHeat = scan.heat();
                ungrown = scan.ungrown();
            }

            if (m != Method.Blocks) {
                int cells = scanLight(chunk);
                if (cells < 0) {
                    // Light for this chunk has not arrived yet: try again shortly.
                    int tries = retries.merge(key, 1, Integer::sum);
                    if (tries <= MAX_LIGHT_RETRIES) {
                        schedule(key, LIGHT_DELAY_TICKS);
                        return;
                    }
                    cells = 0;
                }
                retries.remove(key);
                lightHeat = cells / cellsPerHeat.get();
            }
        } catch (RuntimeException ignored) {
            return;
        }

        int total = Math.max(blockHeat, lightHeat);
        applyScore(key, new Score(total, ungrown, spreadRadius.get(), vetoRadius.get()));

        if (debug.get()) {
            info("Scanned %d, %d -> heat %d (blocks %d, light %d)%s",
                cp.x, cp.z, total, blockHeat, lightHeat, ungrown ? " [ungrown]" : "");
        }
    }

    /**
     * Fully grown cluster = +1 heat. A bud that sits on something other than
     * budding amethyst = +1 heat (its budding block is gone). A bud still on
     * budding amethyst is still growing and marks the chunk as "ungrown".
     */
    private BlockScan scanBlocks(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        if (sections == null) return new BlockScan(0, false);

        ChunkPos cp = chunk.getPos();
        int bottom = chunk.getBottomY();
        int lo = minY.get();
        int hi = maxY.get();
        BlockPos.Mutable support = new BlockPos.Mutable();

        int heatCount = 0;
        boolean ungrown = false;

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;

            int sectionBottom = bottom + i * 16;
            if (sectionBottom + 15 < lo || sectionBottom > hi) continue;

            // Palette-level rejection: skip sections with no growth blocks at all.
            if (!section.hasAny(SusChunkFinder::isGrowthState)) continue;

            int y0 = Math.max(0, lo - sectionBottom);
            int y1 = Math.min(15, hi - sectionBottom);

            for (int y = y0; y <= y1; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Block block = state.getBlock();

                        if (block == Blocks.AMETHYST_CLUSTER) {
                            heatCount++;
                            continue;
                        }
                        if (!isBud(block) || !state.contains(Properties.FACING)) continue;

                        Direction back = state.get(Properties.FACING).getOpposite();
                        support.set(
                            cp.getStartX() + x + back.getOffsetX(),
                            sectionBottom + y + back.getOffsetY(),
                            cp.getStartZ() + z + back.getOffsetZ()
                        );

                        Block base = blockAt(chunk, support);
                        if (base == null) continue; // neighbour chunk not loaded: unknown

                        if (base == Blocks.BUDDING_AMETHYST) ungrown = true;
                        else heatCount++;
                    }
                }
            }
        }

        return new BlockScan(heatCount, ungrown);
    }

    /**
     * Counts glowing cells from the block-light data. A fully grown cluster is a
     * source of exactly {@link #GLOW_LIGHT}; any cell whose neighbour is brighter
     * is just the falloff of a stronger light (torch, lava...) and is ignored.
     * Returns -1 when no light data is available for the chunk yet.
     */
    private int scanLight(WorldChunk chunk) {
        ClientWorld world = mc.world;
        var view = world.getLightingProvider().get(LightType.BLOCK);

        ChunkPos cp = chunk.getPos();
        int lo = minY.get();
        int hi = maxY.get();
        int minSection = Math.max(world.getBottomSectionCoord(), Math.floorDiv(lo, 16));
        int maxSection = Math.min(world.getTopSectionCoord() - 1, Math.floorDiv(hi, 16));

        BlockPos.Mutable pos = new BlockPos.Mutable();
        boolean sawData = false;
        int cells = 0;

        for (int sy = minSection; sy <= maxSection; sy++) {
            var array = view.getLightSection(ChunkSectionPos.from(cp.x, sy, cp.z));
            if (array == null || array.isUninitialized()) continue;
            sawData = true;

            for (int y = 0; y < 16; y++) {
                int wy = sy * 16 + y;
                if (wy < lo || wy > hi) continue;

                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (array.get(x, y, z) != GLOW_LIGHT) continue;
                        if (isLightPeak(world, pos, cp.getStartX() + x, wy, cp.getStartZ() + z)) cells++;
                    }
                }
            }
        }

        return sawData ? cells : -1;
    }

    private boolean isLightPeak(ClientWorld world, BlockPos.Mutable pos, int x, int y, int z) {
        for (Direction d : Direction.values()) {
            pos.set(x + d.getOffsetX(), y + d.getOffsetY(), z + d.getOffsetZ());
            if (world.getLightLevel(LightType.BLOCK, pos) > GLOW_LIGHT) return false;
        }
        return true;
    }

    // ---------------------------------------------------------- heat / veto map

    private void applyScore(long key, Score next) {
        Score old = next == null ? scores.remove(key) : scores.put(key, next);

        if (old != null) {
            if (old.heat() > 0) spread(heat, key, old.heatRadius(), -old.heat());
            if (old.ungrown()) spread(vetoes, key, old.vetoRadius(), -1);
        }
        if (next != null) {
            if (next.heat() > 0) spread(heat, key, next.heatRadius(), next.heat());
            if (next.ungrown()) spread(vetoes, key, next.vetoRadius(), 1);
        }
        dirty = true;
    }

    private static void spread(Map<Long, Integer> map, long key, int radius, int delta) {
        ChunkPos c = new ChunkPos(key);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                long k = ChunkPos.toLong(c.x + dx, c.z + dz);
                int v = map.getOrDefault(k, 0) + delta;
                if (v <= 0) map.remove(k);
                else map.put(k, v);
            }
        }
    }

    /** Rebuild the set of flagged chunks and the hottest chunk of every patch. */
    private void recompute() {
        flagged.clear();

        int need = sensitivity.get();
        int neighbours = minScanned.get();

        for (Map.Entry<Long, Integer> e : heat.entrySet()) {
            if (e.getValue() < need) continue;

            long key = e.getKey();
            if (vetoes.containsKey(key)) continue;

            Score own = scores.get(key);
            if (own != null && own.ungrown()) continue;

            ChunkPos cp = new ChunkPos(key);
            if (!loaded(cp.x, cp.z)) continue;
            if (scannedNeighbours(cp.x, cp.z) < neighbours) continue;

            flagged.put(key, e.getValue());
        }

        peaks.clear();
        peaks.putAll(hottestPerPatch(flagged));

        if (notify.get()) {
            for (Map.Entry<Long, Integer> e : peaks.entrySet()) {
                if (announced.add(e.getKey())) {
                    ChunkPos cp = new ChunkPos(e.getKey());
                    info("Suspicious chunk at %d, %d (heat %d)", cp.x * 16 + 8, cp.z * 16 + 8, e.getValue());
                }
            }
        }
        announced.retainAll(peaks.keySet());
    }

    private int scannedNeighbours(int cx, int cz) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (scores.containsKey(ChunkPos.toLong(cx + dx, cz + dz))) count++;
            }
        }
        return count;
    }

    private static Map<Long, Integer> hottestPerPatch(Map<Long, Integer> chunks) {
        Map<Long, Integer> out = new HashMap<>();
        Set<Long> seen = new HashSet<>();

        for (long start : chunks.keySet()) {
            if (!seen.add(start)) continue;

            ArrayDeque<Long> open = new ArrayDeque<>();
            open.add(start);
            long best = start;
            int bestHeat = chunks.get(start);

            while (!open.isEmpty()) {
                long current = open.poll();
                int h = chunks.get(current);
                if (h > bestHeat || (h == bestHeat && current < best)) {
                    best = current;
                    bestHeat = h;
                }

                ChunkPos c = new ChunkPos(current);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        long next = ChunkPos.toLong(c.x + dx, c.z + dz);
                        if (chunks.containsKey(next) && seen.add(next)) open.add(next);
                    }
                }
            }

            out.put(best, bestHeat);
        }

        return out;
    }

    // ---------------------------------------------------------------- rendering

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null || flagged.isEmpty()) return;

        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;
        int range = renderRange.get();

        double y = baseY();
        double slabTop = y + Math.max(0.10, renderHeight.get() * 0.10);
        double wallTop = y + Math.max(borderHeight.get(), slabTop - y);

        Color fill = fillColor.get();
        Color line = lineColor.get();

        for (long key : flagged.keySet()) {
            ChunkPos cp = new ChunkPos(key);
            if (Math.max(Math.abs(cp.x - pcx), Math.abs(cp.z - pcz)) > range) continue;

            double x1 = cp.x * 16.0;
            double z1 = cp.z * 16.0;
            double x2 = x1 + 16.0;
            double z2 = z1 + 16.0;

            // Thin filled cell, one per chunk.
            event.renderer.boxSides(x1, y, z1, x2, slabTop, z2, fill, 0);

            if (!outline.get()) continue;

            // Border only where the neighbouring chunk is not flagged, so a patch
            // reads as one merged shape instead of a grid.
            if (!flagged.containsKey(ChunkPos.toLong(cp.x, cp.z - 1))) edge(event, x1, z1, x2, z1, y, wallTop, fill, line);
            if (!flagged.containsKey(ChunkPos.toLong(cp.x, cp.z + 1))) edge(event, x1, z2, x2, z2, y, wallTop, fill, line);
            if (!flagged.containsKey(ChunkPos.toLong(cp.x - 1, cp.z))) edge(event, x1, z1, x1, z2, y, wallTop, fill, line);
            if (!flagged.containsKey(ChunkPos.toLong(cp.x + 1, cp.z))) edge(event, x2, z1, x2, z2, y, wallTop, fill, line);
        }
    }

    private void edge(Render3DEvent event, double ax, double az, double bx, double bz,
                      double y, double top, Color fill, Color line) {
        // Translucent wall (thin box) plus crisp lines on the top and bottom edge.
        if (top - y > 0.2) {
            double t = 0.05;
            event.renderer.boxSides(
                Math.min(ax, bx) - (ax == bx ? t : 0), y, Math.min(az, bz) - (az == bz ? t : 0),
                Math.max(ax, bx) + (ax == bx ? t : 0), top, Math.max(az, bz) + (az == bz ? t : 0),
                fill, 0
            );
        }
        event.renderer.line(ax, y, az, bx, y, bz, line);
        event.renderer.line(ax, top, az, bx, top, bz, line);
        event.renderer.line(ax, y, az, ax, top, az, line);
        event.renderer.line(bx, y, bz, bx, top, bz, line);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!labels.get() || mc.world == null || mc.player == null || peaks.isEmpty()) return;

        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;
        int range = renderRange.get();

        TextRenderer text = TextRenderer.get();
        Color white = new Color(255, 255, 255, 255);
        double labelY = baseY() + Math.max(borderHeight.get(), 1.0) + 1.0;

        for (Map.Entry<Long, Integer> e : peaks.entrySet()) {
            ChunkPos cp = new ChunkPos(e.getKey());
            if (Math.max(Math.abs(cp.x - pcx), Math.abs(cp.z - pcz)) > range) continue;

            Vector3d pos = new Vector3d(cp.x * 16.0 + 8.0, labelY, cp.z * 16.0 + 8.0);
            if (!NametagUtils.to2D(pos, 1.0, true)) continue;

            String label = String.valueOf(e.getValue());
            NametagUtils.begin(pos);
            text.beginBig();
            double half = text.getWidth(label, true) / 2.0;
            text.render(label, -half, -text.getHeight(true), white, true);
            text.end();
            NametagUtils.end();
        }
    }

    private double baseY() {
        return renderAtPlayerY.get() ? mc.player.getY() : fixedY.get();
    }

    // ------------------------------------------------------------------ helpers

    private boolean inScanRange(int cx, int cz) {
        return Math.max(Math.abs(cx - mc.player.getChunkPos().x), Math.abs(cz - mc.player.getChunkPos().z)) <= scanRange.get();
    }

    private boolean loaded(int cx, int cz) {
        return mc.world != null && mc.world.getChunkManager().getWorldChunk(cx, cz) != null;
    }

    private double chunkDistSq(long key) {
        ChunkPos c = new ChunkPos(key);
        double dx = c.x - mc.player.getChunkPos().x;
        double dz = c.z - mc.player.getChunkPos().z;
        return dx * dx + dz * dz;
    }

    private Block blockAt(WorldChunk home, BlockPos pos) {
        ChunkPos hp = home.getPos();
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        if (cx == hp.x && cz == hp.z) return home.getBlockState(pos).getBlock();
        if (!loaded(cx, cz)) return null;
        return mc.world.getBlockState(pos).getBlock();
    }

    private static boolean isBud(Block block) {
        return block == Blocks.SMALL_AMETHYST_BUD
            || block == Blocks.MEDIUM_AMETHYST_BUD
            || block == Blocks.LARGE_AMETHYST_BUD;
    }

    private static boolean isGrowthState(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.AMETHYST_CLUSTER || isBud(block);
    }

    private static boolean isGrowthRelated(BlockState state) {
        return state.getBlock() == Blocks.BUDDING_AMETHYST || isGrowthState(state);
    }

    private void clearRuntime() {
        scores.clear();
        heat.clear();
        vetoes.clear();
        pending.clear();
        ready.clear();
        readySet.clear();
        retries.clear();
        flagged.clear();
        peaks.clear();
        announced.clear();
        dirty = false;
    }
}
