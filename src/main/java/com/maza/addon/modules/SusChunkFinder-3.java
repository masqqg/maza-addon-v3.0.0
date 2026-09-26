package com.maza.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
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
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fast, crash-safe suspicious chunk finder.
 * Only fully grown amethyst clusters / large buds are counted.
 * Chunks are queued immediately when they become loaded.
 */
public class SusChunkFinder extends Module {
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup detection = settings.createGroup("Detection");
    private final SettingGroup render = settings.createGroup("Render");

    private final Setting<Integer> scanRange = general.add(new IntSetting.Builder()
        .name("scan-range").defaultValue(8).min(1).max(32).sliderMax(32).build());

    private final Setting<Integer> renderRange = general.add(new IntSetting.Builder()
        .name("render-range").defaultValue(8).min(1).max(32).sliderMax(32).build());

    private final Setting<Integer> minimumGrown = detection.add(new IntSetting.Builder()
        .name("minimum-grown-amethyst").defaultValue(8).min(1).max(128).sliderMax(64).build());

    private final Setting<Integer> minimumCluster = detection.add(new IntSetting.Builder()
        .name("minimum-cluster-size").defaultValue(4).min(1).max(32).sliderMax(16).build());

    private final Setting<Integer> minY = detection.add(new IntSetting.Builder()
        .name("min-y").defaultValue(-64).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Integer> maxY = detection.add(new IntSetting.Builder()
        .name("max-y").defaultValue(80).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Integer> chunksPerTick = general.add(new IntSetting.Builder()
        .name("chunks-per-tick").description("How many newly loaded chunks are scanned each tick.")
        .defaultValue(2).min(1).max(8).sliderMax(8).build());

    private final Setting<Boolean> rescan = general.add(new BoolSetting.Builder()
        .name("rescan-loaded-chunks").description("Rescan loaded chunks after they are initially scanned.")
        .defaultValue(false).build());

    private final Setting<Integer> rescanSeconds = general.add(new IntSetting.Builder()
        .name("rescan-seconds").defaultValue(30).min(1).max(600).sliderMax(120).build());

    private final Setting<ShapeMode> shapeMode = render.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());

    private final Setting<Boolean> renderAtPlayerY = render.add(new BoolSetting.Builder()
        .name("render-at-player-y").defaultValue(true).build());

    private final Setting<Integer> fixedY = render.add(new IntSetting.Builder()
        .name("fixed-y").defaultValue(0).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final SettingColor redSide = new SettingColor(255, 0, 0, 55);
    private final SettingColor redLine = new SettingColor(255, 0, 0, 255);

    private final Map<Long, ScanResult> results = new HashMap<>();
    private final Map<Long, Long> scanTimes = new HashMap<>();
    private final Set<Long> queued = new HashSet<>();
    private final ArrayDeque<ScanTask> queue = new ArrayDeque<>();

    @Override
    public void onActivate() {
        clearRuntime();
    }

    @Override
    public void onDeactivate() {
        clearRuntime();
    }

    public SusChunkFinder() {
        super(
            Categories.Misc,
            "maza-sus-chunk-finder",
            "Highlights chunks containing many fully grown amethyst clusters."
        );
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) {
            clearRuntime();
            return;
        }

        try {
            queueLoadedChunksImmediately();
            scanQueuedChunks();
        } catch (Throwable ignored) {
            // Never let the finder take down the game because of a bad/unloading chunk.
        }
    }

    /** Finds loaded chunks every tick instead of waiting 250ms for a queue refresh. */
    private void queueLoadedChunksImmediately() {
        int centerX = mc.player.getChunkPos().x;
        int centerZ = mc.player.getChunkPos().z;
        int range = scanRange.get();
        long now = System.currentTimeMillis();

        List<ScanTask> candidates = new ArrayList<>();

        for (int x = centerX - range; x <= centerX + range; x++) {
            for (int z = centerZ - range; z <= centerZ + range; z++) {
                if (Math.max(Math.abs(x - centerX), Math.abs(z - centerZ)) > range) continue;

                long key = ChunkPos.toLong(x, z);
                if (queued.contains(key)) continue;

                Long last = scanTimes.get(key);
                if (last != null) {
                    if (!rescan.get()) continue;
                    if (now - last < rescanSeconds.get() * 1000L) continue;
                }

                WorldChunk chunk;
                try {
                    chunk = mc.world.getChunkManager().getWorldChunk(x, z);
                } catch (Throwable ignored) {
                    continue;
                }

                if (chunk == null) continue;

                double distance = Math.abs(x - centerX) + Math.abs(z - centerZ);
                candidates.add(new ScanTask(key, x, z, distance));
            }
        }

        candidates.sort(Comparator.comparingDouble(t -> t.priority));

        // Queue all currently loaded chunks. Only a small number are actually scanned per tick.
        for (ScanTask task : candidates) {
            if (queued.add(task.key)) queue.addLast(task);
        }
    }

    private void scanQueuedChunks() {
        int amount = Math.max(1, chunksPerTick.get());

        for (int i = 0; i < amount; i++) {
            ScanTask task = queue.pollFirst();
            if (task == null) return;
            queued.remove(task.key);

            try {
                scanChunk(task);
            } catch (Throwable ignored) {
                // Chunk can unload/change while being scanned. Ignore that chunk and continue.
            }
        }
    }

    private void scanChunk(ScanTask task) {
        if (mc.world == null) return;

        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(task.chunkX, task.chunkZ);
        if (chunk == null) return;

        int bottom = chunk.getBottomY();
        int top = bottom + chunk.getHeight() - 1;
        int startY = Math.max(bottom, minY.get());
        int endY = Math.min(top, maxY.get());
        if (endY < startY) return;

        ScanResult result = new ScanResult(task.chunkX, task.chunkZ);

        // Scan every block in this already-loaded chunk. No FPS checks or artificial time limit.
        for (int y = startY; y <= endY; y++) {
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    try {
                        BlockPos pos = new BlockPos(task.chunkX * 16 + localX, y, task.chunkZ * 16 + localZ);
                        BlockState state = chunk.getBlockState(pos);
                        Block block = state.getBlock();

                        // ONLY fully grown amethyst: Cluster and Large Amethyst Bud.
                        if (block == Blocks.AMETHYST_CLUSTER || block == Blocks.LARGE_AMETHYST_BUD) {
                            result.grownAmethyst.add(pos.toImmutable());
                        }
                    } catch (Throwable ignored) {
                        // Skip only the problematic block.
                    }
                }
            }
        }

