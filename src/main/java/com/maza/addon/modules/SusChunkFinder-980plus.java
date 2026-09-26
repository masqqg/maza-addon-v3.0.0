package com.maza.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class SusChunkFinder extends Module {
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup detection = settings.createGroup("Detection");
    private final SettingGroup performance = settings.createGroup("Performance");
    private final SettingGroup rendering = settings.createGroup("Render");
    private final SettingGroup analysis = settings.createGroup("Analysis");

    private final Setting<Integer> scanRange = general.add(new IntSetting.Builder()
        .name("scan-range").defaultValue(8).min(1).sliderMax(32).build());

    private final Setting<Integer> renderRange = general.add(new IntSetting.Builder()
        .name("render-range").defaultValue(8).min(1).sliderMax(32).build());

    private final Setting<Integer> sensitivity = general.add(new IntSetting.Builder()
        .name("sensitivity").defaultValue(60).min(0).sliderMax(100).build());

    private final Setting<Integer> minimumScore = general.add(new IntSetting.Builder()
        .name("minimum-score").defaultValue(20).min(1).sliderMax(150).build());

    private final Setting<Boolean> nearestFirst = general.add(new BoolSetting.Builder()
        .name("nearest-first").defaultValue(true).build());

    private final Setting<Boolean> directionPriority = general.add(new BoolSetting.Builder()
        .name("direction-priority").defaultValue(true).build());

    private final Setting<Boolean> detectAmethyst = detection.add(new BoolSetting.Builder()
        .name("amethyst").defaultValue(true).build());

    private final Setting<Boolean> detectBudding = detection.add(new BoolSetting.Builder()
        .name("budding-amethyst").defaultValue(true).build());

    private final Setting<Boolean> detectClusters = detection.add(new BoolSetting.Builder()
        .name("amethyst-clusters").defaultValue(true).build());

    private final Setting<Boolean> detectVines = detection.add(new BoolSetting.Builder()
        .name("cave-vines").defaultValue(true).build());

    private final Setting<Boolean> detectAmethystBlocks = detection.add(new BoolSetting.Builder()
        .name("amethyst-blocks").defaultValue(true).build());

    private final Setting<Integer> minY = detection.add(new IntSetting.Builder()
        .name("min-y").defaultValue(-64).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Integer> maxY = detection.add(new IntSetting.Builder()
        .name("max-y").defaultValue(96).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Boolean> clusterAnalysis = analysis.add(new BoolSetting.Builder()
        .name("cluster-analysis").defaultValue(true).build());

    private final Setting<Integer> clusterRadius = analysis.add(new IntSetting.Builder()
        .name("cluster-radius").defaultValue(2).min(1).sliderMax(6).build());

    private final Setting<Integer> minimumClusterSize = analysis.add(new IntSetting.Builder()
        .name("minimum-cluster-size").defaultValue(2).min(1).sliderMax(20).build());

    private final Setting<Boolean> densityAnalysis = analysis.add(new BoolSetting.Builder()
        .name("density-analysis").defaultValue(true).build());

    private final Setting<Integer> blocksPerTick = performance.add(new IntSetting.Builder()
        .name("blocks-per-tick").defaultValue(2500).min(100).sliderMax(10000).build());

    private final Setting<Integer> maxMilliseconds = performance.add(new IntSetting.Builder()
        .name("max-milliseconds").defaultValue(5).min(1).sliderMax(20).build());

    private final Setting<Boolean> adaptivePerformance = performance.add(new BoolSetting.Builder()
        .name("adaptive-performance").defaultValue(true).build());

    private final Setting<Integer> minimumFps = performance.add(new IntSetting.Builder()
        .name("minimum-fps").defaultValue(45).min(10).sliderMax(120).build());

    private final Setting<Integer> rescanDelay = performance.add(new IntSetting.Builder()
        .name("rescan-delay").defaultValue(200).min(20).sliderMax(2000).build());

    private final Setting<Integer> cacheSize = performance.add(new IntSetting.Builder()
        .name("cache-size").defaultValue(512).min(32).sliderMax(2048).build());

    private final Setting<ShapeMode> shapeMode = rendering.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());

    private final Setting<Boolean> renderAtPlayerY = rendering.add(new BoolSetting.Builder()
        .name("render-at-player-y").defaultValue(true).build());

    private final Setting<Integer> fixedY = rendering.add(new IntSetting.Builder()
        .name("fixed-y").defaultValue(0).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Integer> renderHeight = rendering.add(new IntSetting.Builder()
        .name("render-height").defaultValue(1).min(1).sliderMax(8).build());

    private final Setting<Boolean> renderOnlySuspicious = rendering.add(new BoolSetting.Builder()
        .name("render-only-suspicious").defaultValue(true).build());

    private final Setting<SettingColor> sideColor = rendering.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(255, 0, 0, 45)).build());

    private final Setting<SettingColor> lineColor = rendering.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(255, 0, 0, 255)).build());

    private final Setting<Boolean> debug = general.add(new BoolSetting.Builder()
        .name("debug").defaultValue(false).build());

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final Map<Long, ScanResult> results = new HashMap<>();
    private final Map<Long, Long> scanTimes = new HashMap<>();
    private final Queue<ScanTask> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();

    private ScanTask currentTask;
    private int currentIndex;
    private long lastQueueUpdate;

    public SusChunkFinder() {
        super(
            Categories.Misc,
            "sus-chunk-finder",
            "Finds suspicious chunks by analysing underground amethyst and cave-vine patterns."
        );
    }

    @Override
    public void onActivate() {
        clearRuntime();
        lastQueueUpdate = 0L;
    }

    @Override
    public void onDeactivate() {
        clearRuntime();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) {
            clearRuntime();
            return;
        }

        updateQueue();
        processScanner();
        trimCache();
    }

    private void updateQueue() {
        long now = System.currentTimeMillis();

        if (now - lastQueueUpdate < 250L) {
            return;
        }

        lastQueueUpdate = now;

        int centerX = mc.player.getChunkPos().x;
        int centerZ = mc.player.getChunkPos().z;

        int range = scanRange.get();

        List<ScanTask> candidates = new ArrayList<>();

        for (int x = centerX - range; x <= centerX + range; x++) {
            for (int z = centerZ - range; z <= centerZ + range; z++) {
                int dx = x - centerX;
                int dz = z - centerZ;

                if (Math.max(Math.abs(dx), Math.abs(dz)) > range) {
                    continue;
                }

                long key = ChunkPos.toLong(x, z);

                if (queued.contains(key)) {
                    continue;
                }

                if (currentTask != null && currentTask.key == key) {
                    continue;
                }

                Long previous = scanTimes.get(key);

                if (previous != null &&
                    now - previous < rescanDelay.get() * 50L) {
                    continue;
                }

                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x, z);

                if (chunk == null) {
                    continue;
                }

                double priority = Math.sqrt(dx * dx + dz * dz);

                if (directionPriority.get()) {
                    priority += directionPenalty(dx, dz);
                }

                candidates.add(new ScanTask(key, x, z, priority));
            }
        }

        if (nearestFirst.get()) {
            candidates.sort(Comparator.comparingDouble(task -> task.priority));
        }

        int amount = 32;

        for (ScanTask task : candidates) {
            if (amount-- <= 0) {
                break;
            }

            if (queued.add(task.key)) {
                queue.add(task);
            }
        }
    }

    private double directionPenalty(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return 0.0;
        }

        Vec3d look = mc.player.getRotationVec(1.0f);

        double length = Math.sqrt(dx * dx + dz * dz);

        double x = dx / length;
        double z = dz / length;

        double dot = look.x * x + look.z * z;

        return (1.0 - dot) * 3.0;
    }

    private void processScanner() {
        if (mc.world == null) {
            return;
        }

        long start = System.nanoTime();
        long limit = maxMilliseconds.get() * 1_000_000L;

        int budget = getEffectiveBudget();

        int processed = 0;

        while (processed < budget) {
            if (System.nanoTime() - start >= limit) {
                break;
            }

            if (!processOneBlock()) {
                break;
            }

            processed++;
        }
    }

    private int getEffectiveBudget() {
        int budget = blocksPerTick.get();

        if (!adaptivePerformance.get()) {
            return budget;
        }

        int fps = MinecraftClient.getCurrentFps();

        if (fps <= 0) {
            return budget;
        }

        if (fps < minimumFps.get()) {
            budget = Math.max(100, budget / 2);
        } else if (fps > minimumFps.get() + 20) {
            budget = Math.min(10000, (int) (budget * 1.25));
        }

        return budget;
    }

    private boolean processOneBlock() {
        if (currentTask == null) {
            currentTask = queue.poll();

            if (currentTask == null) {
                return false;
            }

            queued.remove(currentTask.key);

            currentIndex = 0;
            currentTask.result = new ScanResult(
                currentTask.chunkX,
                currentTask.chunkZ
            );
        }

        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(
            currentTask.chunkX,
            currentTask.chunkZ
        );

        if (chunk == null) {
            finishTask();
            return true;
        }

        int bottom = chunk.getBottomY();
        int top = bottom + chunk.getHeight() - 1;

        int startY = Math.max(bottom, minY.get());
        int endY = Math.min(top, maxY.get());

        if (endY < startY) {
            finishTask();
            return true;
        }

        int height = endY - startY + 1;
        int total = 256 * height;

        if (currentIndex >= total) {
            finishTask();
            return true;
        }

        int localX = currentIndex & 15;
        int localZ = (currentIndex >> 4) & 15;
        int yIndex = currentIndex >> 8;

        int worldX = currentTask.chunkX * 16 + localX;
        int worldZ = currentTask.chunkZ * 16 + localZ;
        int worldY = startY + yIndex;

        BlockPos pos = new BlockPos(worldX, worldY, worldZ);
        BlockState state = chunk.getBlockState(pos);

        analyseBlock(currentTask.result, pos, state);

        currentIndex++;

        return true;
    }

    private void analyseBlock(
        ScanResult result,
        BlockPos pos,
        BlockState state
    ) {
        Block block = state.getBlock();

        if (detectAmethyst.get() && isAmethyst(block)) {
            result.amethystCount++;
            result.targets.add(pos.toImmutable());

            if (detectBudding.get() && isBudding(block)) {
                result.buddingCount++;
            }

            if (detectClusters.get() && isCluster(block)) {
                result.clusterCount++;
            }

            if (block == Blocks.AMETHYST_BLOCK) {
                result.amethystBlockCount++;
            }
        }

        if (detectVines.get() && isCaveVine(block)) {
            result.caveVineCount++;
            result.targets.add(pos.toImmutable());
        }
    }

    private boolean isAmethyst(Block block) {
        if (block == Blocks.BUDDING_AMETHYST) {
            return true;
        }

        if (block == Blocks.AMETHYST_CLUSTER) {
            return true;
        }

        if (block == Blocks.SMALL_AMETHYST_BUD) {
            return true;
        }

        if (block == Blocks.MEDIUM_AMETHYST_BUD) {
            return true;
        }

        if (block == Blocks.LARGE_AMETHYST_BUD) {
            return true;
        }

        return detectAmethystBlocks.get() &&
            block == Blocks.AMETHYST_BLOCK;
    }

    private boolean isBudding(Block block) {
        return block == Blocks.BUDDING_AMETHYST;
    }

    private boolean isCluster(Block block) {
        return block == Blocks.AMETHYST_CLUSTER ||
            block == Blocks.SMALL_AMETHYST_BUD ||
            block == Blocks.MEDIUM_AMETHYST_BUD ||
            block == Blocks.LARGE_AMETHYST_BUD;
    }

    private boolean isCaveVine(Block block) {
        return block == Blocks.CAVE_VINES ||
            block == Blocks.CAVE_VINES_PLANT;
    }

    private void finishTask() {
        if (currentTask == null || currentTask.result == null) {
            currentTask = null;
            currentIndex = 0;
            return;
        }

        ScanResult result = currentTask.result;

        if (clusterAnalysis.get()) {
            analyseClusters(result);
        }

        if (densityAnalysis.get()) {
            analyseDensity(result);
        }

        result.score = calculateScore(result);

        results.put(result.key(), result);
        scanTimes.put(result.key(), System.currentTimeMillis());

        currentTask = null;
        currentIndex = 0;
    }

    private int calculateScore(ScanResult result) {
        int score = 0;

        score += Math.min(result.buddingCount * 12, 60);
        score += Math.min(result.clusterCount * 4, 40);
        score += Math.min(result.amethystCount * 2, 40);
        score += Math.min(result.amethystBlockCount * 2, 20);
        score += Math.min(result.caveVineCount * 2, 30);

        score += Math.min(result.clusterScore, 50);
        score += Math.min(result.densityScore, 30);

        if (result.largestCluster >= minimumClusterSize.get()) {
            score += Math.min(result.largestCluster * 3, 40);
        }

        double multiplier =
            0.50 +
            sensitivity.get() / 100.0;

        return Math.max(
            0,
            (int) (score * multiplier)
        );
    }

    private void analyseDensity(ScanResult result) {
        if (result.targets.isEmpty()) {
            result.densityScore = 0;
            return;
        }

        int targetCount = result.targets.size();

        result.density =
            targetCount / 4096.0;

        if (result.density > 0.002) {
            result.densityScore += 5;
        }

        if (result.density > 0.005) {
            result.densityScore += 5;
        }

        if (result.density > 0.01) {
            result.densityScore += 10;
        }

        if (result.density > 0.02) {
            result.densityScore += 10;
        }
    }

    private void analyseClusters(ScanResult result) {
        if (result.targets.isEmpty()) {
            return;
        }

        Set<Long> positions = new HashSet<>();

        for (BlockPos pos : result.targets) {
            positions.add(
                BlockPos.asLong(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ()
                )
            );
        }

        Set<Long> visited = new HashSet<>();

        int largest = 0;
        int clusterCount = 0;

        for (BlockPos start : result.targets) {
            long startKey = BlockPos.asLong(
                start.getX(),
                start.getY(),
                start.getZ()
            );

            if (visited.contains(startKey)) {
                continue;
            }

            ArrayDeque<BlockPos> open = new ArrayDeque<>();

            open.add(start);
            visited.add(startKey);

            int size = 0;

            while (!open.isEmpty()) {
                BlockPos current = open.poll();

                size++;

                for (int dx = -clusterRadius.get();
                     dx <= clusterRadius.get();
                     dx++) {

                    for (int dy = -clusterRadius.get();
                         dy <= clusterRadius.get();
                         dy++) {

                        for (int dz = -clusterRadius.get();
                             dz <= clusterRadius.get();
                             dz++) {

                            if (dx == 0 &&
                                dy == 0 &&
                                dz == 0) {
                                continue;
                            }

                            int distance =
                                Math.abs(dx) +
                                Math.abs(dy) +
                                Math.abs(dz);

                            if (distance > clusterRadius.get()) {
                                continue;
                            }

                            int nx = current.getX() + dx;
                            int ny = current.getY() + dy;
                            int nz = current.getZ() + dz;

                            long key = BlockPos.asLong(
                                nx,
                                ny,
                                nz
                            );

                            if (!positions.contains(key)) {
                                continue;
                            }

                            if (!visited.add(key)) {
                                continue;
                            }

                            open.add(
                                new BlockPos(nx, ny, nz)
                            );
                        }
                    }
                }
            }

            if (size >= minimumClusterSize.get()) {
                clusterCount++;
            }

            largest = Math.max(largest, size);

            if (size >= 2) {
                result.clusterScore += Math.min(size * 2, 20);
            }
        }

        result.largestCluster = largest;
        result.clusterCountDetected = clusterCount;
        result.clusterScore = Math.min(result.clusterScore, 50);
    }

    @Override
    public void render3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) {
            return;
        }

        int centerX = mc.player.getChunkPos().x;
        int centerZ = mc.player.getChunkPos().z;

        int y = renderAtPlayerY.get()
            ? mc.player.getBlockY()
            : fixedY.get();

        int range = renderRange.get();

        for (ScanResult result : results.values()) {
            int dx = result.chunkX - centerX;
            int dz = result.chunkZ - centerZ;

            if (Math.max(
                Math.abs(dx),
                Math.abs(dz)
            ) > range) {
                continue;
            }

            if (renderOnlySuspicious.get() &&
                result.score < minimumScore.get()) {
                continue;
            }

            int minX = result.chunkX * 16;
            int minZ = result.chunkZ * 16;

            int maxX = minX + 16;
            int maxZ = minZ + 16;

            event.renderer.box(
                minX,
                y,
                minZ,
                maxX,
                y + renderHeight.get(),
                maxZ,
                sideColor.get(),
                lineColor.get(),
                shapeMode.get()
            );
        }
    }

    private void trimCache() {
        int max = cacheSize.get();

        while (results.size() > max) {
            Long oldest = null;
            long oldestTime = Long.MAX_VALUE;

            for (Map.Entry<Long, Long> entry : scanTimes.entrySet()) {
                if (entry.getValue() < oldestTime) {
                    oldestTime = entry.getValue();
                    oldest = entry.getKey();
                }
            }

            if (oldest == null) {
                break;
            }

            results.remove(oldest);
            scanTimes.remove(oldest);
        }
    }

    private void clearRuntime() {
        queue.clear();
        queued.clear();
        results.clear();
        scanTimes.clear();

        currentTask = null;
        currentIndex = 0;
    }

    private static class ScanTask {
        final long key;
        final int chunkX;
        final int chunkZ;
        final double priority;

        ScanResult result;

        ScanTask(
            long key,
            int chunkX,
            int chunkZ,
            double priority
        ) {
            this.key = key;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.priority = priority;
        }
    }

    private static class ScanResult {
        final int chunkX;
        final int chunkZ;

        int score;

        int amethystCount;
        int buddingCount;
        int clusterCount;
        int caveVineCount;
        int amethystBlockCount;

        int clusterScore;
        int densityScore;

        int largestCluster;
        int clusterCountDetected;

        double density;

        final List<BlockPos> targets =
            new ArrayList<>();

        ScanResult(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        long key() {
            return ChunkPos.toLong(
                chunkX,
                chunkZ
            );
        }
    }
}
