package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
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

public class OreFilter extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> minClusterSize = sgGeneral.add(new IntSetting.Builder()
        .name("min-cluster-size").description("Bu sayıdan küçük cevher kümeleri sahte kabul edilir")
        .defaultValue(5).min(1).max(20).sliderRange(1, 20).build());

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius").description("Tarama yarıçapı (chunk)")
        .defaultValue(4).min(1).max(16).sliderRange(1, 16).build());

    // Sahte cevherler = client-side taş/deepslate ile değiştirilecek
    private final Set<BlockPos> fakeOres = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> scannedChunks = ConcurrentHashMap.newKeySet();

    public OreFilter() {
        super(MazaCategory.INSTANCE, "ore-filter", "Sahte cevherleri tespit edip taşa çevirir (Anti-Xray bypass)");
    }

    /**
     * Mixin tarafından çağrılır. Bu pozisyon sahte cevher mi?
     */
    public boolean isFakeOre(BlockPos pos) {
        return fakeOres.contains(pos);
    }

    @Override
    public void onActivate() {
        fakeOres.clear();
        scannedChunks.clear();
        if (mc == null || mc.world == null || mc.player == null) return;
        info("OreFilter aktif. Sahte cevherler taşa dönüşüyor...");
        rescanAll();
        reloadChunks();
    }

    @Override
    public void onDeactivate() {
        fakeOres.clear();
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
            info("Tarama bitti. Sahte cevher: " + fakeOres.size());
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
                        
                        Block block = chunk.getBlockState(pos).getBlock();
                        if (!isOre(block)) continue;

                        // BFS ile cluster tespit et
                        List<BlockPos> cluster = bfsCluster(chunk, pos, visited, chunkX, chunkZ);
                        
                        // Cluster boyutu küçükse sahte
                        if (cluster.size() < minClusterSize.get()) {
                            fakeOres.addAll(cluster);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private List<BlockPos> bfsCluster(WorldChunk chunk, BlockPos start, Set<BlockPos> visited, int chunkX, int chunkZ) {
        List<BlockPos> cluster = new ArrayList<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        int limit = 256;
        int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};

        while (!queue.isEmpty() && cluster.size() < limit) {
            BlockPos current = queue.poll();
            cluster.add(current);

            for (int[] d : dirs) {
                BlockPos next = current.add(d[0], d[1], d[2]);
                int nx = next.getX();
                int nz = next.getZ();
                
                // Chunk sınırları içinde mi?
                if (nx < chunkX || nx >= chunkX + 16 || nz < chunkZ || nz >= chunkZ + 16) continue;

                if (!visited.contains(next)) {
                    try {
                        Block nextBlock = chunk.getBlockState(next).getBlock();
                        if (isOre(nextBlock)) {
                            visited.add(next);
                            queue.add(next);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return cluster;
    }

    private boolean isOre(Block block) {
        return block == Blocks.COAL_ORE ||
               block == Blocks.IRON_ORE ||
               block == Blocks.GOLD_ORE ||
               block == Blocks.DIAMOND_ORE ||
               block == Blocks.EMERALD_ORE ||
               block == Blocks.LAPIS_ORE ||
               block == Blocks.REDSTONE_ORE ||
               block == Blocks.COPPER_ORE ||
               block == Blocks.DEEPSLATE_COAL_ORE ||
               block == Blocks.DEEPSLATE_IRON_ORE ||
               block == Blocks.DEEPSLATE_GOLD_ORE ||
               block == Blocks.DEEPSLATE_DIAMOND_ORE ||
               block == Blocks.DEEPSLATE_EMERALD_ORE ||
               block == Blocks.DEEPSLATE_LAPIS_ORE ||
               block == Blocks.DEEPSLATE_REDSTONE_ORE ||
               block == Blocks.DEEPSLATE_COPPER_ORE ||
               block == Blocks.NETHER_GOLD_ORE ||
               block == Blocks.NETHER_QUARTZ_ORE ||
               block == Blocks.ANCIENT_DEBRIS;
    }
}
