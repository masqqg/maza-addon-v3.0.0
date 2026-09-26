package com.maza.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
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
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDetection = settings.createGroup("Detection");
    private final SettingGroup sgPerformance = settings.createGroup("Performance");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> renderRange = sgGeneral.add(new IntSetting.Builder()
        .name("render-range").description("How many chunks around the player are rendered.")
        .defaultValue(8).min(1).sliderMax(32).build());

    private final Setting<Integer> minimumScore = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-score").description("Minimum score required for a chunk to be marked.")
        .defaultValue(10).min(1).sliderMax(100).build());

    private final Setting<Integer> sensitivity = sgGeneral.add(new IntSetting.Builder()
        .name("sensitivity").description("Detection sensitivity.")
        .defaultValue(60).min(0).sliderMax(100).build());

    private final Setting<Boolean> nearestFirst = sgGeneral.add(new BoolSetting.Builder()
        .name("nearest-first").description("Scan chunks closest to the player first.")
        .defaultValue(true).build());

    private final Setting<Boolean> directionPriority = sgGeneral.add(new BoolSetting.Builder()
        .name("direction-priority").description("Prioritize chunks in the direction you are looking.")
        .defaultValue(true).build());

    private final Setting<Boolean> detectAmethyst = sgDetection.add(new BoolSetting.Builder()
        .name("amethyst").description("Detect amethyst blocks.").defaultValue(true).build());

    private final Setting<Boolean> detectBudding = sgDetection.add(new BoolSetting.Builder()
        .name("budding-amethyst").description("Give extra score to budding amethyst.")
        .defaultValue(true).build());

    private final Setting<Boolean> detectClusters = sgDetection.add(new BoolSetting.Builder()
        .name("amethyst-clusters").description("Detect amethyst buds and clusters.")
        .defaultValue(true).build());

    private final Setting<Boolean> detectCaveVines = sgDetection.add(new BoolSetting.Builder()
        .name("cave-vines").description("Detect cave vines.").defaultValue(true).build());

    private final Setting<Boolean> detectDenseAreas = sgDetection.add(new BoolSetting.Builder()
        .name("dense-areas").description("Give extra score to dense target areas.")
        .defaultValue(true).build());

    private final Setting<Integer> scanMinY = sgDetection.add(new IntSetting.Builder()
        .name("scan-min-y").description("Minimum Y level to scan.")
        .defaultValue(-64).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Integer> scanMaxY = sgDetection.add(new IntSetting.Builder()
        .name("scan-max-y").description("Maximum Y level to scan.")
        .defaultValue(64).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Integer> blocksPerTick = sgPerformance.add(new IntSetting.Builder()
        .name("blocks-per-tick").description("Maximum blocks processed each tick.")
        .defaultValue(1800).min(100).sliderMax(10000).build());

    private final Setting<Integer> maxScanMillis = sgPerformance.add(new IntSetting.Builder()
        .name("max-scan-time-ms").description("Maximum scan time per tick.")
        .defaultValue(4).min(1).sliderMax(15).build());

    private final Setting<Integer> rescanDelay = sgPerformance.add(new IntSetting.Builder()
        .name("rescan-delay").description("Ticks before a scanned chunk can be scanned again.")
        .defaultValue(200).min(20).sliderMax(2000).build());

    private final Setting<Integer> cacheSize = sgPerformance.add(new IntSetting.Builder()
        .name("cache-size").description("Maximum chunk results kept in memory.")
        .defaultValue(512).min(32).sliderMax(2048).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").description("How suspicious chunks are rendered.")
        .defaultValue(ShapeMode.Both).build());

    private final Setting<Integer> renderHeight = sgRender.add(new IntSetting.Builder()
        .name("render-height").description("Vertical height of the chunk marker.")
        .defaultValue(1).min(1).sliderMax(8).build());

    private final Setting<Boolean> renderAtPlayerY = sgRender.add(new BoolSetting.Builder()
        .name("render-at-player-y").description("Render at your current Y level.")
        .defaultValue(true).build());

    private final Setting<Integer> fixedY = sgRender.add(new IntSetting.Builder()
        .name("fixed-y").description("Y level used when player-Y rendering is disabled.")
        .defaultValue(0).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").description("Chunk fill color.")
        .defaultValue(new SettingColor(255, 0, 0, 45)).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").description("Chunk outline color.")
        .defaultValue(new SettingColor(255, 0, 0, 255)).build());

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Map<Long, ScanData> results = new HashMap<>();
    private final Map<Long, Long> scanTimes = new HashMap<>();
    private final Queue<ChunkTask> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();

    private ChunkTask currentTask;
    private int currentIndex;
    private long lastQueueUpdate;

    public SusChunkFinder() {
        super(Categories.Misc, "sus-chunk-finder",
            "Detects suspicious chunks using amethyst and cave-vine patterns.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) {
            clearRuntime();
            return;
        }

        updateQueue();

        long start = System.nanoTime();
        long timeLimit = maxScanMillis.get() * 1_000_000L;
        int processed = 0;

        while (processed < blocksPerTick.get() && System.nanoTime() - start < timeLimit) {
            if (!processNextBlock()) break;
            processed++;
        }

        trimCache();
    }

    private void updateQueue() {
        long now = System.currentTimeMillis();
        if (now - lastQueueUpdate < 250) return;
        lastQueueUpdate = now;

        int px = mc.player.getChunkPos().x;
        int pz = mc.player.getChunkPos().z;
        int range = renderRange.get() + 2;
        List<ChunkTask> candidates = new ArrayList<>();

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) > range) continue;

                int x = px + dx;
                int z = pz + dz;
                long key = ChunkPos.toLong(x, z);

                if (queued.contains(key)) continue;
                if (currentTask != null && currentTask.key == key) continue;

                Long lastScan = scanTimes.get(key);
                if (lastScan != null && now - lastScan < rescanDelay.get() * 50L) continue;

                if (mc.world.getChunkManager().getWorldChunk(x, z) == null) continue;

                double distance = Math.sqrt(dx * dx + dz * dz);
                double priority = distance;
                if (directionPriority.get()) priority += directionPenalty(dx, dz);

                candidates.add(new ChunkTask(key, x, z, priority));
            }
        }

        if (nearestFirst.get()) {
            candidates.sort(Comparator.comparingDouble(task -> task.priority));
        }

        int maxAdd = 24;
        for (ChunkTask task : candidates) {
            if (maxAdd-- <= 0) break;
            if (queued.add(task.key)) queue.add(task);
        }
    }

    private double directionPenalty(int dx, int dz) {
        if (dx == 0 && dz == 0) return 0;

        Vec3d look = mc.player.getRotationVec(1.0f);
        double len = Math.sqrt(dx * dx + dz * dz);
        double nx = dx / len;
        double nz = dz / len;
        double dot = look.x * nx + look.z * nz;
        return (1.0 - dot) * 3.0;
    }

    private boolean processNextBlock() {
        if (mc.world == null) return false;

        if (currentTask == null) {
            currentTask = queue.poll();
            if (currentTask == null) return false;
            queued.remove(currentTask.key);
            currentIndex = 0;
            currentTask.data = new ScanData(currentTask.chunkX, currentTask.chunkZ);
        }

        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(
            currentTask.chunkX, currentTask.chunkZ);

        if (chunk == null) {
            finishCurrentTask();
            return true;
        }

        int minY = Math.max(chunk.getBottomY(), scanMinY.get());
        int maxY = Math.min(chunk.getBottomY() + chunk.getHeight() - 1, scanMaxY.get());

        if (maxY < minY) {
            finishCurrentTask();
            return true;
        }

        int totalY = maxY - minY + 1;
        int totalBlocks = 16 * 16 * totalY;

        if (currentIndex >= totalBlocks) {
            finishCurrentTask();
            return true;
        }

        int localX = currentIndex & 15;
        int localZ = (currentIndex >> 4) & 15;
        int yIndex = currentIndex >> 8;

        int worldX = currentTask.chunkX * 16 + localX;
        int worldZ = currentTask.chunkZ * 16 + localZ;
        int worldY = minY + yIndex;

        BlockPos pos = new BlockPos(worldX, worldY, worldZ);
        checkBlock(currentTask.data, pos, chunk.getBlockState(pos));
        currentIndex++;
        return true;
    }

    private void checkBlock(ScanData data, BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        if (detectAmethyst.get() && isAmethyst(block)) {
            data.amethystCount++;
            data.targets.add(pos.toImmutable());

            if (detectBudding.get() && isBudding(block)) data.buddingCount++;
            if (detectClusters.get() && isCluster(block)) data.clusterCount++;
        }

        if (detectCaveVines.get() && isCaveVine(block)) {
            data.caveVineCount++;
            data.targets.add(pos.toImmutable());
        }
    }

    private boolean isAmethyst(Block block) {
        return block == Blocks.AMETHYST_BLOCK ||
            block == Blocks.BUDDING_AMETHYST ||
            block == Blocks.AMETHYST_CLUSTER ||
            block == Blocks.SMALL_AMETHYST_BUD ||
            block == Blocks.MEDIUM_AMETHYST_BUD ||
            block == Blocks.LARGE_AMETHYST_BUD;
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
        return block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT;
    }

    private void finishCurrentTask() {
        if (currentTask == null || currentTask.data == null) {
            currentTask = null;
            currentIndex = 0;
            return;
        }

        ScanData data = currentTask.data;
        data.clusterScore = calculateClusterScore(data);
        data.score = calculateScore(data);

        results.put(currentTask.key, data);
        scanTimes.put(currentTask.key, System.currentTimeMillis());

        currentTask = null;
        currentIndex = 0;
    }

    private int calculateScore(ScanData data) {
        int score = 0;

        score += Math.min(data.buddingCount * 8, 40);
        score += Math.min(data.amethystCount * 2, 30);
        score += Math.min(data.clusterCount * 2, 25);
        score += Math.min(data.caveVineCount, 15);

        if (detectDenseAreas.get()) score += data.clusterScore;

        double multiplier = 0.60 + (sensitivity.get() / 100.0) * 0.60;
        return (int) (score * multiplier);
    }

    private int calculateClusterScore(ScanData data) {
        if (data.targets.isEmpty()) return 0;

        int nearbyPairs = 0;
        Set<Long> positions = new HashSet<>();

        for (BlockPos pos : data.targets) {
            positions.add(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
        }

        for (BlockPos pos : data.targets) {
            for (Direction direction : Direction.values()) {
                int x = pos.getX() + direction.getOffsetX();
                int y = pos.getY() + direction.getOffsetY();
                int z = pos.getZ() + direction.getOffsetZ();

                if (positions.contains(BlockPos.asLong(x, y, z))) nearbyPairs++;
            }
        }

        nearbyPairs /= 2;
        return Math.min(nearbyPairs * 2, 25);
    }

    @Override
    public void render3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        int playerChunkX = mc.player.getChunkPos().x;
        int playerChunkZ = mc.player.getChunkPos().z;
        int range = renderRange.get();
        int renderY = renderAtPlayerY.get() ? mc.player.getBlockY() : fixedY.get();

        for (ScanData data : results.values()) {
            if (data.score < minimumScore.get()) continue;

            int dx = data.chunkX - playerChunkX;
            int dz = data.chunkZ - playerChunkZ;

            if (Math.max(Math.abs(dx), Math.abs(dz)) > range) continue;

            int minX = data.chunkX * 16;
            int minZ = data.chunkZ * 16;
            int maxX = minX + 16;
            int maxZ = minZ + 16;

            event.renderer.box(
                minX, renderY, minZ,
                maxX, renderY + renderHeight.get(), maxZ,
                sideColor.get(), lineColor.get(), shapeMode.get()
            );
        }
    }

    private void trimCache() {
        int max = cacheSize.get();
        if (results.size() <= max) return;

        while (results.size() > max) {
            Long oldestKey = null;
            long oldestTime = Long.MAX_VALUE;

            for (Map.Entry<Long, Long> entry : scanTimes.entrySet()) {
                if (entry.getValue() < oldestTime) {
                    oldestTime = entry.getValue();
                    oldestKey = entry.getKey();
                }
            }

            if (oldestKey == null) break;
            results.remove(oldestKey);
            scanTimes.remove(oldestKey);
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

    @Override
    public void onActivate() {
        clearRuntime();
        lastQueueUpdate = 0;
    }

    @Override
    public void onDeactivate() {
        clearRuntime();
    }

    private static class ChunkTask {
        final long key;
        final int chunkX;
        final int chunkZ;
        final double priority;
        ScanData data;

        ChunkTask(long key, int chunkX, int chunkZ, double priority) {
            this.key = key;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.priority = priority;
        }
    }

    private static class ScanData {
        final int chunkX;
        final int chunkZ;
        int score;
        int amethystCount;
        int buddingCount;
        int clusterCount;
        int caveVineCount;
        int clusterScore;
        final List<BlockPos> targets = new ArrayList<>();

        ScanData(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }
}
