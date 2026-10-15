package com.maza.client.module.modules.world;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.maza.client.event.EventHandler;
import com.maza.client.event.PacketEvent;
import com.maza.client.event.Render3DEvent;
import com.maza.client.event.TickEvent;
import com.maza.client.module.Category;
import com.maza.client.module.Module;
import com.maza.client.setting.*;
import com.maza.client.utils.ChatUtils;
import com.maza.client.utils.Render3DUtils;
import com.maza.client.utils.ShapeMode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * SusChunkFinder v5 - Standalone Fabric
 */
public class SusChunkFinder extends Module {
    public enum Method { Classic, Light, Both, All }
    public enum PaletteMode { Auto, On, Off }

    private static final int LIGHT_DELAY_TICKS = 8;
    private static final int LIGHT_PACKET_DELAY = 3;
    private static final int BLOCK_PACKET_DELAY = 2;
    private static final int MAX_LIGHT_RETRIES = 6;
    private static final int EDGE_LIGHT = 4;
    private static final int EMPTY_CHECK_MIN_Y = -16;
    private static final int EMPTY_CHECK_MAX_Y = 30;
    private static final int GEODE_LINK = 5;
    private static final int GEODE_CELL = 6;
    private static final int GEODE_INTERVAL_TICKS = 10;
    private static final int OWN_REMOVAL_RADIUS = 12;
    private static final int TIER_HARVESTED = 1;
    private static final int TIER_GROWN = 2;
    private static final int TIER_ACTIVE = 3;
    private static final String[] TIER_NAME = {"", "Harvested", "Grown", "Active"};
    private static final Direction[] DIRS = Direction.values();
    private static final int SLATE_MIN_Y = 0;
    private static final int SLATE_MAX_Y = 60;
    private static final int SLATE_CAP = 256;
    private static final int COOLDOWN_TICKS = 1200;
    private static final int AUTOSAVE_TICKS = 600;
    private static final int MAX_SAVED_CHUNKS = 20000;
    private static final Path SAVE_FILE = FabricLoader.getInstance().getConfigDir()
        .resolve("maza").resolve("maza-sus-chunk-finder.json");

    // Settings
    public final EnumSetting<Method> method = addSetting(new EnumSetting.Builder<Method>()
        .name("method").defaultValue(Method.All).build());

    public final EnumSetting<PaletteMode> paletteMode = addSetting(new EnumSetting.Builder<PaletteMode>()
        .name("palette-mode").defaultValue(PaletteMode.Auto).build());

    public final IntSetting scanRange = addSetting(new IntSetting.Builder()
        .name("scan-range").defaultValue(0).min(0).max(32).sliderMax(32).build());

    public final IntSetting sensitivity = addSetting(new IntSetting.Builder()
        .name("sensitivity").defaultValue(6).min(1).max(50).sliderMin(1).sliderMax(30).build());

    public final IntSetting spreadRadius = addSetting(new IntSetting.Builder()
        .name("spread-radius").defaultValue(0).min(0).max(16).sliderMin(0).sliderMax(16).build());

    public final IntSetting vetoRadius = addSetting(new IntSetting.Builder()
        .name("veto-radius").defaultValue(0).min(0).max(16).sliderMin(0).sliderMax(16).build());

    public final IntSetting minScanned = addSetting(new IntSetting.Builder()
        .name("min-scanned-neighbours").defaultValue(3).min(0).max(8).sliderMin(0).sliderMax(8).build());

    public final BoolSetting relaxEmpty = addSetting(new BoolSetting.Builder()
        .name("relax-empty-chunks").defaultValue(true).build());

    public final BoolSetting keepFlagged = addSetting(new BoolSetting.Builder()
        .name("keep-flagged").defaultValue(false).build());

    public final BoolSetting hottestOnly = addSetting(new BoolSetting.Builder()
        .name("hottest-only").defaultValue(true).build());

    public final BoolSetting notify = addSetting(new BoolSetting.Builder()
        .name("notify").defaultValue(true).build());

    public final BoolSetting rememberScores = addSetting(new BoolSetting.Builder()
        .name("remember-scores").defaultValue(true).build());

    public final BoolSetting debug = addSetting(new BoolSetting.Builder()
        .name("debug").defaultValue(false).build());

