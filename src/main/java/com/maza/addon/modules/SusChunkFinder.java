package com.maza.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import com.maza.addon.MazaCategory;

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
import net.minecraft.world.chunk.ChunkSection;

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

    // Sensitivity is the threshold for REAL evidence.
    // 1 is intentionally useful: a single budding-amethyst/geode-growth signal
    // can mark a chunk, but plain deepslate/amethyst blocks cannot.
    private final Setting<Integer> sensitivity = general.add(new IntSetting.Builder()
        .name("sensitivity").defaultValue(1).min(1).max(20).sliderMin(1).sliderMax(20).build());

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

    // LARP v9 Sus Chunk V4 also uses calcite as local geode context.
    private final Setting<Boolean> detectCalcite = detection.add(new BoolSetting.Builder()
        .name("calcite-context").defaultValue(true).build());

    private final Setting<Boolean> detectBeeNest = detection.add(new BoolSetting.Builder()
        .name("bee-nest").defaultValue(true).build());

    private final Setting<Boolean> detectDeepslate = detection.add(new BoolSetting.Builder()
        .name("deepslate").defaultValue(true).build());

    private final Setting<Boolean> detectDripstone = detection.add(new BoolSetting.Builder()
        .name("dripstone").defaultValue(true).build());

    private final Setting<Boolean> requireAmethystGrowth = detection.add(new BoolSetting.Builder()
        .name("require-amethyst-growth").defaultValue(true).build());

    private final Setting<Integer> minY = detection.add(new IntSetting.Builder()
        .name("min-y").defaultValue(-64).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Integer> maxY = detection.add(new IntSetting.Builder()
        .name("max-y").defaultValue(320).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

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

    // LARP-style chunk marker: low translucent fill + a thin border.
    private final SettingColor redFill = new SettingColor(255, 0, 0, 55);
    private final SettingColor redLine = new SettingColor(255, 0, 0, 150);

    private final Setting<Boolean> debug = general.add(new BoolSetting.Builder()
        .name("debug").defaultValue(false).build());

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final Map<Long, ScanResult> results = new HashMap<>();
    private final Map<Long, Long> scanTimes = new HashMap<>();
    private final ArrayDeque<ScanTask> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    // Event-driven dirty chunks: LARP-style behavior without depending on relogging.
    private final ArrayDeque<Long> dirtyQueue = new ArrayDeque<>();
    private final Set<Long> dirtySet = new HashSet<>();

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

        // Immediately adopt chunks that are already loaded when the module is enabled.
        if (mc.world != null && mc.player != null) {
            enqueueLoadedChunksAroundPlayer(true);
        }
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

        // First consume chunks announced/changed by the client. This is the important
        // part that makes newly received amethyst visible without a relog.
        promoteDirtyChunks();
        updateQueue();
        processScanner();
        trimCache();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null || mc.player == null || event.chunk() == null) return;

        WorldChunk chunk = event.chunk();
        ChunkPos pos = chunk.getPos();
        if (Math.max(Math.abs(pos.x - mc.player.getChunkPos().x),
            Math.abs(pos.z - mc.player.getChunkPos().z)) > scanRange.get()) return;

        // ChunkDataEvent fires when the client actually receives the chunk. Queue it
        // immediately instead of waiting for the periodic scanner/rescan delay.
        enqueueDirty(pos.x, pos.z, true);
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null || mc.player == null || event.pos == null) return;

        BlockPos pos = event.pos;
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;

        boolean relevant = isAmethyst(event.oldState.getBlock())
            || isAmethyst(event.newState.getBlock())
            || isUsefulSignal(event.oldState.getBlock())
            || isUsefulSignal(event.newState.getBlock());

        if (!relevant) return;

        // A growth block can affect the score of its own chunk and a neighboring
        // chunk at a boundary, so dirty a compact 3x3 neighborhood.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                enqueueDirty(cx + dx, cz + dz, true);
            }
        }
    }

    private void enqueueLoadedChunksAroundPlayer(boolean force) {
        if (mc.world == null || mc.player == null) return;
        int cx = mc.player.getChunkPos().x;
        int cz = mc.player.getChunkPos().z;
        int range = scanRange.get();

        for (int x = cx - range; x <= cx + range; x++) {
            for (int z = cz - range; z <= cz + range; z++) {
                if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) > range) continue;
                if (mc.world.getChunkManager().getWorldChunk(x, z) != null) {
                    enqueueDirty(x, z, force);
                }
            }
        }
    }

    private void enqueueDirty(int x, int z, boolean force) {
        if (mc.world == null || mc.player == null) return;
        int cx = mc.player.getChunkPos().x;
        int cz = mc.player.getChunkPos().z;
        if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) > scanRange.get()) return;
        if (mc.world.getChunkManager().getWorldChunk(x, z) == null) return;

        long key = ChunkPos.toLong(x, z);
        if (!force && scanTimes.containsKey(key)) return;

        // Remove stale result so the renderer cannot show an old state while the
        // freshly received chunk is being rescanned.
        results.remove(key);
        scanTimes.remove(key);

        if (dirtySet.add(key)) dirtyQueue.addLast(key);
    }

    private void promoteDirtyChunks() {
        while (!dirtyQueue.isEmpty()) {
            long key = dirtyQueue.removeFirst();
            dirtySet.remove(key);

            ChunkPos cp = new ChunkPos(key);
            if (mc.world == null || mc.world.getChunkManager().getWorldChunk(cp.x, cp.z) == null) continue;
            if (currentTask != null && currentTask.key == key) continue;
            if (queued.add(key)) {
                queue.addFirst(new ScanTask(key, cp.x, cp.z, -100000.0));
            }
        }
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

                // Normal rescans are event-driven. This avoids repeatedly walking
                // every loaded chunk and lets ChunkDataEvent/BlockUpdateEvent be the
                // source of fresh data, as in the reference implementation.
                if (scanTimes.containsKey(key)) continue;

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

            // LARP-style scanning: inspect the chunk's actual section palettes instead
            // of repeatedly asking the world for every block. This sees block states
            // already present in a freshly received chunk immediately, including buds
            // and clusters, without requiring a relog.
            scanChunkSections(chunk, currentTask.result);
            finishTask();
        } catch (Throwable ignored) {
            currentTask = null;
            currentIndex = 0;
        }
    }

    private void scanChunkSections(WorldChunk chunk, ScanResult result) {
        ChunkSection[] sections = chunk.getSectionArray();
        if (sections == null) return;

        int chunkBottom = chunk.getBottomY();
        int min = minY.get();
        int max = maxY.get();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section == null || section.isEmpty()) continue;

            int sectionBottom = chunkBottom + sectionIndex * 16;
            int sectionTop = sectionBottom + 15;
            if (sectionTop < min || sectionBottom > max) continue;

            // Fast palette-level rejection, similar to the reference implementation.
            if (!section.hasAny(this::isScanCandidateState)) continue;

            int localMinY = Math.max(0, min - sectionBottom);
            int localMaxY = Math.min(15, max - sectionBottom);

            for (int localY = localMinY; localY <= localMaxY; localY++) {
                int worldY = sectionBottom + localY;

                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        BlockState state = section.getBlockState(localX, localY, localZ);
                        if (!isScanCandidateState(state)) continue;

                        BlockPos pos = new BlockPos(
                            chunk.getPos().getStartX() + localX,
                            worldY,
                            chunk.getPos().getStartZ() + localZ
                        );

                        analyseBlock(result, pos, state);
                    }
                }
            }
        }
    }

    private boolean isScanCandidateState(BlockState state) {
        Block block = state.getBlock();
        return isAmethyst(block)
            || (detectCalcite.get() && block == Blocks.CALCITE)
            || isCaveVine(block)
            || block == Blocks.BEE_NEST
            || isDeepslateFamily(block)
            || isDripstone(block);
    }

    private void analyseBlock(
        ScanResult result,
        BlockPos pos,
        BlockState state
    ) {
        Block block = state.getBlock();

        // Match the reference V4 behaviour: section data is scanned directly, so
        // amethyst that is loaded in the client chunk can be detected even when it
        // is underground and not currently visible on screen.
        if (lightAnalysis.get() && isUsefulSignal(block)) {
            analyseLight(result, pos);
        }

        if (detectCalcite.get() && block == Blocks.CALCITE) {
            result.calciteCount++;
        }

        if (detectBudding.get() && block == Blocks.BUDDING_AMETHYST) {
            result.buddingCount++;
            result.growthBaseCount++;

            int localGrowth = 0;
            for (Direction direction : Direction.values()) {
                BlockPos adjacentPos = pos.offset(direction);
                Block adjacent = mc.world.getBlockState(adjacentPos).getBlock();

                if (isGrowthStage(adjacent)) {
                    localGrowth++;
                    result.growthCount++;
                    result.targets.add(adjacentPos.toImmutable());
                }
            }

            // V4-style local geode context: calcite close to budding amethyst
            // strengthens the signal, but is never enough by itself.
            if (detectCalcite.get() && nearCalcite(pos, 4)) {
                result.nearCalciteCount++;
            }

            result.growthScore += localGrowth * localGrowth * 2;
            if (localGrowth >= 1) result.growthScore += 3;
            if (localGrowth >= 2) result.growthScore += 5;
            if (localGrowth >= 3) result.growthScore += 7;
            if (localGrowth >= 5) result.growthScore += 10;
        }

        // IMPORTANT: Chunk Finder V4 can identify a mature amethyst cluster even
        // when the player is nowhere near the cluster. Therefore clusters/buds are
        // direct scan evidence, not merely a by-product of budding-amethyst checks.
        if (detectClusters.get() && isCluster(block)) {
            result.clusterCount++;

            if (isMatureGrowth(block)) {
                result.matureGrowthCount++;
            }

            result.targets.add(pos.toImmutable());

            // A cluster with calcite nearby is much stronger geode context.
            if (detectCalcite.get() && nearCalcite(pos, 3)) {
                result.nearCalciteCount++;
            }
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

        if (detectDripstone.get() && isDripstone(block)) {
            result.dripstoneCount++;
            result.targets.add(pos.toImmutable());
        }

        if (detectAmethystBlocks.get() && block == Blocks.AMETHYST_BLOCK) {
            result.amethystBlockCount++;
        }

        if (detectAmethyst.get() && isAmethyst(block)) {
            result.amethystCount++;
        }
    }

    private boolean nearCalcite(BlockPos center, int radius) {
        if (mc.world == null) return false;

        int r = Math.max(1, radius);

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > r) continue;

                    if (mc.world.getBlockState(center.add(dx, dy, dz)).getBlock()
                        == Blocks.CALCITE) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isUsefulSignal(Block block) {
        return block == Blocks.BUDDING_AMETHYST
            || isGrowthStage(block)
            || (detectCalcite.get() && block == Blocks.CALCITE)
            || isCaveVine(block)
            || block == Blocks.BEE_NEST
            || isDeepslateFamily(block)
            || isDripstone(block);
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

    private boolean isCluster(Block block) {
        return block == Blocks.SMALL_AMETHYST_BUD
            || block == Blocks.MEDIUM_AMETHYST_BUD
            || block == Blocks.LARGE_AMETHYST_BUD
            || block == Blocks.AMETHYST_CLUSTER;
    }

    private boolean isCaveVine(Block block) {
        return block == Blocks.CAVE_VINES
            || block == Blocks.CAVE_VINES_PLANT;
    }

    private boolean isAmethyst(Block block) {
        return block == Blocks.AMETHYST_BLOCK
            || block == Blocks.BUDDING_AMETHYST
            || isGrowthStage(block);
    }

    private boolean isMatureGrowth(Block block) {
        return block == Blocks.LARGE_AMETHYST_BUD
            || block == Blocks.AMETHYST_CLUSTER;
    }

    private boolean isDripstone(Block block) {
        return block == Blocks.DRIPSTONE_BLOCK
            || block == Blocks.POINTED_DRIPSTONE;
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
        // Primary evidence: actual amethyst growth/cluster data found directly in
        // the loaded chunk palette. No ocean/water veto is applied.
        int score = 0;

        boolean hasBudding = result.buddingCount > 0;
        boolean hasCluster = result.clusterCount > 0;
        boolean hasMature = result.matureGrowthCount > 0;
        boolean hasGrowth = result.growthCount > 0 || hasMature;
        boolean strongGrowth = result.growthCount >= 2 || result.matureGrowthCount >= 2;

        if (requireAmethystGrowth.get() && hasBudding && !hasGrowth && !hasCluster) {
            return 0;
        }

        // Direct cluster signal: this is the important V4 behaviour for finding
        // a geode when the player is not physically beside it.
        if (hasCluster) score += 3;
        if (hasMature) score += Math.min(result.matureGrowthCount * 2, 12);
        if (result.clusterCount >= 2) score += 3;
        if (result.clusterCount >= 4) score += 4;
        if (result.clusterCount >= 8) score += 6;

        if (hasBudding) score += 2;
        if (hasGrowth) score += 3;
        if (strongGrowth) score += 3;

        if (result.growthCount >= 4) score += 4;
        if (result.growthCount >= 8) score += 6;
        if (result.growthBaseCount >= 2) score += 3;
        if (result.growthBaseCount >= 4) score += 5;

        // Calcite is contextual evidence used by the reference Sus Chunk Finder.
        if (result.nearCalciteCount > 0) score += Math.min(result.nearCalciteCount * 2, 8);
        if (result.calciteCount >= 4 && (hasCluster || hasGrowth)) score += 2;
        if (result.calciteCount >= 12 && (hasCluster || hasGrowth)) score += 3;

        if (clusterAnalysis.get() && result.clusterCountDetected > 0) {
            score += Math.min(result.clusterCountDetected * 2, 8);
            score += Math.min(result.clusterScore / 4, 8);
        }

        // Cave context follows the reference approach: useful as confirmation,
        // never sufficient by itself.
        if (hasCluster || hasGrowth) {
            if (result.caveVineCount >= 4) score += 1;
            if (result.caveVineCount >= 12) score += 2;
            if (result.dripstoneCount >= 8) score += 1;
            if (result.dripstoneCount >= 20) score += 2;
            if (result.beeNestCount >= 2) score += 1;
        }

        if (densityAnalysis.get()) score += Math.min(result.densityScore / 2, 5);

        // Keep sensitivity predictable: sensitivity 1 is strict about evidence,
        // not a multiplier that paints almost every chunk.
        int threshold = Math.max(1, sensitivity.get());
        int effectiveScore = Math.max(0, score - (threshold - 1) * 2);

        return effectiveScore;
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
        double y = renderAtPlayerY.get() ? mc.player.getY() : fixedY.get();
        int range = renderRange.get();

        // Match the reference addon's chunk-cell presentation: one small,
        // low-height rectangle per detected chunk. Adjacent chunks are NOT
        // merged, so the individual chunk grid remains visible.
        for (ScanResult result : results.values()) {
            if (renderOnlySuspicious.get() && result.score < sensitivity.get()) continue;

            int dx = result.chunkX - centerX;
            int dz = result.chunkZ - centerZ;
            if (Math.max(Math.abs(dx), Math.abs(dz)) > range) continue;

            // Restore the earlier LARP-style chunk marker: centered 32x32 cell,
            // low-height filled Sides only. Outline/edge alpha is zero.
            double minX = result.chunkX * 16.0 - 8.0;
            double minZ = result.chunkZ * 16.0 - 8.0;
            double maxX = result.chunkX * 16.0 + 24.0;
            double maxZ = result.chunkZ * 16.0 + 24.0;
            double topY = y + Math.max(0.10, renderHeight.get() * 0.10);

            event.renderer.box(
                minX, y, minZ,
                maxX, topY, maxZ,
                redFill,
                new SettingColor(255, 0, 0, 0),
                meteordevelopment.meteorclient.renderer.ShapeMode.Sides,
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
        int dripstoneCount;
        int calciteCount;
        int nearCalciteCount;
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
