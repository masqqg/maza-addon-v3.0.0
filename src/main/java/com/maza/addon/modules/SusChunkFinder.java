package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
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
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

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
            .description("Oyuncunun etrafında taranacak chunk yarıçapı.")
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
            .description("Chunk'ın ESP olarak gösterilmesi için gereken minimum skor.")
            .defaultValue(45)
            .min(1)
            .max(200)
            .sliderMax(100)
            .build()
    );

    private final Setting<Integer> rescanDelay = sgScan.add(
        new IntSetting.Builder()
            .name("rescan-delay")
            .description("Aynı chunk'ın tekrar taranması için minimum süre.")
            .defaultValue(100)
            .min(10)
            .max(2000)
            .sliderMax(500)
            .build()
    );

    private final Setting<Boolean> scanAmethyst = sgDetection.add(
        new BoolSetting.Builder()
            .name("amethyst")
            .description("Amethyst bloklarını analiz eder.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> scanCaveVines = sgDetection.add(
        new BoolSetting.Builder()
            .name("cave-vines")
            .description("Cave Vines bloklarını analiz eder.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> useClusters = sgDetection.add(
        new BoolSetting.Builder()
            .name("clusters")
            .description("Yakın hedef blokları cluster olarak analiz eder.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> prioritizeDirection = sgScan.add(
        new BoolSetting.Builder()
            .name("direction-priority")
            .description("Oyuncunun baktığı yöne doğru olan chunk'lara öncelik verir.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> baseBlocksPerTick = sgPerformance.add(
        new IntSetting.Builder()
            .name("blocks-per-tick")
            .description("Normal koşullarda tick başına taranabilecek blok sayısı.")
            .defaultValue(1500)
            .min(100)
            .max(10000)
            .sliderMax(5000)
            .build()
    );

    private final Setting<Boolean> adaptivePerformance = sgPerformance.add(
        new BoolSetting.Builder()
            .name("adaptive-performance")
            .description("FPS'e göre tarama hızını otomatik ayarlar.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> minimumFps = sgPerformance.add(
        new IntSetting.Builder()
            .name("minimum-fps")
            .description("Tarama sırasında korunması hedeflenen minimum FPS.")
            .defaultValue(45)
            .min(15)
            .max(240)
            .sliderMax(120)
            .build()
    );

    private final Setting<Integer> renderRange = sgRender.add(
        new IntSetting.Builder()
            .name("render-range")
            .description("SUS chunk'larının maksimum render mesafesi.")
            .defaultValue(12)
            .min(1)
            .max(32)
            .sliderMax(16)
            .build()
    );

    private final Setting<Boolean> renderScore = sgRender.add(
        new BoolSetting.Builder()
            .name("render-score")
            .description("ESP üzerinde skor gösterimini etkinleştirir.")
            .defaultValue(false)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(
        new ColorSetting.Builder()
            .name("side-color")
            .description("ESP dolgu rengi.")
            .defaultValue(new SettingColor(255, 40, 40, 35))
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(
        new ColorSetting.Builder()
            .name("line-color")
            .description("ESP çizgi rengi.")
            .defaultValue(new SettingColor(255, 40, 40, 220))
            .build()
    );

    private final Setting<Boolean> debug = sgDebug.add(
        new BoolSetting.Builder()
            .name("debug")
            .description("Tarama istatistiklerini chat'e yazdırır.")
            .defaultValue(false)
            .build()
    );

    /* ============================================================
       TARGET BLOCKS
       ============================================================ */

    private static final Set<Block> AMETHYST_BLOCKS = Set.of(
        Blocks.BUDDING_AMETHYST,
        Blocks.AMETHYST_CLUSTER,
        Blocks.LARGE_AMETHYST_BUD,
        Blocks.MEDIUM_AMETHYST_BUD,
        Blocks.SMALL_AMETHYST_BUD
    );

    private static final Set<Block> VINE_BLOCKS = Set.of(
        Blocks.CAVE_VINES,
        Blocks.CAVE_VINES_PLANT
    );

    /* ============================================================
       INTERNAL STATE
       ============================================================ */

    private final PriorityQueue<ChunkTask> scanQueue =
        new PriorityQueue<>(Comparator.comparingDouble(ChunkTask::priority));

    private final Map<Long, ChunkResult> results = new HashMap<>();
    private final Map<Long, Long> lastScanned = new HashMap<>();
    private final Set<Long> queued = new HashSet<>();

    private ScanTask currentTask;

    private long tickCounter;
    private long scannedChunks;
    private long scannedBlocks;
    private long suspiciousChunks;
    private long targetBlocks;

    private int lastFps = 60;

    /* ============================================================
       CONSTRUCTOR
       ============================================================ */

    public SusChunkFinder() {
        super(
            MazaCategory.INSTANCE,
            "sus-chunk-finder",
            "Amethyst ve cave-vine yoğunluğuna göre şüpheli chunk'ları analiz eder."
        );
    }

    /* ============================================================
       ACTIVATE
       ============================================================ */

    @Override
    public void onActivate() {
        clearScanner();

        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }

        buildQueue();

        if (debug.get()) {
            info("SusChunkFinder aktif. İlk tarama kuyruğu oluşturuldu.");
        }
    }

    /* ============================================================
       DEACTIVATE
       ============================================================ */

    @Override
    public void onDeactivate() {
        clearScanner();

        if (debug.get()) {
            info("SusChunkFinder kapatıldı.");
        }
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

        /*
         * Her birkaç tick'te bir yeni chunk'ları kuyruğa ekle.
         * Böylece oyuncu hareket ettiğinde sistem yeni alanı otomatik
         * olarak taramaya başlar.
         */
        if (tickCounter % 5 == 0) {
            refreshQueue();
        }

        /*
         * Önce aktif task'ı devam ettir.
         */
        if (currentTask != null) {
            processCurrentTask();

            /*
             * Task bitmediyse bu tick'te yeni chunk başlatma.
             */
            if (currentTask != null) {
                return;
            }
        }

        /*
         * Kuyruktan sıradaki chunk'ı al.
         */
        startNextTask();
    }

    /* ============================================================
       QUEUE CREATION
       ============================================================ */

    private void buildQueue() {
        scanQueue.clear();
        queued.clear();

        if (mc == null || mc.player == null) {
            return;
        }

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;

        int radius = scanRadius.get();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {

                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;

                double priority = calculatePriority(
                    chunkX,
                    chunkZ,
                    playerChunkX,
                    playerChunkZ
                );

                queueChunk(chunkX, chunkZ, priority);
            }
        }
    }

    private void refreshQueue() {
        if (mc == null || mc.player == null) {
            return;
        }

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;

        int radius = scanRadius.get();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {

                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;

                long key = ChunkPos.toLong(chunkX, chunkZ);

                if (queued.contains(key)) {
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

                double priority = calculatePriority(
                    chunkX,
                    chunkZ,
                    playerChunkX,
                    playerChunkZ
                );

                queueChunk(chunkX, chunkZ, priority);
            }
        }

        cleanupOldResults();
    }

    private void queueChunk(int chunkX, int chunkZ, double priority) {
        long key = ChunkPos.toLong(chunkX, chunkZ);

        if (queued.contains(key)) {
            return;
        }

        queued.add(key);
        scanQueue.offer(new ChunkTask(chunkX, chunkZ, priority));
    }

    /* ============================================================
       PRIORITY
       ============================================================ */

    private double calculatePriority(
        int chunkX,
        int chunkZ,
        int playerChunkX,
        int playerChunkZ
    ) {
        double dx = chunkX - playerChunkX;
        double dz = chunkZ - playerChunkZ;

        double distance = Math.sqrt(dx * dx + dz * dz);

        /*
         * Yakın chunk = daha yüksek öncelik.
         */
        double priority = distance;

        if (prioritizeDirection.get() && mc != null && mc.player != null) {

            Vec3d look = mc.player.getRotationVec(1.0f);

            double length = Math.sqrt(look.x * look.x + look.z * look.z);

            if (length > 0.001) {

                double normalizedX = look.x / length;
                double normalizedZ = look.z / length;

                double chunkDirectionX = dx;
                double chunkDirectionZ = dz;

                double chunkLength = Math.sqrt(
                    chunkDirectionX * chunkDirectionX +
                    chunkDirectionZ * chunkDirectionZ
                );

                if (chunkLength > 0.001) {

                    chunkDirectionX /= chunkLength;
                    chunkDirectionZ /= chunkLength;

                    double dot =
                        normalizedX * chunkDirectionX +
                        normalizedZ * chunkDirectionZ;

                    /*
                     * Baktığımız yöndeki chunk'ları öne al.
                     */
                    priority -= dot * 2.5;
                }
            }
        }

        return priority;
    }

    /* ============================================================
       START TASK
       ============================================================ */

    private void startNextTask() {
        while (!scanQueue.isEmpty()) {

            ChunkTask task = scanQueue.poll();

            if (task == null) {
                return;
            }

            long key = task.key();

            queued.remove(key);

            if (!isChunkLoaded(task.chunkX, task.chunkZ)) {
                continue;
            }

            Long previous = lastScanned.get(key);

            if (previous != null &&
                tickCounter - previous < rescanDelay.get()) {
                continue;
            }

            WorldChunk chunk = getChunk(task.chunkX, task.chunkZ);

            if (chunk == null) {
                continue;
            }

            currentTask = new ScanTask(chunk);

            return;
        }
    }

    /* ============================================================
       PROCESS CURRENT TASK
       ============================================================ */

    private void processCurrentTask() {
        if (currentTask == null) {
            return;
        }

        int budget = getCurrentBudget();

        int processed = 0;

        while (processed < budget && currentTask.hasNext()) {

            currentTask.scanNextBlock();

            processed++;
            scannedBlocks++;

            if (currentTask.finished()) {
                finishTask();
                return;
            }
        }
    }

    /* ============================================================
       FINISH TASK
       ============================================================ */

    private void finishTask() {
        if (currentTask == null) {
            return;
        }

        ChunkResult result = currentTask.buildResult();

        long key = result.key;

        results.put(key, result);

        lastScanned.put(key, tickCounter);

        scannedChunks++;

        if (result.targetCount > 0) {
            targetBlocks += result.targetCount;
        }

        if (result.score >= getEffectiveMinimumScore()) {
            suspiciousChunks++;
        }

        currentTask = null;
    }

    /* ============================================================
       PERFORMANCE
       ============================================================ */

    private int getCurrentBudget() {

        int base = baseBlocksPerTick.get();

        if (!adaptivePerformance.get()) {
            return base;
        }

        int fps = lastFps;

        int minimum = minimumFps.get();

        if (fps <= minimum) {
            return Math.max(100, base / 4);
        }

        if (fps <= minimum + 10) {
            return Math.max(150, base / 2);
        }

        if (fps >= minimum + 50) {
            return Math.min(base * 2, 10000);
        }

        return base;
    }

    private void updateFps() {
        if (mc == null) {
            return;
        }

        /*
         * MinecraftClient#getCurrentFps API sürüme göre değişebildiği
         * için güvenli fallback kullanıyoruz.
         */
        try {
            lastFps = MinecraftClient.getInstance().getCurrentFps();
        }
        catch (Throwable ignored) {
            lastFps = 60;
        }

        if (lastFps <= 0) {
            lastFps = 60;
        }
    }

    /* ============================================================
       SCORE
       ============================================================ */

    private int getEffectiveMinimumScore() {

        int base = minimumScore.get();

        /*
         * Sensitivity yükseldikçe daha düşük skorlu sonuçlara izin ver.
         */
        int reduction =
            (int) ((100.0 - sensitivity.get()) * 0.25);

        return Math.max(1, base - reduction);
    }

    private int calculateScore(ScanData data) {

        int score = 0;

        /*
         * Temel hedef yoğunluğu.
         */
        score += Math.min(data.targetCount * 2, 40);

        /*
         * Budding Amethyst çok daha güçlü sinyal.
         */
        score += Math.min(data.buddingCount * 8, 40);

        /*
         * Amethyst cluster.
         */
        score += Math.min(data.clusterCount * 4, 25);

        /*
         * Bud çeşitleri.
         */
        score += Math.min(data.budCount * 2, 20);

        /*
         * Cave vines.
         */
        score += Math.min(data.caveVineCount * 2, 25);

        /*
         * Farklı hedef türlerinin bulunması.
         */
        score += data.distinctTargetTypes * 5;

        /*
         * Cluster analizi.
         */
        if (data.largestCluster >= 2) {
            score += Math.min(data.largestCluster * 3, 30);
        }

        /*
         * Yoğunluk.
         */
        if (data.density > 0.0025) {
            score += 10;
        }

        if (data.density > 0.005) {
            score += 10;
        }

        if (data.density > 0.01) {
            score += 15;
        }

        /*
         * Hassasiyet.
         */
        double sensitivityMultiplier =
            0.60 + (sensitivity.get() / 100.0) * 0.60;

        score = (int) (score * sensitivityMultiplier);

        return score;
    }

    /* ============================================================
       CLEANUP
       ============================================================ */

    private void cleanupOldResults() {

        if (mc == null || mc.player == null) {
            return;
        }

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;

        int maxDistance = scanRadius.get() + 4;

        Iterator<Map.Entry<Long, ChunkResult>> iterator =
            results.entrySet().iterator();

        while (iterator.hasNext()) {

            Map.Entry<Long, ChunkResult> entry = iterator.next();

            ChunkPos pos = new ChunkPos(entry.getKey());

            int dx = pos.x - playerChunkX;
            int dz = pos.z - playerChunkZ;

            if (Math.abs