    public final IntSetting minY = addSetting(new IntSetting.Builder()
        .name("min-y").defaultValue(-64).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    public final IntSetting maxY = addSetting(new IntSetting.Builder()
        .name("max-y").defaultValue(30).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    public final BoolSetting useSources = addSetting(new BoolSetting.Builder()
        .name("glow-sources").defaultValue(true).build());

    public final BoolSetting useEdges = addSetting(new BoolSetting.Builder()
        .name("glow-edges").defaultValue(true).build());

    public final IntSetting edgeCellsPerHeat = addSetting(new IntSetting.Builder()
        .name("edge-cells-per-heat").defaultValue(12).min(1).max(64).sliderMin(1).sliderMax(64).build());

    public final BoolSetting touchGeode = addSetting(new BoolSetting.Builder()
        .name("touch-geode").defaultValue(false).build());

    public final BoolSetting paletteGate = addSetting(new BoolSetting.Builder()
        .name("palette-gate").defaultValue(true).build());

    public final DoubleSetting minMaturity = addSetting(new DoubleSetting.Builder()
        .name("min-maturity").defaultValue(0.6).min(0.0).max(1.0).sliderMin(0.0).sliderMax(1.0).build());

    public final BoolSetting geodeTiers = addSetting(new BoolSetting.Builder()
        .name("geode-tiers").defaultValue(true).build());

    public final IntSetting grownClusters = addSetting(new IntSetting.Builder()
        .name("grown-clusters").defaultValue(8).min(2).max(40).sliderMin(2).sliderMax(40).build());

    public final BoolSetting flagHarvested = addSetting(new BoolSetting.Builder()
        .name("flag-harvested").defaultValue(true).build());

    public final IntSetting harvestYoungBuds = addSetting(new IntSetting.Builder()
        .name("harvest-young-buds").defaultValue(12).min(3).max(60).sliderMin(3).sliderMax(60).build());

    public final IntSetting harvestMinMedium = addSetting(new IntSetting.Builder()
        .name("harvest-min-medium").defaultValue(3).min(0).max(20).sliderMin(0).sliderMax(20).build());

    public final IntSetting harvestMaxClusters = addSetting(new IntSetting.Builder()
        .name("harvest-max-clusters").defaultValue(2).min(0).max(10).sliderMin(0).sliderMax(10).build());

    public final BoolSetting liveEvents = addSetting(new BoolSetting.Builder()
        .name("live-events").defaultValue(true).build());

    public final IntSetting minEvents = addSetting(new IntSetting.Builder()
        .name("min-events").defaultValue(3).min(1).max(30).sliderMin(1).sliderMax(30).build());

    public final IntSetting eventWindow = addSetting(new IntSetting.Builder()
        .name("event-window-minutes").defaultValue(10).min(1).max(120).sliderMin(1).sliderMax(120).build());

    public final IntSetting ignoreNear = addSetting(new IntSetting.Builder()
        .name("ignore-near-chunks").defaultValue(0).min(0).max(32).sliderMin(0).sliderMax(32).build());

    public final BoolSetting plantClocks = addSetting(new BoolSetting.Builder()
        .name("plant-clocks").defaultValue(true).build());

    public final IntSetting plantCap = addSetting(new IntSetting.Builder()
        .name("cap-per-type").defaultValue(3).min(1).max(20).sliderMin(1).sliderMax(20).build());

    public final BoolSetting plantKelp = addSetting(new BoolSetting.Builder()
        .name("kelp").defaultValue(true).build());

    public final BoolSetting plantBeeNests = addSetting(new BoolSetting.Builder()
        .name("bee-nests").defaultValue(true).build());

    public final BoolSetting plantBamboo = addSetting(new BoolSetting.Builder()
        .name("bamboo").defaultValue(false).build());

    public final BoolSetting plantCaveVines = addSetting(new BoolSetting.Builder()
        .name("cave-vines").defaultValue(false).build());

    public final BoolSetting plantVines = addSetting(new BoolSetting.Builder()
        .name("vines").defaultValue(false).build());

    public final BoolSetting rotatedDeepslate = addSetting(new BoolSetting.Builder()
        .name("rotated-deepslate").defaultValue(true).build());

    public final IntSetting maxSlateBoxes = addSetting(new IntSetting.Builder()
        .name("max-boxes").defaultValue(256).min(1).sliderMax(1024).build());

    public final ColorSetting slateColor = addSetting(new ColorSetting.Builder()
        .name("color").defaultValue(new SettingColor(0, 255, 255, 220)).build());

    public final IntSetting scanBudgetMs = addSetting(new IntSetting.Builder()
        .name("scan-budget-ms").defaultValue(3).min(1).max(20).sliderMin(1).sliderMax(20).build());

    public final IntSetting renderRange = addSetting(new IntSetting.Builder()
        .name("render-range").defaultValue(12).min(1).sliderMax(32).build());

    public final ColorSetting fillColor = addSetting(new ColorSetting.Builder()
        .name("fill-color").defaultValue(new SettingColor(255, 0, 0, 70)).build());

    public final ColorSetting lineColor = addSetting(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(255, 0, 0, 230)).build());

    public final BoolSetting outline = addSetting(new BoolSetting.Builder()
        .name("outline").defaultValue(true).build());

    public final DoubleSetting borderHeight = addSetting(new DoubleSetting.Builder()
        .name("border-height").defaultValue(2.0).min(0.0).sliderMax(8.0).build());

    public final BoolSetting tracers = addSetting(new BoolSetting.Builder()
        .name("tracers").defaultValue(true).build());

    public final IntSetting renderY = addSetting(new IntSetting.Builder()
        .name("render-y").defaultValue(64).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    public final IntSetting renderHeight = addSetting(new IntSetting.Builder()
        .name("render-height").defaultValue(1).min(1).sliderMax(8).build());

    // Data records
    private record Bud(int x, int y, int z, int stage) {}

    private record Stats(int small, int medium, int large, int clusters) {
        static Stats of(List<Bud> buds) {
            int[] n = new int[5];
            for (Bud b : buds) n[b.stage()]++;
            return new Stats(n[1], n[2], n[3], n[4]);
        }

        int young() { return small + medium + large; }
        int total() { return young() + clusters; }
        double maturity() { return total() == 0 ? 0.0 : (double) clusters / total(); }
    }

    private record Score(int heat, boolean ungrown, boolean hasBlocks, int heatRadius, int vetoRadius) {}
    private record BlockScan(int heat, boolean ungrown, List<Bud> buds) {}
    private record LightScan(List<Bud> buds, int edges) {}
    private record Geode(List<Bud> buds, Map<Long, Integer> chunkCounts, int small, int medium, int large, int clusters) {
        int total() { return small + medium + large + clusters; }
        double maturity() { return total() == 0 ? 0.0 : (double) clusters / total(); }
    }
    private record Marker(double x, double y, double z, int tier, int value) {}
    private record PlantScan(int heat, List<BlockPos> deepslate) {
        static final PlantScan NONE = new PlantScan(0, List.of());
    }

    // Runtime state
    private final Map<Long, Score> scores = new HashMap<>();
    private final Map<Long, Integer> heat = new HashMap<>();
    private final Map<Long, Integer> vetoes = new HashMap<>();
    private final Map<Long, Integer> pending = new HashMap<>();
    private final ArrayDeque<Long> ready = new ArrayDeque<>();
    private final Set<Long> readySet = new HashSet<>();
    private final Map<Long, Integer> retries = new HashMap<>();
    private final Set<Long> lightTriggered = new HashSet<>();
    private final Map<Long, List<Bud>> emitters = new HashMap<>();
    private final Map<Long, ArrayDeque<Long>> eventLog = new HashMap<>();
    private final Map<Long, Integer> cooldown = new HashMap<>();
    private final Set<Long> restored = new HashSet<>();
    private final Map<Long, List<BlockPos>> deepslate = new HashMap<>();
    private final Map<Long, Integer> flagged = new HashMap<>();
    private final Map<Long, Integer> sticky = new HashMap<>();
    private final Map<Long, Integer> peaks = new HashMap<>();
    private final List<Marker> markers = new ArrayList<>();
    private final Set<Long> announced = new HashSet<>();
    private final Set<String> announcedGeodes = new HashSet<>();

    private List<Geode> geodes = List.of();
    private boolean geodesStale;
    private int geodesAtTick = -1000;
    private ClientWorld lastWorld;
    private int tick;
    private int appliedSignature;
    private boolean dirty;
    private JsonObject saveRoot;
    private String activeSaveKey;
    private boolean persistedApplied;
    private int lastSaveTick;

    public SusChunkFinder() {
        super("SusChunkFinder", "Finds chunks with signs of long-term player activity from amethyst growth", Category.WORLD);
    }

    @Override
    public void onEnable() {
        clearRuntime();
        lastWorld = null;
        saveRoot = rememberScores.get() ? loadSaveFile() : null;
    }

    @Override
    public void onDisable() {
        saveScores(false);
        clearRuntime();
        lastWorld = null;
    }

    private void print(String format, Object... args) {
        ChatUtils.info(String.format(format, args));
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) {
            saveScores(false);
            clearRuntime();
            lastWorld = null;
            return;
        }

        int signature = signature();
        if (mc.world != lastWorld || signature != appliedSignature) {
            if (mc.world != lastWorld) saveScores(false);
            clearRuntime();
            lastWorld = mc.world;
            appliedSignature = signature;
            activeSaveKey = saveKey();
            applyPersistedScores();
            sweep();
        }

        tick++;
        if (rememberScores.get() && tick - lastSaveTick >= AUTOSAVE_TICKS) {
            lastSaveTick = tick;
            saveScores(true);
        }
        if (tick % 10 == 0) sweep();
        if (tick % 20 == 0) {
            pruneUnloaded();
            dirty = true;
        }

        releaseDue();
        runScans();

        if (dirty) {
            dirty = false;
            recompute();
        }
    }

    @EventHandler
    public void onPacket(PacketEvent.Receive event) {
        if (event.packet instanceof ChunkDataS2CPacket chunkData) {
            if (mc.world == null || mc.player == null) return;
            if (!inScanRange(chunkData.getChunkX(), chunkData.getChunkZ())) return;
            schedule(ChunkPos.toLong(chunkData.getChunkX(), chunkData.getChunkZ()), LIGHT_DELAY_TICKS);
            return;
        }

        if (event.packet instanceof BlockUpdateS2CPacket update) {
            if (isAmethystState(update.getState())) {
                long key = ChunkPos.toLong(update.getPos().getX() >> 4, update.getPos().getZ() >> 4);
                mc.execute(() -> blockTouched(key));
            }
            return;
        }

        if (event.packet instanceof ChunkDeltaUpdateS2CPacket delta) {
            Set<Long> touched = new HashSet<>();
            delta.visitUpdates((pos, state) -> {
                if (isAmethystState(state)) touched.add(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
            });
            if (!touched.isEmpty()) mc.execute(() -> { for (long key : touched) blockTouched(key); });
            return;
        }

        if (event.packet instanceof LightUpdateS2CPacket light) {
            int cx = light.getChunkX();
            int cz = light.getChunkZ();
            mc.execute(() -> {
                if (mc.world == null || mc.player == null || !inScanRange(cx, cz)) return;
                long key = ChunkPos.toLong(cx, cz);
                lightTriggered.add(key);
                schedule(key, LIGHT_PACKET_DELAY);
            });
        }
    }

    private void blockTouched(long key) {
        if (mc.world == null || mc.player == null) return;
        ChunkPos cp = new ChunkPos(key);
        if (!inScanRange(cp.x, cp.z)) return;
        schedule(key, BLOCK_PACKET_DELAY);
    }

    private static boolean isAmethystState(BlockState state) {
        return isGrowthState(state) || state.isOf(Blocks.AMETHYST_BLOCK) || state.isOf(Blocks.BUDDING_AMETHYST);
    }

    private void schedule(long key, int delay) {
        pending.merge(key, tick + delay, Math::max);
    }

    private void sweep() {
        int cx = mc.player.getChunkPos().x;
        int cz = mc.player.getChunkPos().z;
        int range = effectiveRange();

        for (int x = cx - range; x <= cx + range; x++) {
            for (int z = cz - range; z <= cz + range; z++) {
                long key = ChunkPos.toLong(x, z);
                Integer wait = cooldown.get(key);
                if (wait != null && wait > tick) continue;
                boolean retry = wait != null;
                if (!retry && scores.containsKey(key) && !restored.contains(key)) continue;
                if (pending.containsKey(key) || readySet.contains(key)) continue;
                if (!loaded(x, z)) continue;
                if (retry) cooldown.remove(key);
                schedule(key, 2);
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

    private void pruneUnloaded() {
        pending.keySet().removeIf(key -> {
            ChunkPos cp = new ChunkPos(key);
            return !loaded(cp.x, cp.z);
        });
        retries.keySet().removeIf(key -> !scores.containsKey(key) && !pending.containsKey(key));
        lightTriggered.removeIf(key -> !pending.containsKey(key) && !readySet.contains(key));
        deepslate.keySet().removeIf(key -> {
            ChunkPos cp = new ChunkPos(key);
            return !loaded(cp.x, cp.z);
        });

        if (emitters.keySet().removeIf(key -> {
            ChunkPos cp = new ChunkPos(key);
            return !loaded(cp.x, cp.z);
        })) geodesStale = true;
    }

    private void scanOne(long key) {
        ChunkPos cp = new ChunkPos(key);
        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cp.x, cp.z);
        if (chunk == null) return;

        Method m = method.get();
        int blockHeat = 0;
        int lightHeat = 0;
        boolean ungrown = false;
        boolean lightMissing = false;
        List<Bud> blockBuds = null;
        List<Bud> lightBuds = null;
        Stats lightStats = null;

        try {
            if (m != Method.Light) {
                BlockScan scan = usePalette() ? scanPalette(chunk) : scanBlocks(chunk);
                blockHeat = scan.heat();
                ungrown = scan.ungrown();
                blockBuds = scan.buds();
            }

            if (m != Method.Classic) {
                LightScan scan = scanLight(chunk);
                if (scan == null) {
                    lightMissing = true;
                } else {
                    lightBuds = scan.buds();
                    lightStats = Stats.of(lightBuds);
                    int fromEdges = scan.edges() / Math.max(1, edgeCellsPerHeat.get());
                    lightHeat = Math.max(lightStats.clusters(), fromEdges);
                    if (lightStats.young() > 0 && lightStats.maturity() < minMaturity.get()) ungrown = true;
                }
            }
        } catch (RuntimeException e) {
            if (debug.get()) print("Scan failed at %d, %d: %s", cp.x, cp.z, e.toString());
            lightTriggered.remove(key);
            return;
        }

        PlantScan plants = scanPlantsSafe(chunk);
        if (plants.deepslate().isEmpty()) deepslate.remove(key);
        else deepslate.put(key, plants.deepslate());
        restored.remove(key);

        int total = Math.max(blockHeat, lightHeat) + plants.heat();
        boolean hasBlocks = hasBlocksInRange(chunk, EMPTY_CHECK_MIN_Y, EMPTY_CHECK_MAX_Y);
        applyScore(key, new Score(total, ungrown, hasBlocks, spreadRadius.get(), effectiveVetoRadius()));

        List<Bud> snapshot = (lightBuds != null && !lightBuds.isEmpty()) ? lightBuds
            : (blockBuds != null ? blockBuds : lightBuds);
        boolean live = lightTriggered.remove(key);
        if (snapshot != null) {
            List<Bud> previous = emitters.put(key, snapshot);
            if (live && lightBuds != null && previous != null && liveEvents.get()) {
                recordEvents(key, previous, snapshot);
            }
            geodesStale = true;
        }

        if (lightMissing) {
            int tries = retries.merge(key, 1, Integer::sum);
            if (tries <= MAX_LIGHT_RETRIES) {
                schedule(key, LIGHT_DELAY_TICKS);
            } else {
                retries.remove(key);
                cooldown.put(key, tick + COOLDOWN_TICKS);
                if (m == Method.Light) {
                    applyScore(key, null);
                    emitters.remove(key);
                    geodesStale = true;
                }
            }
        } else {
            retries.remove(key);
            cooldown.remove(key);
        }

        if (debug.get()) {
            String stages = lightStats == null ? ""
                : String.format(" stages %d/%d/%d/%d", lightStats.small(), lightStats.medium(), lightStats.large(), lightStats.clusters());
            print("Scanned %d, %d -> heat %d (blocks %d, light %d, plants %d)%s%s%s%s",
                cp.x, cp.z, total, blockHeat, lightHeat, plants.heat(), stages, ungrown ? " [ungrown]" : "",
                lightMissing ? " [no light yet]" : "",
                plants.deepslate().isEmpty() ? "" : " [" + plants.deepslate().size() + " buried deepslate]");
        }
    }

    private BlockScan scanBlocks(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        List<Bud> buds = new ArrayList<>();
        if (sections == null) return new BlockScan(0, false, buds);

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
            if (!section.hasAny(SusChunkFinder::isGrowthState)) continue;

            int y0 = Math.max(0, lo - sectionBottom);
            int y1 = Math.min(15, hi - sectionBottom);

            for (int y = y0; y <= y1; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Block block = state.getBlock();
                        int stage = stageForBlock(block);
                        if (stage == 0) continue;

                        int wx = cp.getStartX() + x;
                        int wy = sectionBottom + y;
                        int wz = cp.getStartZ() + z;
                        buds.add(new Bud(wx, wy, wz, stage));

                        if (block == Blocks.AMETHYST_CLUSTER) {
                            heatCount++;
                            continue;
                        }
                        if (!state.contains(Properties.FACING)) continue;

                        Direction back = state.get(Properties.FACING).getOpposite();
                        support.set(wx + back.getOffsetX(), wy + back.getOffsetY(), wz + back.getOffsetZ());

                        Block base = blockAt(chunk, support);
                        if (base == null) continue;

                        if (base == Blocks.BUDDING_AMETHYST) ungrown = true;
                        else heatCount++;
                    }
                }
            }
        }

        return new BlockScan(heatCount, ungrown, buds);
    }

    private BlockScan scanPalette(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        List<Bud> none = List.of();
        if (sections == null) return new BlockScan(0, false, none);

        int bottom = chunk.getBottomY();
        int lo = minY.get();
        int hi = maxY.get();
        boolean sawBud = false;
        boolean sawCluster = false;

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;

            int sectionBottom = bottom + i * 16;
            if (sectionBottom + 15 < lo || sectionBottom > hi) continue;

            if (!sawBud && section.hasAny(state -> isBud(state.getBlock()))) sawBud = true;
            if (!sawCluster && section.hasAny(state -> state.isOf(Blocks.AMETHYST_CLUSTER))) sawCluster = true;
        }

        if (sawBud) return new BlockScan(0, true, none);
        return new BlockScan(sawCluster ? 1 : 0, false, none);
    }

