package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class SusChunkFinder extends Module {

    /* ============================================================
       SETTINGS
       ============================================================ */

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgScan = settings.createGroup("Scan");
    private final SettingGroup sgDetection = settings.createGroup("Detection");
    private final SettingGroup sgPerformance = settings.createGroup("Performance");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgDebug = settings.createGroup("Debug");

    private final Setting<Integer> scanRadius = sgGeneral.add(
        new IntSetting.Builder()
            .name("scan-radius")
            .description("Tarama yarıçapı.")
            .defaultValue(8)
            .min(1)
            .max(32)
            .sliderMax(16)
            .build()
    );

    private final Setting<Integer> sensitivity = sgDetection.add(
        new IntSetting.Builder()
            .name("sensitivity")
            .description("SUS tespit hassasiyeti.")
            .defaultValue(60)
            .min(0)
            .max(100)
            .sliderMax(100)
            .build()
    );

    private final Setting<Integer> minimumScore = sgDetection.add(
        new IntSetting.Builder()
            .name("minimum-score")
            .description("ESP için gereken minimum SUS skoru.")
            .defaultValue(45)
            .min(1)
            .max(200)
            .sliderMax(100)
            .build()
    );

    private final Setting<Boolean> detectBudding = sgDetection.add(
        new BoolSetting.Builder()
            .name("budding-amethyst")
            .description("Budding Amethyst tespiti.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> detectClusters = sgDetection.add(
        new BoolSetting.Builder()
            .name("amethyst-clusters")
            .description("Amethyst Cluster tespiti.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> detectBuds = sgDetection.add(
        new BoolSetting.Builder()
            .name("amethyst-buds")
            .description("Amethyst bud tespiti.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> detectVines = sgDetection.add(
        new BoolSetting.Builder()
            .name("cave-vines")
            .description("Cave Vines tespiti.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> clusterAnalysis = sgDetection.add(
        new BoolSetting.Builder()
            .name("cluster-analysis")
            .description("Yakın hedef blokları cluster olarak analiz eder.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> rescanDelay = sgScan.add(
        new IntSetting.Builder()
            .name("rescan-delay")
            .description("Aynı chunk'ın tekrar taranması için bekleme.")
            .defaultValue(100)
            .min(10)
            .max(2000)
            .sliderMax(500)
            .build()
    );

    private final Setting<Boolean> directionPriority = sgScan.add(
        new BoolSetting.Builder()
            .name("direction-priority")
            .description("Oyuncunun baktığı yöndeki chunk'lara öncelik verir.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> blocksPerTick = sgPerformance.add(
        new IntSetting.Builder()
            .name("blocks-per-tick")
            .description("Tick başına temel tarama bütçesi.")
            .defaultValue(1500)
            .min(100)
            .max(10000)
            .sliderMax(5000)
            .build()
    );

    private final Setting<Boolean> adaptivePerformance = sgPerformance.add(
        new BoolSetting.Builder()
            .name("adaptive-performance")
            .description("FPS'e göre tarama hızını ayarlar.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> minimumFps = sgPerformance.add(
        new IntSetting.Builder()
            .name("minimum-fps")
            .description("Tarama sırasında hedeflenen minimum FPS.")
            .defaultValue(45)
            .min(15)
            .max(240)
            .sliderMax(120)
            .build()
    );

    private final Setting<Double> renderHeight = sgRender.add(
        new DoubleSetting.Builder()
            .name("render-height")
            .description("Yatay chunk ESP'sinin kalınlığı.")
            .defaultValue(0.08)
            .min(0.01)
            .max(2.0)
            .sliderMax(1.0)
            .decimalPlaces(2)
            .build()
    );

    private final Setting<Integer> renderRange = sgRender.add(
        new IntSetting.Builder()
            .name("render-range")
            .description("ESP render mesafesi.")
            .defaultValue(12)
            .min(1)
            .max(32)
            .sliderMax(16)
            .build()
    );

    private final Setting<Boolean> renderAtPlayerY = sgRender.add(
        new BoolSetting.Builder()
            .name("render-at-player-y")
            .description("Yatay ESP'yi oyuncunun bulunduğu Y seviyesinde gösterir.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> fixedRenderY = sgRender.add(
        new IntSetting.Builder()
            .name("fixed-render-y")
            .description("Oyuncu Y seviyesi kullanılmıyorsa ESP'nin Y seviyesi.")
            .defaultValue(0)
            .min(-64)
            .max(320)
            .sliderMax(100)
            .visible(() -> !renderAtPlayerY.get())
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("Chunk ESP şekli.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(
        new ColorSetting.Builder()
            .name("side-color")
            .description("ESP dolgu rengi ve saydamlığı.")
            .defaultValue(new SettingColor(255, 30, 30, 35))
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(
        new ColorSetting.Builder()
            .name("line-color")
            .description("ESP çizgi rengi.")
            .defaultValue(new SettingColor(255, 30, 30, 230))
            .build()
    );

    private final Setting<Boolean> debug = sgDebug.add(
        new BoolSetting.Builder()
            .name("debug")
            .description("Debug bilgilerini gösterir.")
            .defaultValue(false)
            .build()
    );

    /* ============================================================
       TARGET BLOCKS
       ============================================================ */

    private static final Set<Block> BUDDING_BLOCKS = Set.of(
        Blocks.BUDDING_AMETHYST
    );

    private static final Set<Block> CLUSTER_BLOCKS = Set.of(
        Blocks.AMETHYST_CLUSTER
    );

    private static final Set<Block> BUD_BLOCKS = Set.of(
        Blocks.SMALL_AMETHYST_BUD,
        Blocks.MEDIUM_AMETHYST_BUD,
        Blocks.LARGE_AMETHYST_BUD
    );

    private static final Set<Block> VINE_BLOCKS = Set.of(
        Blocks.CAVE_VINES,
        Blocks.CAVE_VINES_PLANT
    );

    /* ============================================================
       QUEUES / CACHE
       ============================================================ */

    private final PriorityQueue<ChunkTask> queue =
        new PriorityQueue<>(Comparator.comparingDouble(ChunkTask::priority));

    private final Set<Long> queuedChunks = new HashSet<>();

    private final Map<Long, ChunkResult> results = new HashMap<>();

    private final Map<Long, Long> lastScanned = new HashMap<>();

    private ScanTask currentTask;

    /* ============================================================
       STATISTICS
       ============================================================ */

    private long tickCounter;
    private long scannedChunks;
    private long scannedBlocks;
    private long foundTargets;
    private long suspiciousChunks;

    private int currentFps = 60;

    /* ============================================================
       CONSTRUCTOR
       ============================================================ */

    public SusChunkFinder() {
        super(
            MazaCategory.INSTANCE,
            "sus-chunk-finder",
            "Şüpheli chunk'ları detaylı şekilde analiz eder."
        );
    }

    /* ============================================================
       ACTIVATE
       ============================================================ */

    @Override
    public void onActivate() {
        clearAll();

        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }

        rebuildQueue();

        if (debug.get()) {
            info("SusChunkFinder aktif.");
        }
    }

    /* ============================================================
       DEACTIVATE
       ============================================================ */

    @Override
    public void onDeactivate() {
        clearAll();
    }

    /* ============================================================
       TICK
       ============================================================ */

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }

        tickCounter++;

        updateFps();

        if (tickCounter % 5 == 0) {
            refreshQueue();
        }

        if (currentTask != null) {
            processTask();

            if (currentTask != null) {
                return;
            }
        }

        startNextTask();
    }

    /* ============================================================
       QUEUE
       ============================================================ */

    private void rebuildQueue() {
        queue.clear();
        queuedChunks.clear();

        int centerX = mc.player.getBlockX() >> 4;
        int centerZ = mc.player.getBlockZ() >> 4;

        int radius = scanRadius.get();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {

                int chunkX = centerX + x;
                int chunkZ = centerZ + z;

                if (!isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }

                queueChunk(
                    chunkX,
                    chunkZ,
                    calculatePriority(chunkX, chunkZ, centerX, centerZ)
                );
            }
        }
    }

    private void refreshQueue() {
        if (mc == null || mc.player == null) {
            return;
        }

        int centerX = mc.player.getBlockX() >> 4;
        int centerZ = mc.player.getBlockZ() >> 4;

        int radius = scanRadius.get();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {

                int chunkX = centerX + x;
                int chunkZ = centerZ + z;

                long key = ChunkPos.toLong(chunkX, chunkZ);

                if (queuedChunks.contains(key)) {
                    continue;
                }

                Long previous = lastScanned.get(key);

                if (previous != null &&
                    tickCounter - previous < rescanDelay.get()) {
                    continue;
                }

                if (!isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }

                queueChunk(
                    chunkX,
                    chunkZ,
                    calculatePriority(chunkX, chunkZ, centerX, centerZ)
                );
            }
        }

        cleanupCache();
    }

    private void queueChunk(int chunkX, int chunkZ, double priority) {
        long key = ChunkPos.toLong(chunkX, chunkZ);

        if (queuedChunks.contains(key)) {
            return;
        }

        queuedChunks.add(key);

        queue.offer(
            new ChunkTask(chunkX, chunkZ, priority)
        );
    }

    /* ============================================================
       PRIORITY
       ============================================================ */

    private double calculatePriority(
        int chunkX,
        int chunkZ,
        int centerX,
        int centerZ
    ) {
        double dx = chunkX - centerX;
        double dz = chunkZ - centerZ;

        double distance = Math.sqrt(
            dx * dx + dz * dz
        );

        double priority = distance;

        if (!directionPriority.get()) {
            return priority;
        }

        if (mc == null || mc.player == null) {
            return priority;
        }

        Vec3d look = mc.player.getRotationVec(1.0f);

        double horizontalLength =
            Math.sqrt(look.x * look.x + look.z * look.z);

        if (horizontalLength <= 0.001) {
            return priority;
        }

        double lookX = look.x / horizontalLength;
        double lookZ = look.z / horizontalLength;

        double chunkLength =
            Math.sqrt(dx * dx + dz * dz);

        if (chunkLength <= 0.001) {
            return priority;
        }

        double directionX = dx / chunkLength;
        double directionZ = dz / chunkLength;

        double dot =
            lookX * directionX +
            lookZ * directionZ;

        priority -= dot * 2.5;

        return priority;
    }

    /* ============================================================
       TASK START
       ============================================================ */

    private void startNextTask() {
        while (!queue.isEmpty()) {

            ChunkTask task = queue.poll();

            if (task == null) {
                return;
            }

            queuedChunks.remove(task.key());

            if (!isChunkLoaded(task.chunkX, task.chunkZ)) {
                continue;
            }

            Long previous = lastScanned.get(task.key());

            if (previous != null &&
                tickCounter - previous < rescanDelay.get()) {
                continue;
            }

            WorldChunk chunk =
                getChunk(task.chunkX, task.chunkZ);

            if (chunk == null) {
                continue;
            }

            currentTask =
                new ScanTask(chunk);

            return;
        }
    }

    /* ============================================================
       TASK PROCESSING
       ============================================================ */

    private void processTask() {
        if (currentTask == null) {
            return;
        }

        int budget = getScanBudget();

        for (int i = 0; i < budget; i++) {

            if (currentTask.finished()) {
                finishTask();
                return;
            }

            currentTask.scanNext();

            scannedBlocks++;

            if (currentTask.finished()) {
                finishTask();
                return;
            }
        }
    }

    /* ============================================================
       TASK FINISH
       ============================================================ */

    private void finishTask() {
        if (currentTask == null) {
            return;
        }

        ChunkResult result =
            currentTask.createResult();

        long key = result.key;

        results.put(key, result);

        lastScanned.put(
            key,
            tickCounter
        );

        scannedChunks++;

        foundTargets += result.targetCount;

        if (result.score >= effectiveMinimumScore()) {
            suspiciousChunks++;
        }

        currentTask = null;
    }

    /* ============================================================
       PERFORMANCE
       ============================================================ */

    private int getScanBudget() {
        int base = blocksPerTick.get();

        if (!adaptivePerformance.get()) {
            return base;
        }

        int minimum = minimumFps.get();

        if (currentFps < minimum) {
            return Math.max(100, base / 4);
        }

        if (currentFps < minimum + 10) {
            return Math.max(150, base / 2);
        }

        if (currentFps > minimum + 50) {
            return Math.min(10000, base * 2);
        }

        return base;
    }

    private void updateFps() {
        try {
            currentFps =
                MinecraftClient.getInstance().getCurrentFps();
        }
        catch (Throwable ignored) {
            currentFps = 60;
        }

        if (currentFps <= 0) {
            currentFps = 60;
        }
    }

    /* ============================================================
       SCORE
       ============================================================ */

    private int effectiveMinimumScore() {
        int base = minimumScore.get();

        int reduction =
            (int) ((100.0 - sensitivity.get()) * 0.25);

        return Math.max(
            1,
            base - reduction
        );
    }

    private int calculateScore(ScanData data) {
        int score = 0;

        /*
         * Genel hedef yoğunluğu.
         */
        score += Math.min(
            data.targetCount * 2,
            40
        );

        /*
         * Budding Amethyst güçlü sinyal.
         */
        score += Math.min(
            data.buddingCount * 10,
            50
        );

        /*
         * Cluster.
         */
        score += Math.min(
            data.clusterCount * 4,
            30
        );

        /*
         * Bud.
         */
        score += Math.min(
            data.budCount * 2,
            20
        );

        /*
         * Cave Vines.
         */
        score += Math.min(
            data.vineCount * 2,
            25
        );

        /*
         * Farklı hedef tipleri.
         */
        score +=
            data.distinctTypes * 5;

        /*
         * Cluster yoğunluğu.
         */
        if (data.largestCluster >= 2) {
            score += Math.min(
                data.largestCluster * 3,
                30
            );
        }

        /*
         * Yoğunluk bonusları.
         */
        if (data.density > 0.0025) {
            score += 10;
        }

        if (data.density > 0.005) {
            score += 10;
        }

 
