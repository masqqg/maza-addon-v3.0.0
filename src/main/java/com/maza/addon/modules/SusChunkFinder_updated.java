package com.maza.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
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
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
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

    // Sensitivity is user-adjustable from 1 to 20.
    private final Setting<Integer> sensitivity = general.add(new IntSetting.Builder()
        .name("sensitivity").defaultValue(20).min(1).max(20).sliderMin(1).sliderMax(20).build());

    private static final int MINIMUM_SCORE = 20;

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

    private final Setting<Boolean> detectBeeNest = detection.add(new BoolSetting.Builder()
        .name("bee-nest").defaultValue(true).build());

    private final Setting<Boolean> detectDeepslate = detection.add(new BoolSetting.Builder()
        .name("deepslate").defaultValue(true).build());

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

    private final Setting<Boolean> lightAnalysis = analysis.add(new BoolSetting.Builder()
        .name("light-analysis").defaultValue(true).build());

    private final Setting<Integer> rescanDelay = performance.add(new IntSetting.Builder()
        .name("rescan-delay").defaultValue(200).min(20).sliderMax(2000).build());

    private final Setting<Integer> cacheSize = performance.add(new IntSetting.Builder()
        .name("cache-size").defaultValue(512).min(32).sliderMax(2048).build());

    private final Setting<Boolean> renderAtPlayerY = rendering.add(new BoolSetting.Builder()
        .name("render-at-player-y").defaultValue(true).build());

    private final Setting<Integer> fixedY = rendering.add(new IntSetting.Builder()
        .name("fixed-y").defaultValue(0).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Integer> renderHeight = rendering.add(new IntSetting.Builder()
        .name("render-height").defaultValue(1).min(1).sliderMax(8).build());

    private final Setting<Boolean> renderOnlySuspicious = rendering.add(new BoolSetting.Builder()
        .name("render-only-suspicious").defaultValue(true).build());

    // Clean outline: red for normal findings, white for player-light findings.
    private final SettingColor redLine = new SettingColor(255, 0, 0, 255);
    private final SettingColor whiteLine = new SettingColor(255, 255, 255, 255);

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
            MazaCategory.INSTANCE,
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

        // Queue every currently loaded chunk immediately. The scanner processes the
        // nearest queued chunk as a complete chunk on the next tick.
        for (ScanTask task : candidates) {
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
        if (mc.world == null) return;

        // No FPS/adaptive throttling. A loaded chunk is scanned completely as soon
        // as it reaches the front of the queue. Exceptions are contained below.
        try {
            if (currentTask == null) {
                currentTask = queue.poll();
                if (currentTask == null) return;
                queued.remove(currentTask.key);
                currentIndex = 0;
                currentTask.result = new ScanResult(currentTask.chunkX, currentTask.chunkZ);
            }

            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(
                currentTask.chunkX, currentTask.chunkZ
            );

            if (chunk == null) {
                finishTask();
                return;
            }

            int bottom = chunk.getBottomY();
            int top = bottom + chunk.getHeight() - 1;
            int startY = Math.max(bottom, minY.get());
            int endY = Math.min(top, maxY.get());

            if (endY < startY) {
                finishTask();
                return;
            }

            int height = endY - startY + 1;
            int total = 256 * height;

            while (currentIndex < total) {
                int localX = currentIndex & 15;
                int localZ = (currentIndex >> 4) & 15;
                int yIndex = currentIndex >> 8;

                BlockPos pos = new BlockPos(
                    currentTask.chunkX * 16 + localX,
                    startY + yIndex,
                    currentTask.chunkZ * 16 + localZ
                );

                try {
                    analyseBlock(currentTask.result, pos, chunk.getBlockState(pos));
                } catch (Throwable ignored) {
                    // Skip a bad block without stopping the scan.
                }

                currentIndex++;
            }

            finishTask();
        } catch (Throwable ignored) {
            // A chunk can unload/change while being scanned. Drop only that task.
            currentTask = null;
            currentIndex = 0;
        }
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

        // Light is sampled only around useful signals so the scanner stays fast.
        if (lightAnalysis.get() && isUsefulSignal(block)) {
            analyseLight(result, pos);
        }

        // IMPORTANT: a plain amethyst block is NOT treated as suspicious by itself.
        // The useful signal is the growth attached to budding amethyst.
        if (detectBudding.get() && block == Blocks.BUDDING_AMETHYST) {
            result.buddingCount++;
            result.growthBaseCount++;

            int localGrowth = 0;
            for (Direction direction : Direction.values()) {
                Block adjacent = mc.world.getBlockState(pos.offset(direction)).getBlock();
                if (isGrowthStage(adjacent)) {
                    localGrowth++;
                    result.growthCount++;
                    result.targets.add(pos.offset(direction).toImmutable());
                }
            }

            // A budding block with mature growth is much stronger evidence than
            // the surrounding geode blocks.
            if (localGrowth >= 1) result.growthScore += 8;
            if (localGrowth >= 2) result.growthScore += 12;
            if (localGrowth >= 3) result.growthScore += 18;
            if (localGrowth >= 4) result.growthScore += 25;
            if (localGrowth >= 5) result.growthScore += 32;
            if (localGrowth >= 6) result.growthScore += 40;
        }

        if (detectClusters.get() && isCluster(block)) {
            result.clusterCount++;
            if (isMatureGrowth(block)) result.matureGrowthCount++;
        }

        if (detectVines.get() && isCaveVine(block)) {
            result.caveVineCount++;
            result.secondarySignalCount++;
            result.targets.add(pos.toImmutable());
        }

        if (detectBeeNest.get() && block == Blocks.BEE_NEST) {
            result.beeNestCount++;
            result.secondarySignalCount++;
            result.targets.add(pos.toImmutable());
        }

        if (detectDeepslate.get() && isDeepslateFamily(block)) {
            result.deepslateCount++;
            result.secondarySignalCount++;
        }

        // Keep amethyst blocks available as secondary context, but do not make
        // ordinary AMETHYST_BLOCKs alone trigger a suspicious chunk.
        if (detectAmethystBlocks.get() && block == Blocks.AMETHYST_BLOCK) {
            result.amethystBlockCount++;
        }

        if (detectAmethyst.get() && isAmethyst(block)) {
            result.amethystCount++;
        }
    }

    private boolean isUsefulSignal(Block block) {
        return block == Blocks.BUDDING_AMETHYST
            || isGrowthStage(block)
            || isCaveVine(block)
            || block == Blocks.BEE_NEST
            || isDeepslateFamily(block);
    }

    private void analyseLight(ScanResult result, BlockPos pos) {
        if (mc.world == null) return;

        int total = mc.world.getLightLevel(pos);
        int blockLight = mc.world.getLightLevel(LightType.BLOCK, pos);
        int skyLight = mc.world.getLightLevel(LightType.SKY, pos);

        result.lightSamples++;
        result.minTotalLight = Math.min(result.minTotalLight, total);
        result.maxBlockLight = Math.max(result.maxBlockLight, blockLight);
        result.maxSkyLight = Math.max(result.maxSkyLight, skyLight);

        // Darkness is contextual evidence only.
        if (total <= 4) {
            result.darkLightCount++;
            result.lightScore += 2;
        }

        // Artificial/local light in a low-sky-light area is a stronger signal.
        if (blockLight >= 7 && skyLight <= 4) {
            result.localLightCount++;
            result.lightScore += 3;
        }
    }

    private void analysePlayerLight(ScanResult result) {
        if (!lightAnalysis.get() || mc.player == null || mc.world == null) return;

        // Player-light data is attached only to the chunk the player is actually in.
        // This prevents one player's light level from turning every scanned chunk white.
        if (result.chunkX != mc.player.getChunkPos().x || result.chunkZ != mc.player.getChunkPos().z) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int total = mc.world.getLightLevel(playerPos);
        int blockLight = mc.world.getLightLevel(LightType.BLOCK, playerPos);
        int skyLight = mc.world.getLightLevel(LightType.SKY, playerPos);

        result.playerTotalLight = total;
        result.playerBlockLight = blockLight;
        result.playerSkyLight = skyLight;

        if (total <= 4 || (blockLight >= 7 && skyLight <= 4)) {
            result.playerLightSignal = true;
            result.lightScore += 3;
        }
    }

    private boolean isGrowthStage(Block block) {
        return block == Blocks.SMALL_AMETHYST_BUD
            || block == Blocks.MEDIUM_AMETHYST_BUD
            || block == Blocks.LARGE_AMETHYST_BUD
            || block == Blocks.AMETHYST_CLUSTER;
    }

    private boolean isMatureGrowth(Block block) {
        return block == Blocks.LARGE_AMETHYST_BUD
            || block == Blocks.AMETHYST_CLUSTER;
    }

    private boolean isDeepslateFamily(Block block) {
        if (block == Blocks.DEEPSLATE
            || block == Blocks.COBBLED_DEEPSLATE
            || block == Blocks.POLISHED_DEEPSLATE
            || block == Blocks.DEEPSLATE_BRICKS
            || block == Blocks.DEEPSLATE_TILES
            || block == Blocks.CHISELED_DEEPSLATE) {
            return true;
        }

        // Supports Donut/custom blocks without hard-linking the addon to a
        // non-vanilla block class.
        String id = Registries.BLOCK.getId(block).getPath();
        return id.contains("rotated_deepslate") || id.contains("rotated-deepslate");
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

        analysePlayerLight(result);
        result.score = calculateScore(result);

        results.put(result.key(), result);
        scanTimes.put(result.key(), System.currentTimeMillis());

        currentTask = null;
        currentIndex = 0;
    }

    private int calculateScore(ScanResult result) {
        // Primary signal: actual growth on budding amethyst.
        int score = result.growthScore;
        score += Math.min(result.matureGrowthCount * 5, 40);
        score += Math.min(result.growthCount * 2, 30);

        // Secondary environmental signals only add a little; they can never make
        // a normal amethyst geode suspicious on their own.
        score += Math.min(result.caveVineCount, 10);
        score += Math.min(result.beeNestCount * 3, 12);
        score += Math.min(result.secondarySignalCount / 8, 8);

        if (result.growthBaseCount >= 2) score += 8;
        if (result.growthBaseCount >= 4) score += 12;
        if (result.growthBaseCount >= 8) score += 20;

        if (clusterAnalysis.get()) score += Math.min(result.clusterScore, 20);
        if (densityAnalysis.get()) score += Math.min(result.densityScore, 10);
        if (lightAnalysis.get()) score += Math.min(result.lightScore, 12);

        double multiplier = 0.50 + sensitivity.get() / 100.0;
        return Math.max(0, (int) (score * multiplier));
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

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        int centerX = mc.player.getChunkPos().x;
        int centerZ = mc.player.getChunkPos().z;
        int y = renderAtPlayerY.get() ? mc.player.getBlockY() : fixedY.get();
        int range = renderRange.get();

        Set<Long> suspicious = new HashSet<>();
        for (ScanResult result : results.values()) {
            if (renderOnlySuspicious.get() && result.score < MINIMUM_SCORE) continue;
            int dx = result.chunkX - centerX;
            int dz = result.chunkZ - centerZ;
            if (Math.max(Math.abs(dx), Math.abs(dz)) > range) continue;
            suspicious.add(ChunkPos.toLong(result.chunkX, result.chunkZ));
        }

        // Each detected cell is rendered as a 32x32 area. Adjacent cells are
        // merged so there are no doubled internal borders or overlapping boxes.
        Set<Long> used = new HashSet<>();
        for (Long key : suspicious) {
            if (used.contains(key)) continue;

            ChunkPos packed = new ChunkPos(key);
            int cx = packed.x;
            int cz = packed.z;

            int width = 1;
            while (suspicious.contains(ChunkPos.toLong(cx + width, cz))) {
                width++;
            }

            int height = 1;
            boolean canGrow = true;
            while (canGrow) {
                int rowZ = cz + height;
                for (int x = 0; x < width; x++) {
                    if (!suspicious.contains(ChunkPos.toLong(cx + x, rowZ))) {
                        canGrow = false;
                        break;
                    }
                }
                if (canGrow) height++;
            }

            for (int x = 0; x < width; x++) {
                for (int z = 0; z < height; z++) {
                    used.add(ChunkPos.toLong(cx + x, cz + z));
                }
            }

            boolean playerLight = false;
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < height; z++) {
                    ScanResult merged = results.get(ChunkPos.toLong(cx + x, cz + z));
                    if (merged != null && merged.playerLightSignal) {
                        playerLight = true;
                    }
                }
            }

            // One suspicious chunk gets a 32x32 marker centered on the chunk.
            // Adjacent suspicious chunks are merged into one larger rectangle.
            int minX = cx * 16 - 8;
            int minZ = cz * 16 - 8;
            int maxX = (cx + width) * 16 + 8;
            int maxZ = (cz + height) * 16 + 8;

            event.renderer.box(
                minX, y, minZ, maxX, y + renderHeight.get(), maxZ,
                playerLight ? whiteLine : redLine,
                playerLight ? whiteLine : redLine,
                meteordevelopment.meteorclient.renderer.ShapeMode.Lines,
                0
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
        int growthBaseCount;
        int growthCount;
        int matureGrowthCount;
        int growthScore;
        int beeNestCount;
        int deepslateCount;
        int secondarySignalCount;

        int lightSamples;
        int darkLightCount;
        int localLightCount;
        int lightScore;
        int minTotalLight = 15;
        int maxBlockLight;
        int maxSkyLight;
        int playerTotalLight;
        int playerBlockLight;
        int playerSkyLight;
        boolean playerLightSignal;

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
