package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FakeOreFilter extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgDebug = settings.createGroup("Debug");

    // Genel Ayarlar
    private final Setting<Integer> minAirBlocks = sgGeneral.add(new IntSetting.Builder()
        .name("min-air-blocks").description("Minimum çevresel boşluk sayısı (6'dan azı sahte)")
        .defaultValue(6).min(1).max(20).sliderRange(1, 20).build());

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius").description("Tarama yarıçapı (chunk)")
        .defaultValue(4).min(1).max(16).sliderRange(1, 16).build());

    private final Setting<Boolean> strictYCheck = sgGeneral.add(new BoolSetting.Builder()
        .name("strict-y-check").description("Y katmanına göre taş/deepslate kontrolü")
        .defaultValue(true).build());

    // Render Ayarları
    private final Setting<Integer> renderRange = sgRender.add(new IntSetting.Builder()
        .name("render-range").description("Max render mesafesi (blok)")
        .defaultValue(128).min(16).max(512).sliderRange(16, 512).build());

    private final Setting<SettingColor> fakeColor = sgRender.add(new ColorSetting.Builder()
        .name("fake-color").description("Sahte maden rengi")
        .defaultValue(new SettingColor(255, 0, 0, 150)).build());

    private final Setting<SettingColor> realColor = sgRender.add(new ColorSetting.Builder()
        .name("real-color").description("Gerçek maden rengi")
        .defaultValue(new SettingColor(0, 255, 0, 100)).build());

    private final Setting<Boolean> showReal = sgRender.add(new BoolSetting.Builder()
        .name("show-real").description("Gerçek madenleri de göster")
        .defaultValue(false).build());

    private final Setting<Boolean> showFake = sgRender.add(new BoolSetting.Builder()
        .name("show-fake").description("Sahte madenleri göster")
        .defaultValue(true).build());

    // Debug Ayarları
    private final Setting<Boolean> debugLog = sgDebug.add(new BoolSetting.Builder()
        .name("debug-log").description("Chat'e analiz logu yaz")
        .defaultValue(false).build());

    // Veri Setleri
    private final Set<BlockPos> fakeOres = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> realOres = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> scannedChunks = ConcurrentHashMap.newKeySet();

    public FakeOreFilter() {
        super(MazaCategory.INSTANCE, "fake-ore-filter", "Detaylı sahte maden analizi ve filtreleme");
    }

    @Override
    public void onActivate() {
        fakeOres.clear();
        realOres.clear();
        scannedChunks.clear();
        
        if (mc == null || mc.world == null || mc.player == null) return;
        
        info("FakeOreFilter aktif. Detaylı tarama başlıyor...");
        
        ChunkPos here = mc.player.getChunkPos();
        int radius = scanRadius.get();
        
        for (int cx = here.x - radius; cx <= here.x + radius; cx++) {
            for (int cz = here.z - radius; cz <= here.z + radius; cz++) {
                try {
                    WorldChunk chunk = mc.world.getChunk(cx, cz);
                    if (chunk != null && !scannedChunks.contains(chunk.getPos())) {
                        scanChunkDeep(chunk);
                        scannedChunks.add(chunk.getPos());
                    }
                } catch (Exception e) {
                    if (debugLog.get()) error("Chunk tarama hatası: " + e.getMessage());
                }
            }
        }
        
        if (debugLog.get()) {
            info("Tarama tamamlandı. Sahte: " + fakeOres.size() + ", Gerçek: " + realOres.size());
        }
    }

    @Override
    public void onDeactivate() {
        fakeOres.clear();
        realOres.clear();
        scannedChunks.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (event == null || event.chunk() == null) return;
        
        try {
            ChunkPos pos = event.chunk().getPos();
            if (!scannedChunks.contains(pos)) {
                scanChunkDeep(event.chunk());
                scannedChunks.add(pos);
                
                if (debugLog.get()) {
                    info("Chunk [" + pos.x + ", " + pos.z + "] tarandı.");
                }
            }
        } catch (Exception e) {
            if (debugLog.get()) error("ChunkData hatası: " + e.getMessage());
        }
    }

    /**
     * Detaylı chunk taraması. Her bloğu tek tek analiz eder.
     */
    private void scanChunkDeep(WorldChunk chunk) {
        if (mc == null || mc.world == null || chunk == null) return;

        int chunkX = chunk.getPos().x * 16;
        int chunkZ = chunk.getPos().z * 16;
        
        // Her bloğu tara (-64 ile 320 arası)
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = -64; y < 320; y++) {
                    BlockPos pos = new BlockPos(chunkX + x, y, chunkZ + z);
                    
                    try {
                        Block block = chunk.getBlockState(pos).getBlock();
                        
                        // Cevher mi?
                        if (isOreBlock(block)) {
                            analyzeOreBlock(chunk, pos, block, y);
                        }
                    } catch (Exception ignored) {
                        // Crash önleme: herhangi bir hata olursa devam et
                    }
                }
            }
        }
    }

    /**
     * Tek bir cevher bloğunu detaylı analiz eder.
     */
    private void analyzeOreBlock(WorldChunk chunk, BlockPos pos, Block block, int y) {
        try {
            // 1. Çevresel boşluk analizi (6 yönlü + köşeler)
            int airCount = countSurroundingAir(chunk, pos);
            
            // 2. Y katmanı analizi (taş vs deepslate)
            boolean yLevelValid = checkYLevelValidity(block, y);
            
            // 3. Komşu blok analizi (cevher cluster'ı mı?)
            int adjacentOres = countAdjacentOres(chunk, pos);
            
            // Karar verme mantığı
            boolean isFake = false;
            String reason = "";
            
            // Kural 1: Boşluk sayısı 6'dan azsa sahte
            if (airCount < minAirBlocks.get()) {
                isFake = true;
                reason = "Düşük boşluk (" + airCount + ")";
            }
            
            // Kural 2: Y katmanı uyumsuzsa sahte
            if (strictYCheck.get() && !yLevelValid) {
                isFake = true;
                reason += (reason.isEmpty() ? "" : " + ") + "Y katmanı uyumsuz";
            }
            
            // Kural 3: Çok fazla bitişik cevher varsa şüpheli (anti-cheat pattern)
            if (adjacentOres > 8) {
                isFake = true;
                reason += (reason.isEmpty() ? "" : " + ") + "Şüpheli cluster";
            }
            
            // Sonuç kaydet
            if (isFake) {
                fakeOres.add(pos);
                if (debugLog.get() && fakeOres.size() % 50 == 0) {
                    info("Sahte tespit: " + pos.toShortString() + " | " + reason);
                }
            } else {
                realOres.add(pos);
            }
            
        } catch (Exception e) {
            // Crash önleme
            if (debugLog.get()) error("Analiz hatası: " + e.getMessage());
        }
    }

    /**
     * Çevredeki tüm hava bloklarını sayar (6 yön + 8 köşe = 14 kontrol)
     */
    private int countSurroundingAir(WorldChunk chunk, BlockPos pos) {
        int count = 0;
        
        // 6 temel yön
        int[][] directions = {
            {1, 0, 0}, {-1, 0, 0},  // X
            {0, 1, 0}, {0, -1, 0},  // Y
            {0, 0, 1}, {0, 0, -1}   // Z
        };
        
        // 8 köşe
        int[][] corners = {
            {1, 1, 1}, {1, 1, -1}, {1, -1, 1}, {1, -1, -1},
            {-1, 1, 1}, {-1, 1, -1}, {-1, -1, 1}, {-1, -1, -1}
        };
        
        try {
            // Temel yönler
            for (int[] dir : directions) {
                BlockPos adj = pos.add(dir[0], dir[1], dir[2]);
                if (isAirSafe(chunk, adj)) count++;
            }
            
            // Köşeler (yarım puan)
            for (int[] corner : corners) {
                BlockPos adj = pos.add(corner[0], corner[1], corner[2]);
                if (isAirSafe(chunk, adj)) count += 0.5;
            }
        } catch (Exception ignored) {}
        
        return (int) Math.round(count);
    }

    /**
     * Güvenli hava kontrolü
     */
    private boolean isAirSafe(WorldChunk chunk, BlockPos pos) {
        try {
            return chunk.getBlockState(pos).isAir();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Y katmanına göre blok türü geçerliliğini kontrol eder
     */
    private boolean checkYLevelValidity(Block block, int y) {
        try {
            // Y > 0: Normal taş cevherleri olmalı
            if (y > 0) {
                return isRegularOre(block) || isNetherOre(block);
            }
            // Y <= 0: Deepslate cevherleri olmalı
            else {
                return isDeepslateOre(block) || isNetherOre(block);
            }
        } catch (Exception e) {
            return true; // Hata olursa geçerli say
        }
    }

    /**
     * Bitişik cevher sayısını hesaplar
     */
    private int countAdjacentOres(WorldChunk chunk, BlockPos pos) {
        int count = 0;
        int[][] directions = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
        };
        
        try {
            for (int[] dir : directions) {
                BlockPos adj = pos.add(dir[0], dir[1], dir[2]);
                Block adjBlock = chunk.getBlockState(adj).getBlock();
                if (isOreBlock(adjBlock)) count++;
            }
        } catch (Exception ignored) {}
        
        return count;
    }

    /**
     * Blok cevher mi kontrol eder
     */
    private boolean isOreBlock(Block block) {
        return isRegularOre(block) || isDeepslateOre(block) || isNetherOre(block);
    }

    private boolean isRegularOre(Block block) {
        return block == Blocks.COAL_ORE ||
               block == Blocks.IRON_ORE ||
               block == Blocks.GOLD_ORE ||
               block == Blocks.DIAMOND_ORE ||
               block == Blocks.EMERALD_ORE ||
               block == Blocks.LAPIS_ORE ||
               block == Blocks.REDSTONE_ORE ||
               block == Blocks.COPPER_ORE;
    }

    private boolean isDeepslateOre(Block block) {
        return block == Blocks.DEEPSLATE_COAL_ORE ||
               block == Blocks.DEEPSLATE_IRON_ORE ||
               block == Blocks.DEEPSLATE_GOLD_ORE ||
               block == Blocks.DEEPSLATE_DIAMOND_ORE ||
               block == Blocks.DEEPSLATE_EMERALD_ORE ||
               block == Blocks.DEEPSLATE_LAPIS_ORE ||
               block == Blocks.DEEPSLATE_REDSTONE_ORE ||
               block == Blocks.DEEPSLATE_COPPER_ORE;
    }

    private boolean isNetherOre(Block block) {
        return block == Blocks.NETHER_GOLD_ORE ||
               block == Blocks.NETHER_QUARTZ_ORE ||
               block == Blocks.ANCIENT_DEBRIS;
    }

    /**
     * Render olayı. Sahte ve gerçek madenleri çizer.
     */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc == null || mc.player == null || event == null || event.renderer == null) return;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        double maxDist = renderRange.get();
        double maxDistSq = maxDist * maxDist;

        int renderedFake = 0;
        int renderedReal = 0;

        // Sahte madenleri çiz
        if (showFake.get()) {
            for (BlockPos pos : fakeOres) {
                try {
                    double dx = pos.getX() + 0.5 - px;
                    double dy = pos.getY() + 0.5 - py;
                    double dz = pos.getZ() + 0.5 - pz;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    
                    if (distSq > maxDistSq) continue;
                    
                    event.renderer.box(
                        pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                        fakeColor.get(), fakeColor.get(), ShapeMode.Both, 0
                    );
                    
                    renderedFake++;
                    if (renderedFake > 500) break; // FPS koruması
                } catch (Exception ignored) {}
            }
        }

        // Gerçek madenleri çiz
        if (showReal.get()) {
            for (BlockPos pos : realOres) {
                try {
                    double dx = pos.getX() + 0.5 - px;
                    double dy = pos.getY() + 0.5 - py;
                    double dz = pos.getZ() + 0.5 - pz;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    
                    if (distSq > maxDistSq) continue;
                    
                    event.renderer.box(
                        pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                        realColor.get(), realColor.get(), ShapeMode.Both, 0
                    );
                    
                    renderedReal++;
                    if (renderedReal > 500) break; // FPS koruması
                } catch (Exception ignored) {}
            }
        }
    }
                    }