        result.grownCount = result.grownAmethyst.size();
        result.largestCluster = findLargestCluster(result.grownAmethyst);
        result.suspicious = result.grownCount >= minimumGrown.get()
            && result.largestCluster >= minimumCluster.get();

        results.put(result.key(), result);
        scanTimes.put(result.key(), System.currentTimeMillis());
    }

    /**
     * Counts connected grown-amethyst formations. Only grown amethyst blocks are considered.
     */
    private int findLargestCluster(List<BlockPos> blocks) {
        if (blocks.isEmpty()) return 0;

        Set<Long> positions = new HashSet<>(blocks.size() * 2);
        for (BlockPos pos : blocks) {
            positions.add(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
        }

        Set<Long> visited = new HashSet<>(positions.size());
        int largest = 0;

        for (BlockPos start : blocks) {
            long startKey = BlockPos.asLong(start.getX(), start.getY(), start.getZ());
            if (!visited.add(startKey)) continue;

            ArrayDeque<BlockPos> open = new ArrayDeque<>();
            open.add(start);
            int size = 0;

            while (!open.isEmpty()) {
                BlockPos current = open.removeFirst();
                size++;

                // 6-direction connection + one block diagonal/near connection.
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 2) continue;

                            long key = BlockPos.asLong(
                                current.getX() + dx,
                                current.getY() + dy,
                                current.getZ() + dz
                            );

                            if (positions.contains(key) && visited.add(key)) {
                                open.addLast(new BlockPos(
                                    current.getX() + dx,
                                    current.getY() + dy,
                                    current.getZ() + dz
                                ));
                            }
                        }
                    }
                }
            }

            if (size > largest) largest = size;
        }

        return largest;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        int centerX = mc.player.getChunkPos().x;
        int centerZ = mc.player.getChunkPos().z;
        int y = renderAtPlayerY.get() ? mc.player.getBlockY() : fixedY.get();
        int range = renderRange.get();

        for (ScanResult result : results.values()) {
            if (!result.suspicious) continue;

            int dx = result.chunkX - centerX;
            int dz = result.chunkZ - centerZ;
            if (Math.max(Math.abs(dx), Math.abs(dz)) > range) continue;

            int minX = result.chunkX * 16;
            int minZ = result.chunkZ * 16;

            try {
                event.renderer.box(
                    minX, y, minZ,
                    minX + 16, y + 0.15, minZ + 16,
                    redSide, redLine, shapeMode.get(), 0
                );
            } catch (Throwable ignored) {
                // Rendering a stale chunk result must never crash the client.
            }
        }
    }

    private void clearRuntime() {
        queue.clear();
        queued.clear();
        results.clear();
        scanTimes.clear();
    }

    private static class ScanTask {
        final long key;
        final int chunkX;
        final int chunkZ;
        final double priority;

        ScanTask(long key, int chunkX, int chunkZ, double priority) {
            this.key = key;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.priority = priority;
        }
    }

    private static class ScanResult {
        final int chunkX;
        final int chunkZ;
        final List<BlockPos> grownAmethyst = new ArrayList<>();
        int grownCount;
        int largestCluster;
        boolean suspicious;

        ScanResult(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        long key() {
            return ChunkPos.toLong(chunkX, chunkZ);
        }
    }
}