    private static boolean[] geodeSections(ChunkSection[] sections) {
        boolean[] out = new boolean[sections.length];
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            out[i] = section != null && !section.isEmpty() && section.hasAny(SusChunkFinder::isGeodeState);
        }
        return out;
    }

    private static boolean nearGeode(boolean[] gate, int i) {
        return gate[i] || (i > 0 && gate[i - 1]) || (i + 1 < gate.length && gate[i + 1]);
    }

    private static boolean isGeodeState(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.AMETHYST_BLOCK || block == Blocks.BUDDING_AMETHYST || isGrowthState(state);
    }

    private LightScan scanLight(WorldChunk chunk) {
        LightCache lights = new LightCache(mc.world, chunk.getPos().x, chunk.getPos().z);
        if (!lights.any) return null;

        List<Bud> buds = new ArrayList<>();
        ChunkSection[] sections = chunk.getSectionArray();
        if (sections == null) return new LightScan(buds, 0);

        ChunkPos cp = chunk.getPos();
        int bottom = chunk.getBottomY();
        int lo = minY.get();
        int hi = maxY.get();
        boolean sources = useSources.get();
        boolean edges = useEdges.get();
        boolean untrusted = usePalette();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        boolean[] gate = paletteGate.get() ? geodeSections(sections) : null;
        boolean needTouch = touchGeode.get();

        int edgeCount = 0;

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || !lights.nearLight(i)) continue;
            if (gate != null && !nearGeode(gate, i)) continue;

            int sectionBottom = bottom + i * 16;
            if (sectionBottom + 15 < lo || sectionBottom > hi) continue;

            int y0 = Math.max(lo, sectionBottom);
            int y1 = Math.min(hi, sectionBottom + 15);

            for (int y = y0; y <= y1; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int wx = cp.getStartX() + x;
                        int wz = cp.getStartZ() + z;
                        int value = lights.at(wx, y, wz);

                        if (value == 0) {
                            if (edges && touchesGlowEdge(lights, wx, y, wz)
                                && (untrusted
                                    ? lightConsistentOpening(lights, wx, y, wz)
                                    : isOpen(section.getBlockState(x, y & 15, z))
                                        && hasLitOpening(lights, cursor, wx, y, wz))) {
                                edgeCount++;
                            }
                            continue;
                        }

                        int lightStage = stageForLight(value);
                        if (!sources || lightStage == 0) continue;
                        if (!isEmitter(lights, wx, y, wz, value)) continue;

                        BlockState state = section.getBlockState(x, y & 15, z);
                        int stage = stageForBlock(state.getBlock());
                        if (stage == 0) {
                            if (state.getLuminance() > 0) continue;
                            stage = lightStage;
                        }
                        if (needTouch && !touchesGeodeBlock(chunk, cursor, wx, y, wz)) continue;
                        buds.add(new Bud(wx, y, wz, stage));
                    }
                }
            }
        }

        return new LightScan(buds, edgeCount);
    }

    private boolean touchesGeodeBlock(WorldChunk home, BlockPos.Mutable cursor, int x, int y, int z) {
        for (Direction d : DIRS) {
            BlockState neighbour = stateAt(home, cursor, x + d.getOffsetX(), y + d.getOffsetY(), z + d.getOffsetZ());
            if (neighbour != null && (neighbour.isOf(Blocks.AMETHYST_BLOCK) || neighbour.isOf(Blocks.BUDDING_AMETHYST))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEmitter(LightCache lights, int x, int y, int z, int value) {
        for (Direction d : DIRS) {
            int nx = x + d.getOffsetX();
            int nz = z + d.getOffsetZ();
            if (!lights.known(nx, nz)) return false;
            if (lights.at(nx, y + d.getOffsetY(), nz) > value) return false;
        }
        return true;
    }

    private static boolean touchesGlowEdge(LightCache lights, int x, int y, int z) {
        int brightest = 0;
        for (Direction d : DIRS) {
            int nx = x + d.getOffsetX();
            int nz = z + d.getOffsetZ();
            if (!lights.known(nx, nz)) return false;
            int v = lights.at(nx, y + d.getOffsetY(), nz);
            if (v > EDGE_LIGHT) return false;
            if (v > brightest) brightest = v;
        }
        return brightest == EDGE_LIGHT;
    }

    private boolean hasLitOpening(LightCache lights, BlockPos.Mutable cursor, int x, int y, int z) {
        for (Direction d : DIRS) {
            int nx = x + d.getOffsetX();
            int ny = y + d.getOffsetY();
            int nz = z + d.getOffsetZ();
            if (lights.at(nx, ny, nz) != EDGE_LIGHT) continue;
            if (isOpen(mc.world.getBlockState(cursor.set(nx, ny, nz)))) return true;
        }
        return false;
    }

    private static boolean isOpen(BlockState state) {
        return state.isAir() || state.isOf(Blocks.AMETHYST_CLUSTER);
    }

    private static int stageForLight(int value) {
        return switch (value) {
            case 1 -> 1;
            case 2 -> 2;
            case 4 -> 3;
            case 5 -> 4;
            default -> 0;
        };
    }

    private static int stageForBlock(Block block) {
        if (block == Blocks.SMALL_AMETHYST_BUD) return 1;
        if (block == Blocks.MEDIUM_AMETHYST_BUD) return 2;
        if (block == Blocks.LARGE_AMETHYST_BUD) return 3;
        if (block == Blocks.AMETHYST_CLUSTER) return 4;
        return 0;
    }

    private static final class LightCache {
        private final ChunkNibbleArray[][][] layers;
        private final boolean[][] chunkLoaded = new boolean[3][3];
        private final int cx;
        private final int cz;
        private final int bottom;
        private final int count;
        boolean any;

        LightCache(ClientWorld world, int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
            this.bottom = world.getBottomSectionCoord();
            this.count = world.countVerticalSections();
            this.layers = new ChunkNibbleArray[3][3][count];

            var view = world.getLightingProvider().get(LightType.BLOCK);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    chunkLoaded[dx + 1][dz + 1] = world.getChunkManager().getWorldChunk(cx + dx, cz + dz) != null;
                    for (int s = 0; s < count; s++) {
                        ChunkNibbleArray layer = view.getLightSection(ChunkSectionPos.from(cx + dx, bottom + s, cz + dz));
                        if (layer != null && layer.isUninitialized()) layer = null;
                        layers[dx + 1][dz + 1][s] = layer;
                        if (layer != null) any = true;
                    }
                }
            }
        }

        boolean known(int x, int z) {
            int dx = (x >> 4) - cx;
            int dz = (z >> 4) - cz;
            if (dx < -1 || dx > 1 || dz < -1 || dz > 1) return false;
            return chunkLoaded[dx + 1][dz + 1];
        }

        boolean nearLight(int sectionIndex) {
            for (int dx = 0; dx < 3; dx++) {
                for (int dz = 0; dz < 3; dz++) {
                    for (int s = sectionIndex - 1; s <= sectionIndex + 1; s++) {
                        if (s < 0 || s >= count) continue;
                        if (layers[dx][dz][s] != null) return true;
                    }
                }
            }
            return false;
        }

        int at(int x, int y, int z) {
            int s = (y >> 4) - bottom;
            if (s < 0 || s >= count) return 0;
            int dx = (x >> 4) - cx;
            int dz = (z >> 4) - cz;
            if (dx < -1 || dx > 1 || dz < -1 || dz > 1) return 0;
            ChunkNibbleArray layer = layers[dx + 1][dz + 1][s];
            return layer == null ? 0 : layer.get(x & 15, y & 15, z & 15);
        }
    }

    private static boolean hasBlocksInRange(WorldChunk chunk, int lo, int hi) {
        ChunkSection[] sections = chunk.getSectionArray();
        if (sections == null) return false;
        int bottom = chunk.getBottomY();

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;

            int base = bottom + i * 16;
            if (base + 15 < lo || base > hi) continue;

            for (int y = 0; y < 16; y++) {
                int wy = base + y;
                if (wy < lo || wy > hi) continue;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!section.getBlockState(x, y, z).isAir()) return true;
                    }
                }
            }
        }
        return false;
    }

    private PlantScan scanPlantsSafe(WorldChunk chunk) {
        try {
            return scanPlants(chunk);
        } catch (RuntimeException e) {
            if (debug.get()) print("Plant scan failed: %s", e.toString());
            return PlantScan.NONE;
        }
    }

    private PlantScan scanPlants(WorldChunk chunk) {
        final boolean kelp = plantKelp.get();
        final boolean bees = plantBeeNests.get();
        final boolean bamboo = plantBamboo.get();
        final boolean caveVines = plantCaveVines.get();
        final boolean vines = plantVines.get();
        final boolean plantsOn = plantClocks.get() && (kelp || bees || bamboo || caveVines || vines);
        final boolean slate = rotatedDeepslate.get();
        if (!plantsOn && !slate) return PlantScan.NONE;

        ChunkSection[] sections = chunk.getSectionArray();
        if (sections == null) return PlantScan.NONE;

        ChunkPos cp = chunk.getPos();
        int bottom = chunk.getBottomY();
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        int kelpN = 0;
        int beeN = 0;
        int bambooN = 0;
        int caveN = 0;
        int vineN = 0;
        List<BlockPos> slateList = slate ? new ArrayList<>() : null;

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;

            int sectionBottom = bottom + i * 16;

            boolean wantPlants = plantsOn && section.hasAny(st ->
                (kelp && st.isOf(Blocks.KELP))
                    || (bees && st.isOf(Blocks.BEE_NEST))
                    || (bamboo && st.isOf(Blocks.BAMBOO))
                    || (caveVines && st.isOf(Blocks.CAVE_VINES))
                    || (vines && st.isOf(Blocks.VINE)));

            boolean wantSlate = slate
                && sectionBottom + 15 >= SLATE_MIN_Y && sectionBottom <= SLATE_MAX_Y
                && slateList.size() < SLATE_CAP
                && section.hasAny(st -> st.isOf(Blocks.DEEPSLATE)
                    && st.contains(Properties.AXIS) && st.get(Properties.AXIS) != Direction.Axis.Y);

            if (!wantPlants && !wantSlate) continue;

            for (int y = 0; y < 16; y++) {
                int wy = sectionBottom + y;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) continue;

                        int wx = cp.getStartX() + x;
                        int wz = cp.getStartZ() + z;

                        if (wantPlants) {
                            if (kelp && state.isOf(Blocks.KELP)) {
                                if (state.contains(Properties.AGE_25) && state.get(Properties.AGE_25) == 25) kelpN++;
                            } else if (bees && state.isOf(Blocks.BEE_NEST)) {
                                if (state.contains(Properties.HONEY_LEVEL) && state.get(Properties.HONEY_LEVEL) == 5) beeN++;
                            } else if (caveVines && state.isOf(Blocks.CAVE_VINES)) {
                                if (state.contains(Properties.AGE_25) && state.get(Properties.AGE_25) == 25) caveN++;
                            } else if (bamboo && state.isOf(Blocks.BAMBOO)) {
                                if (state.contains(Properties.STAGE) && state.get(Properties.STAGE) == 1) {
                                    BlockState above = stateAt(chunk, cursor, wx, wy + 1, wz);
                                    if (above != null && !above.isOf(Blocks.BAMBOO)) bambooN++;
                                }
                            } else if (vines && state.isOf(Blocks.VINE)) {
                                BlockState below = stateAt(chunk, cursor, wx, wy - 1, wz);
                                if (below != null && !below.isOf(Blocks.VINE) && !below.isAir()) vineN++;
                            }
                        }

                        if (wantSlate && wy >= SLATE_MIN_Y && wy <= SLATE_MAX_Y
                            && slateList.size() < SLATE_CAP
                            && state.isOf(Blocks.DEEPSLATE)
                            && state.contains(Properties.AXIS) && state.get(Properties.AXIS) != Direction.Axis.Y
                            && fullyEnclosed(chunk, cursor, wx, wy, wz)) {
                            slateList.add(new BlockPos(wx, wy, wz));
                        }
                    }
                }
            }
        }

        int cap = plantCap.get();
        int heatVal = Math.min(kelpN, cap) + Math.min(beeN, cap) + Math.min(bambooN, cap)
            + Math.min(caveN, cap) + Math.min(vineN, cap);

        return new PlantScan(heatVal, slateList == null || slateList.isEmpty() ? List.of() : slateList);
    }

    private boolean fullyEnclosed(WorldChunk home, BlockPos.Mutable cursor, int x, int y, int z) {
        for (Direction d : DIRS) {
            BlockState neighbour = stateAt(home, cursor, x + d.getOffsetX(), y + d.getOffsetY(), z + d.getOffsetZ());
            if (neighbour == null || neighbour.isAir()) return false;
        }
        return true;
    }

    private BlockState stateAt(WorldChunk home, BlockPos.Mutable cursor, int x, int y, int z) {
        cursor.set(x, y, z);
        ChunkPos hp = home.getPos();
        int cx = x >> 4;
        int cz = z >> 4;
        if (cx == hp.x && cz == hp.z) return home.getBlockState(cursor);
        if (!loaded(cx, cz)) return null;
        return mc.world.getBlockState(cursor);
    }

    private static boolean lightConsistentOpening(LightCache lights, int x, int y, int z) {
        for (Direction d : DIRS) {
            int nx = x + d.getOffsetX();
            int ny = y + d.getOffsetY();
            int nz = z + d.getOffsetZ();
            if (!lights.known(nx, nz) || lights.at(nx, ny, nz) != EDGE_LIGHT) continue;

            int bright = 0;
            for (Direction e : DIRS) {
                int ex = nx + e.getOffsetX();
                int ez = nz + e.getOffsetZ();
                if (lights.known(ex, ez) && lights.at(ex, ny + e.getOffsetY(), ez) >= 3) bright++;
            }
            if (bright >= 2) return true;
        }
        return false;
    }

    private String saveKey() {
        var entry = mc.getCurrentServerEntry();
        String server = entry != null ? entry.address : "singleplayer";
        return server + "|" + mc.world.getRegistryKey().getValue();
    }

    private JsonObject loadSaveFile() {
        try {
            if (!Files.exists(SAVE_FILE)) return null;
            return JsonParser.parseString(Files.readString(SAVE_FILE)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void applyPersistedScores() {
        if (!rememberScores.get() || persistedApplied || saveRoot == null || activeSaveKey == null) return;
        persistedApplied = true;

        try {
            JsonElement entry = saveRoot.get(activeSaveKey);
            if (entry == null || !entry.isJsonObject()) return;

            JsonObject dim = entry.getAsJsonObject();
            restoreSticky(dim);

            if (!dim.has("sig") || dim.get("sig").getAsInt() != appliedSignature) return;
            if (!dim.has("chunks") || !dim.get("chunks").isJsonObject()) return;

            int vr = effectiveVetoRadius();
            int hr = spreadRadius.get();
            int count = 0;

            for (Map.Entry<String, JsonElement> e : dim.getAsJsonObject("chunks").entrySet()) {
                try {
                    String[] parts = e.getKey().split(",");
                    long key = ChunkPos.toLong(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                    if (scores.containsKey(key)) continue;

                    JsonObject o = e.getValue().getAsJsonObject();
                    applyScore(key, new Score(o.get("h").getAsInt(), o.get("u").getAsBoolean(), o.get("b").getAsBoolean(), hr, vr));
                    restored.add(key);
                    count++;
                } catch (RuntimeException ignored) {}
            }

            if (debug.get()) print("Restored %d saved chunk scores", count);
        } catch (RuntimeException ignored) {}
    }

    private void restoreSticky(JsonObject dim) {
        if (!keepFlagged.get() || !dim.has("sticky") || !dim.get("sticky").isJsonObject()) return;

        for (Map.Entry<String, JsonElement> e : dim.getAsJsonObject("sticky").entrySet()) {
            try {
                String[] parts = e.getKey().split(",");
                long key = ChunkPos.toLong(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                sticky.put(key, e.getValue().getAsInt());
                dirty = true;
            } catch (RuntimeException ignored) {}
        }
    }

    private void saveScores(boolean async) {
        if (!rememberScores.get() || activeSaveKey == null || (scores.isEmpty() && sticky.isEmpty())) return;

        JsonObject chunks = new JsonObject();
        int written = 0;
        for (Map.Entry<Long, Score> e : scores.entrySet()) {
            if (written >= MAX_SAVED_CHUNKS) break;
            Score s = e.getValue();
            if (s.heat() <= 0 && !s.ungrown()) continue;

            ChunkPos cp = new ChunkPos(e.getKey());
            JsonObject o = new JsonObject();
            o.addProperty("h", s.heat());
            o.addProperty("u", s.ungrown());
            o.addProperty("b", s.hasBlocks());
            chunks.add(cp.x + "," + cp.z, o);
            written++;
        }

        JsonObject dim = new JsonObject();
        dim.addProperty("sig", appliedSignature);
        dim.add("chunks", chunks);

        JsonObject marks = new JsonObject();
        for (Map.Entry<Long, Integer> e : sticky.entrySet()) {
            ChunkPos cp = new ChunkPos(e.getKey());
            marks.addProperty(cp.x + "," + cp.z, e.getValue());
        }
        dim.add("sticky", marks);

        if (saveRoot == null) saveRoot = new JsonObject();
        saveRoot.add(activeSaveKey, dim);

        String json = new Gson().toJson(saveRoot);
        Runnable write = () -> {
            try {
                Files.createDirectories(SAVE_FILE.getParent());
                Files.writeString(SAVE_FILE, json);
            } catch (IOException ignored) {}
        };

        if (async) CompletableFuture.runAsync(write);
        else write.run();
    }

    private void recordEvents(long key, List<Bud> before, List<Bud> now) {
        Map<Long, Integer> old = new HashMap<>();
        for (Bud b : before) old.put(BlockPos.asLong(b.x(), b.y(), b.z()), b.stage());

        Set<Long> present = new HashSet<>();
        int events = 0;
        boolean farChunk = chunkDistance(key) > ignoreRadius();

        for (Bud b : now) {
            long packed = BlockPos.asLong(b.x(), b.y(), b.z());
            present.add(packed);
            Integer was = old.get(packed);
            if (farChunk && was != null && b.stage() == was + 1) events++;
        }

        double meX = mc.player.getX();
        double meY = mc.player.getY();
        double meZ = mc.player.getZ();
        for (Map.Entry<Long, Integer> e : old.entrySet()) {
            if (present.contains(e.getKey())) continue;
            BlockPos pos = BlockPos.fromLong(e.getKey());
            double dx = pos.getX() + 0.5 - meX;
            double dy = pos.getY() + 0.5 - meY;
            double dz = pos.getZ() + 0.5 - meZ;
            if (dx * dx + dy * dy + dz * dz > OWN_REMOVAL_RADIUS * OWN_REMOVAL_RADIUS) events++;
        }

        if (events <= 0) return;

        ArrayDeque<Long> log = eventLog.computeIfAbsent(key, k -> new ArrayDeque<>());
        long now0 = System.currentTimeMillis();
        for (int i = 0; i < events; i++) log.addLast(now0);
        dirty = true;

        if (debug.get()) {
            ChunkPos cp = new ChunkPos(key);
            print("%d live event(s) at chunk %d, %d", events, cp.x, cp.z);
        }
    }

    private int ignoreRadius() {
        int configured = ignoreNear.get();
        return configured > 0 ? configured : mc.options.getSimulationDistance().getValue();
    }

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

    private void recompute() {
        Map<Long, Integer> candidates = new HashMap<>();
        Map<Long, Integer> tiers = new HashMap<>();

        int need = sensitivity.get();
        int neighbours = minScanned.get();
        boolean relax = relaxEmpty.get();

        for (Map.Entry<Long, Integer> e : heat.entrySet()) {
            long key = e.getKey();

            Score own = scores.get(key);
            boolean hasBlocks = own == null || own.hasBlocks();
            int threshold = (relax && !hasBlocks) ? 1 : need;
            if (e.getValue() < threshold) continue;

            if (vetoes.containsKey(key)) continue;

            ChunkPos cp = new ChunkPos(key);
            if (!loaded(cp.x, cp.z)) continue;
            if (scannedNeighbours(cp.x, cp.z) < neighbours) continue;

            candidates.put(key, e.getValue());
        }

        markers.clear();
        if (geodeTiers.get()) {
            if (geodesStale && tick - geodesAtTick >= GEODE_INTERVAL_TICKS) {
                geodes = computeGeodes();
                geodesAtTick = tick;
                geodesStale = false;
            } else if (geodesStale) {
                dirty = true;
            }

            for (Geode g : geodes) {
                int tier = geodeTier(g);
                if (tier == 0) continue;

                int value = tier == TIER_GROWN ? g.clusters() : g.small() + g.medium();
                for (long key : g.chunkCounts().keySet()) {
                    ChunkPos cp = new ChunkPos(key);
                    if (!loaded(cp.x, cp.z)) continue;
                    candidates.merge(key, value, Math::max);
                    tiers.merge(key, tier, Math::max);
                }

                double[] c = fitCenter(g.buds());
                markers.add(new Marker(c[0], c[1], c[2], tier, value));
            }
        }

        if (liveEvents.get() && !eventLog.isEmpty()) {
            long limit = System.currentTimeMillis() - eventWindow.get() * 60_000L;
            int min = minEvents.get();
            var it = eventLog.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                ArrayDeque<Long> log = e.getValue();
                while (!log.isEmpty() && log.peekFirst() < limit) log.pollFirst();
                if (log.isEmpty()) {
                    it.remove();
                    continue;
                }
                if (log.size() < min) continue;

                ChunkPos cp = new ChunkPos(e.getKey());
                if (!loaded(cp.x, cp.z)) continue;
                candidates.merge(e.getKey(), log.size() * 2, Math::max);
                tiers.merge(e.getKey(), TIER_ACTIVE, Math::max);
            }
        }

        peaks.clear();
        peaks.putAll(hottestPerPatch(candidates));

        flagged.clear();
        flagged.putAll(hottestOnly.get() ? peaks : candidates);

        if (keepFlagged.get()) {
            for (Map.Entry<Long, Integer> e : flagged.entrySet()) sticky.merge(e.getKey(), e.getValue(), Math::max);
            for (Map.Entry<Long, Integer> e : sticky.entrySet()) flagged.putIfAbsent(e.getKey(), e.getValue());
        } else if (!sticky.isEmpty()) {
            sticky.clear();
        }

        if (notify.get()) {
            for (Marker m : markers) {
                String id = m.tier() + ":" + ((int) Math.floor(m.x()) >> 4) + ":" + ((int) Math.floor(m.z()) >> 4);
                if (announcedGeodes.add(id)) {
                    print("%s geode at %d, %d, %d (%d)", TIER_NAME[m.tier()],
                        (int) Math.floor(m.x()), (int) Math.floor(m.y()), (int) Math.floor(m.z()), m.value());
                }
            }

            for (Map.Entry<Long, Integer> e : peaks.entrySet()) {
                int tier = tiers.getOrDefault(e.getKey(), 0);
                if (tier == TIER_GROWN || tier == TIER_HARVESTED) continue;
                if (announced.add(e.getKey())) {
                    ChunkPos cp = new ChunkPos(e.getKey());
                    print("%s chunk at %d, %d (heat %d)", tier == TIER_ACTIVE ? "Active" : "Suspicious",
                        cp.x * 16 + 8, cp.z * 16 + 8, e.getValue());
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

    private Map<Long, Integer> hottestPerPatch(Map<Long, Integer> chunks) {
        Map<Long, Integer> out = new HashMap<>();
        Set<Long> seen = new HashSet<>();

        for (long start : chunks.keySet()) {
            if (!seen.add(start)) continue;

            ArrayDeque<Long> open = new ArrayDeque<>();
            open.add(start);
            long bestKey = start;
            int best = chunks.get(start);

            while (!open.isEmpty()) {
                long current = open.poll();
                int value = chunks.get(current);
                if (value > best || (value == best && chunkDistSq(current) < chunkDistSq(bestKey))) {
                    best = value;
                    bestKey = current;
                }

                ChunkPos c = new ChunkPos(current);
                long[] next = {
                    ChunkPos.toLong(c.x + 1, c.z), ChunkPos.toLong(c.x - 1, c.z),
                    ChunkPos.toLong(c.x, c.z + 1), ChunkPos.toLong(c.x, c.z - 1)
                };
                for (long n : next) {
                    if (chunks.containsKey(n) && seen.add(n)) open.add(n);
                }
            }

            out.put(bestKey, best);
        }

        return out;
    }

    private List<Geode> computeGeodes() {
        List<Bud> all = new ArrayList<>();
        for (List<Bud> list : emitters.values()) all.addAll(list);
        int n = all.size();
        if (n == 0) return List.of();

        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        Map<Long, List<Integer>> cells = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Bud b = all.get(i);
            long cell = cellKey(Math.floorDiv(b.x(), GEODE_CELL), Math.floorDiv(b.y(), GEODE_CELL), Math.floorDiv(b.z(), GEODE_CELL));
            cells.computeIfAbsent(cell, k -> new ArrayList<>()).add(i);
        }

        for (int i = 0; i < n; i++) {
            Bud b = all.get(i);
            int cx = Math.floorDiv(b.x(), GEODE_CELL);
            int cy = Math.floorDiv(b.y(), GEODE_CELL);
            int cz = Math.floorDiv(b.z(), GEODE_CELL);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        List<Integer> near = cells.get(cellKey(cx + dx, cy + dy, cz + dz));
                        if (near == null) continue;
                        for (int j : near) {
                            if (j <= i) continue;
                            Bud o = all.get(j);
                            int d = Math.max(Math.abs(b.x() - o.x()), Math.max(Math.abs(b.y() - o.y()), Math.abs(b.z() - o.z())));
                            if (d <= GEODE_LINK) parent[find(parent, i)] = find(parent, j);
                        }
                    }
                }
            }
        }

        Map<Integer, List<Bud>> groups = new HashMap<>();
        for (int i = 0; i < n; i++) groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(all.get(i));

        List<Geode> out = new ArrayList<>();
        for (List<Bud> group : groups.values()) {
            int[] stage = new int[5];
            Map<Long, Integer> chunkCounts = new HashMap<>();
            for (Bud b : group) {
                stage[b.stage()]++;
                chunkCounts.merge(ChunkPos.toLong(b.x() >> 4, b.z() >> 4), 1, Integer::sum);
            }
            if (stage[3] + stage[4] == 0 && stage[2] < 3) continue;
            out.add(new Geode(group, chunkCounts, stage[1], stage[2], stage[3], stage[4]));
        }
        return out;
    }

    private int geodeTier(Geode g) {
        if (g.clusters() >= grownClusters.get() && g.maturity() >= minMaturity.get()) return TIER_GROWN;

        if (flagHarvested.get()
            && g.medium() >= harvestMinMedium.get()
            && g.small() + g.medium() >= harvestYoungBuds.get()
            && g.clusters() <= harvestMaxClusters.get()) {
            return TIER_HARVESTED;
        }
        return 0;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static long cellKey(int x, int y, int z) {
        return ((long) x & 0x1FFFFFL) | (((long) y & 0x1FFFFFL) << 21) | (((long) z & 0x1FFFFFL) << 42);
    }

    private static double[] fitCenter(List<Bud> buds) {
        int n = buds.size();
        double mx = 0, my = 0, mz = 0;
        for (Bud b : buds) {
            mx += b.x() + 0.5;
            my += b.y() + 0.5;
            mz += b.z() + 0.5;
        }
        mx /= n;
        my /= n;
        mz /= n;
        double[] centroid = {mx, my, mz};
        if (n < 8) return centroid;

        double[][] a = new double[4][5];
        for (Bud b : buds) {
            double px = b.x() + 0.5 - mx;
            double py = b.y() + 0.5 - my;
            double pz = b.z() + 0.5 - mz;
            double[] row = {2 * px, 2 * py, 2 * pz, 1.0};
            double rhs = px * px + py * py + pz * pz;
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) a[i][j] += row[i] * row[j];
                a[i][4] += row[i] * rhs;
            }
        }

        double[] sol = solve(a);
        if (sol == null) return centroid;

        double drift = Math.sqrt(sol[0] * sol[0] + sol[1] * sol[1] + sol[2] * sol[2]);
        double r2 = sol[3] + drift * drift;
        if (!Double.isFinite(drift) || !Double.isFinite(r2) || r2 < 0 || drift > 24.0) return centroid;

        double r = Math.sqrt(r2);
        if (r < 3.0 || r > 16.0) return centroid;

        return new double[]{mx + sol[0], my + sol[1], mz + sol[2]};
    }

    private static double[] solve(double[][] m) {
        for (int col = 0; col < 4; col++) {
            int pivot = col;
            for (int r = col + 1; r < 4; r++) {
                if (Math.abs(m[r][col]) > Math.abs(m[pivot][col])) pivot = r;
            }
            if (Math.abs(m[pivot][col]) < 1.0e-6) return null;

            double[] tmp = m[col];
            m[col] = m[pivot];
            m[pivot] = tmp;

            for (int r = 0; r < 4; r++) {
                if (r == col) continue;
                double f = m[r][col] / m[col][col];
                for (int c = col; c < 5; c++) m[r][c] -= f * m[col][c];
            }
        }
        return new double[]{m[0][4] / m[0][0], m[1][4] / m[1][1], m[2][4] / m[2][2], m[3][4] / m[3][3]};
    }

    @EventHandler
    public void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        if (flagged.isEmpty() && deepslate.isEmpty()) return;

        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;
        int range = renderRange.get();

        double y = renderY.get();
        double slabTop = y + Math.max(0.10, renderHeight.get() * 0.10);
        double wallTop = y + Math.max(borderHeight.get(), slabTop - y);

        SettingColor fill = fillColor.get();
        SettingColor line = lineColor.get();

        for (long key : flagged.keySet()) {
            ChunkPos cp = new ChunkPos(key);
            if (Math.max(Math.abs(cp.x - pcx), Math.abs(cp.z - pcz)) > range) continue;

            double x1 = cp.x * 16.0;
            double z1 = cp.z * 16.0;
            double x2 = x1 + 16.0;
            double z2 = z1 + 16.0;

            Box box = new Box(x1, y, z1, x2, slabTop, z2);
            Render3DUtils.drawBox(box, fill, line, ShapeMode.Sides);

            if (!outline.get()) continue;

            if (!flagged.containsKey(ChunkPos.toLong(cp.x, cp.z - 1))) edge(x1, z1, x2, z1, y, wallTop, fill, line);
            if (!flagged.containsKey(ChunkPos.toLong(cp.x, cp.z + 1))) edge(x1, z2, x2, z2, y, wallTop, fill, line);
            if (!flagged.containsKey(ChunkPos.toLong(cp.x - 1, cp.z))) edge(x1, z1, x1, z2, y, wallTop, fill, line);
            if (!flagged.containsKey(ChunkPos.toLong(cp.x + 1, cp.z))) edge(x2, z1, x2, z2, y, wallTop, fill, line);
        }

        if (rotatedDeepslate.get() && !deepslate.isEmpty()) {
            int drawn = 0;
            int cap = maxSlateBoxes.get();
            SettingColor slate = slateColor.get();
            slates:
            for (Map.Entry<Long, List<BlockPos>> e : deepslate.entrySet()) {
                ChunkPos cp = new ChunkPos(e.getKey());
                if (Math.max(Math.abs(cp.x - pcx), Math.abs(cp.z - pcz)) > range) continue;
                for (BlockPos p : e.getValue()) {
                    if (drawn++ >= cap) break slates;
                    Render3DUtils.drawBox(new Box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1), null, slate, ShapeMode.Lines);
                }
            }
        }

        if (tracers.get()) {
            double[] start = tracerStart();
            Vec3d s = new Vec3d(start[0], start[1], start[2]);

            for (long key : peaks.keySet()) {
                ChunkPos cp = new ChunkPos(key);
                if (Math.max(Math.abs(cp.x - pcx), Math.abs(cp.z - pcz)) > range) continue;
                Vec3d target = new Vec3d(cp.x * 16.0 + 8.0, slabTop, cp.z * 16.0 + 8.0);
                Render3DUtils.drawLine(s, target, line, 1.5f);
            }
        }
    }

    private void edge(double ax, double az, double bx, double bz, double y, double top, SettingColor fill, SettingColor line) {
        if (top - y > 0.2) {
            double t = 0.05;
            Box wall = new Box(
                Math.min(ax, bx) - (ax == bx ? t : 0), y, Math.min(az, bz) - (az == bz ? t : 0),
                Math.max(ax, bx) + (ax == bx ? t : 0), top, Math.max(az, bz) + (az == bz ? t : 0)
            );
            Render3DUtils.drawBox(wall, fill, line, ShapeMode.Sides);
        }
        Render3DUtils.drawLine(new Vec3d(ax, y, az), new Vec3d(bx, y, bz), line, 1.5f);
        Render3DUtils.drawLine(new Vec3d(ax, top, az), new Vec3d(bx, top, bz), line, 1.5f);
        Render3DUtils.drawLine(new Vec3d(ax, y, az), new Vec3d(ax, top, az), line, 1.5f);
        Render3DUtils.drawLine(new Vec3d(bx, y, bz), new Vec3d(bx, top, bz), line, 1.5f);
    }

    private double[] tracerStart() {
        if (mc.player == null) return new double[]{0, 0, 0};
        double yaw = Math.toRadians(mc.player.getYaw());
        double pitch = Math.toRadians(mc.player.getPitch());
        double cosPitch = Math.cos(pitch);
        double lx = -Math.sin(yaw) * cosPitch;
        double ly = -Math.sin(pitch);
        double lz = Math.cos(yaw) * cosPitch;
        return new double[]{
            mc.player.getX() + lx * 0.6,
            mc.player.getEyeY() + ly * 0.6,
            mc.player.getZ() + lz * 0.6
        };
    }

    private int effectiveRange() {
        int configured = scanRange.get();
        if (configured > 0) return configured;
        return mc.options.getViewDistance().getValue() + 1;
    }

    private int effectiveVetoRadius() {
        return vetoRadius.get() == 0 ? spreadRadius.get() : vetoRadius.get();
    }

    private boolean usePalette() {
        return switch (paletteMode.get()) {
            case On -> true;
            case Off -> false;
            case Auto -> isDonutFolia();
        };
    }

    private boolean isDonutFolia() {
        var handler = mc.getNetworkHandler();
        if (handler == null) return false;
        String brand = handler.getBrand();
        return brand != null && brand.contains("DonutFolia");
    }

    private int signature() {
        return Objects.hash(method.get().ordinal(), usePalette(), spreadRadius.get(), effectiveVetoRadius(),
            minY.get(), maxY.get(), useSources.get(), useEdges.get(), edgeCellsPerHeat.get(),
            minMaturity.get(), paletteGate.get(), touchGeode.get(), plantClocks.get(), plantCap.get(), plantKelp.get(), plantBeeNests.get(),
            plantBamboo.get(), plantCaveVines.get(), plantVines.get());
    }

    private boolean inScanRange(int cx, int cz) {
        return Math.max(Math.abs(cx - mc.player.getChunkPos().x), Math.abs(cz - mc.player.getChunkPos().z)) <= effectiveRange();
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

    private int chunkDistance(long key) {
        ChunkPos c = new ChunkPos(key);
        return Math.max(Math.abs(c.x - mc.player.getChunkPos().x), Math.abs(c.z - mc.player.getChunkPos().z));
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

    private void clearRuntime() {
        scores.clear();
        heat.clear();
        vetoes.clear();
        pending.clear();
        ready.clear();
        readySet.clear();
        retries.clear();
        lightTriggered.clear();
        emitters.clear();
        eventLog.clear();
        sticky.clear();
        cooldown.clear();
        restored.clear();
        deepslate.clear();
        persistedApplied = false;
        flagged.clear();
        peaks.clear();
        markers.clear();
        announced.clear();
        announcedGeodes.clear();
        geodes = List.of();
        geodesStale = false;
        geodesAtTick = -1000;
        dirty = false;
    }
}
