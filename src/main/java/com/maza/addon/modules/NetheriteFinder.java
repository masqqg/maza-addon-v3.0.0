package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetheriteFinder extends Module {
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup render = settings.createGroup("Render");

    private final Setting<Integer> range = general.add(new IntSetting.Builder()
        .name("range").defaultValue(16).min(1).sliderMax(32).build());

    private final Setting<Boolean> candidateSections = render.add(new BoolSetting.Builder()
        .name("candidate-sections")
        .description("Show the 16x16x16 white candidate section containing Ancient Debris.")
        .defaultValue(true).build());

    private final Setting<Boolean> debrisEsp = render.add(new BoolSetting.Builder()
        .name("debris-esp").defaultValue(true).build());

    private final Setting<Boolean> tracers = render.add(new BoolSetting.Builder()
        .name("tracers").defaultValue(true).build());

    private final Setting<ShapeMode> shapeMode = render.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> white = render.add(new ColorSetting.Builder()
        .name("white")
        .defaultValue(new SettingColor(255, 255, 255, 65)).build());

    private final Setting<SettingColor> whiteLine = render.add(new ColorSetting.Builder()
        .name("white-line")
        .defaultValue(new SettingColor(255, 255, 255, 220)).build());

    private final Map<Long, List<SectionCandidate>> candidates = new HashMap<>();
    private final Map<Long, List<BlockPos>> debris = new HashMap<>();
    private long lastScan;

    public NetheriteFinder() {
        super(MazaCategory.INSTANCE, "netherite-finder", "Finds Ancient Debris candidates from received chunk section data and highlights the actual debris in white.");
    }

    @Override
    public void onActivate() {
        candidates.clear();
        debris.clear();
        lastScan = 0L;
        scanLoadedChunks();
    }

    @Override
    public void onDeactivate() {
        candidates.clear();
        debris.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastScan >= 750L) {
            scanLoadedChunks();
            lastScan = now;
        }
    }

    // The important part copied from the supplied Netherite finder is the early
    // chunk-data path: scan the exact WorldChunk delivered with ChunkDataEvent,
    // rather than waiting for a later relog/normal chunk lookup.
    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (event.chunk() == null) return;
        scanChunk(event.chunk());
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null || event.pos == null) return;
        if (!event.oldState.isOf(Blocks.ANCIENT_DEBRIS) && !event.newState.isOf(Blocks.ANCIENT_DEBRIS)) return;
        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(event.pos.getX() >> 4, event.pos.getZ() >> 4);
        if (chunk != null) scanChunk(chunk);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;

        for (List<SectionCandidate> list : new ArrayList<>(candidates.values())) {
            for (SectionCandidate c : list) {
                if (!withinRange(c.chunkX, c.chunkZ, pcx, pcz)) continue;
                if (!candidateSections.get()) continue;

                double minX = c.chunkX * 16.0;
                double minY = c.sectionY * 16.0;
                double minZ = c.chunkZ * 16.0;
                event.renderer.box(
                    minX, minY, minZ,
                    minX + 16.0, minY + 16.0, minZ + 16.0,
                    white.get(), whiteLine.get(), shapeMode.get(), 0
                );
            }
        }

        for (List<BlockPos> list : new ArrayList<>(debris.values())) {
            for (BlockPos pos : list) {
                int cx = pos.getX() >> 4;
                int cz = pos.getZ() >> 4;
                if (!withinRange(cx, cz, pcx, pcz)) continue;

                if (debrisEsp.get()) {
                    event.renderer.box(
                        pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                        white.get(), whiteLine.get(), shapeMode.get(), 0
                    );
                }

                if (tracers.get()) {
                    event.renderer.line(
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        whiteLine.get()
                    );
                }
            }
        }
    }

    private boolean withinRange(int cx, int cz, int pcx, int pcz) {
        return Math.max(Math.abs(cx - pcx), Math.abs(cz - pcz)) <= range.get();
    }

    private void scanLoadedChunks() {
        if (mc.world == null || mc.player == null) return;
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;
        int r = range.get();

        Map<Long, List<SectionCandidate>> newCandidates = new HashMap<>();
        Map<Long, List<BlockPos>> newDebris = new HashMap<>();

        for (int x = pcx - r; x <= pcx + r; x++) {
            for (int z = pcz - r; z <= pcz + r; z++) {
                if (Math.max(Math.abs(x - pcx), Math.abs(z - pcz)) > r) continue;
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x, z);
                if (chunk == null) continue;
                ScanData data = scanChunkData(chunk);
                if (!data.candidates.isEmpty()) newCandidates.put(ChunkKey.of(x, z), data.candidates);
                if (!data.debris.isEmpty()) newDebris.put(ChunkKey.of(x, z), data.debris);
            }
        }

        candidates.clear();
        candidates.putAll(newCandidates);
        debris.clear();
        debris.putAll(newDebris);
    }

    private void scanChunk(WorldChunk chunk) {
        if (chunk == null || mc.player == null) return;
        if (!withinRange(chunk.getPos().x, chunk.getPos().z, mc.player.getChunkPos().x, mc.player.getChunkPos().z)) return;

        ScanData data = scanChunkData(chunk);
        long key = ChunkKey.of(chunk.getPos().x, chunk.getPos().z);
        if (data.candidates.isEmpty()) candidates.remove(key); else candidates.put(key, data.candidates);
        if (data.debris.isEmpty()) debris.remove(key); else debris.put(key, data.debris);
    }

    private ScanData scanChunkData(WorldChunk chunk) {
        ScanData data = new ScanData();
        ChunkSection[] sections = chunk.getSectionArray();
        int bottomSection = mc.world != null ? mc.world.getBottomSectionCoord() : -4;

        for (int si = 0; si < sections.length; si++) {
            ChunkSection section = sections[si];
            if (section == null || !section.hasAny(state -> state.isOf(Blocks.ANCIENT_DEBRIS))) continue;

            int sectionY = bottomSection + si;
            data.candidates.add(new SectionCandidate(chunk.getPos().x, chunk.getPos().z, sectionY));

            List<BlockPos> found = new ArrayList<>();
            for (int ly = 0; ly < 16; ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        if (section.getBlockState(lx, ly, lz).isOf(Blocks.ANCIENT_DEBRIS)) {
                            found.add(new BlockPos(
                                chunk.getPos().x * 16 + lx,
                                sectionY * 16 + ly,
                                chunk.getPos().z * 16 + lz
                            ));
                        }
                    }
                }
            }
            data.debris.addAll(found);
        }
        return data;
    }

    private static final class ScanData {
        final List<SectionCandidate> candidates = new ArrayList<>();
        final List<BlockPos> debris = new ArrayList<>();
    }

    private record SectionCandidate(int chunkX, int chunkZ, int sectionY) {}

    private static final class ChunkKey {
        static long of(int x, int z) {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }
    }
}
