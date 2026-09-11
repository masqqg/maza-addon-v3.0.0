package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CaveFiller extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> maxClusterSize = sgGeneral.add(new IntSetting.Builder()
        .name("max-cluster-size").description("Bu sayıdan küçük boşluklar kapatılır")
        .defaultValue(6).min(1).max(32).sliderRange(1, 32).build());

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius").description("Tarama yarıçapı (chunk)")
        .defaultValue(4).min(1).max(16).sliderRange(1, 16).build());

    // Kapatılacak küçük boşluklar
    private final Set<BlockPos> fillBlocks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> scannedChunks = ConcurrentHashMap.newKeySet();

    public CaveFiller() {
        super(MazaCategory.INSTANCE, "cave-filler", "Küçük boşlukları gerçek taş/deepslate ile kapatır");
    }

    /**
     * Mixin tarafından çağrılır. Bu pozisyon doldurulmalı mı?
     */
    public boolean shouldFill(BlockPos pos) {
        return fillBlocks.contains(pos);
    }

    @Override
    public void onActivate() {
        fillBlocks.clear();
        scannedChunks.clear();
        if (mc == null || mc.world == null || mc.player == null) return;
        info("CaveFiller aktif. Küçük boşluklar taşa dönüşüyor...");
        rescanAll();
        // Chunk'ları yeniden çizdir
        reloadChunks();
    }

    @Override
    public void onDeactivate() {
        fillBlocks.clear();
        scannedChunks.clear();
        reloadChunks();
    }

    private void reloadChunks() {
        try {
            if (mc.worldRenderer != null) {
                mc.worldRenderer.reload();
            }
        } catch (Exception ignored) {}
    }

    private void rescanAll() {
        try {
            ChunkPos here = mc.player.getChunkPos();
            int radius = scanRadius.get();
            for (int cx = here.x - radius; cx <= here.x + radius; cx++) {
                for (int cz = here.z - radius; cz <= here.z + radius; cz++) {
                    WorldChunk chunk = mc.world.getChunk(cx, cz);
                    if (chunk != null && !scannedChunks.contains(chunk.getPos())) {
                        scanChunk(chunk);
                        scannedChunks.add(chunk.getPos());
                    }
                }
            }
            info("Tarama bitti. Kapatılan blok: " + fillBlocks.size());
        } catch (Exception ignored) {}
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (event == null || event.chunk() == null) return;
        try {
            ChunkPos pos = event.chunk().getPos();
            if (!scannedChunks.contains(pos)) {
                scanChunk(event.chunk());
                scannedChunks.add(pos);
            }
        } catch (Exception ignored) {}
    }

    private void scanChunk(WorldChunk chunk) {
        if (mc == null || mc.world == null || chunk == null) return;

        int chunkX = chunk.getPos().x * 16;
        int chunkZ = chunk.getPos().z * 16;
        Set<BlockPos> visited = new HashSet<>();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = -64; y < 320; y++) {
                    BlockPos pos = new BlockPos(chunkX + x, y, chunkZ + z);
                    try {
                        if (visited.contains(pos)) continue;
                        if (!chunk.getBlockState(pos).isAir()) continue;

                        List<BlockPos> cluster = floodFillAir(chunk, pos, visited, chunkX, chunkZ);
                        if (cluster.size() < maxClusterSize.get()) {
                            fillBlocks.addAll(cluster);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private List<BlockPos> floodFillAir(WorldChunk chunk, BlockPos start, Set<BlockPos> visited, int chunkX, int chunkZ) {
        List<BlockPos> cluster = new ArrayList<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        int limit = 4096;
        int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};

        while (!queue.isEmpty() && cluster.size() < limit) {
            BlockPos current = queue.poll();
            cluster.add(current);

            for (int[] d : dirs) {
                BlockPos next = current.add(d[0], d[1], d[2]);
                int nx = next.getX();
                int nz = next.getZ();
                if (nx < chunkX || nx >= chunkX + 16 || nz < chunkZ || nz >= chunkZ + 16) continue;

                if (!visited.contains(next)) {
                    try {
                        if (chunk.getBlockState(next).isAir()) {
                            visited.add(next);
                            queue.add(next);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return cluster;
    }
}
