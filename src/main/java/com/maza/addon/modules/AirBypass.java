package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AirBypass extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y").description("Minimum Y level")
        .defaultValue(-64).min(-64).max(0).build());

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y").description("Maximum Y level")
        .defaultValue(0).min(-64).max(64).build());

    private final Setting<Integer> clusterSize = sgGeneral.add(new IntSetting.Builder()
        .name("min-cluster").description("Min air blocks in chunk to show")
        .defaultValue(8).min(1).max(64).build());

    private final Setting<Integer> renderRange = sgRender.add(new IntSetting.Builder()
        .name("render-range").description("Max chunks to render")
        .defaultValue(3).min(1).max(8).sliderRange(1, 8).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(255, 0, 0, 40)).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(255, 0, 0, 255)).build());

    // cluster center + air count (tek box per chunk, binlerce değil)
    private final Map<ChunkPos, ClusterInfo> clusters = new ConcurrentHashMap<>();

    public AirBypass() {
        super(MazaCategory.INSTANCE, "air-bypass", "Shows hollow areas / bases below y=0");
    }

    @Override
    public void onActivate() {
        clusters.clear();
        if (mc == null || mc.world == null || mc.player == null) return;
        ChunkPos here = mc.player.getChunkPos();
        for (int cx = here.x - 2; cx <= here.x + 2; cx++) {
            for (int cz = here.z - 2; cz <= here.z + 2; cz++) {
                WorldChunk c = mc.world.getChunk(cx, cz);
                if (c != null) scan(c);
            }
        }
    }

    @Override
    public void onDeactivate() {
        clusters.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (event == null || event.chunk() == null) return;
        scan(event.chunk());
    }

    private void scan(WorldChunk chunk) {
        if (mc == null || mc.world == null || chunk == null) return;
        ChunkPos pos = chunk.getPos();

        int yMin = minY.get();
        int yMax = maxY.get();
        int airCount = 0;

        // her 2 blokta tara -> 8x az işlem
        for (int y = yMin; y <= yMax; y += 2) {
            for (int x = 0; x < 16; x += 2) {
                for (int z = 0; z < 16; z += 2) {
                    try {
                        BlockPos p = new BlockPos(pos.x * 16 + x, y, pos.z * 16 + z);
                        if (chunk.getBlockState(p).isAir()) {
                            airCount++;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        if (airCount >= clusterSize.get()) {
            clusters.put(pos, new ClusterInfo(pos.x * 16 + 8, yMin, pos.z * 16 + 8, airCount));
        } else {
            clusters.remove(pos);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc == null || mc.player == null || event == null || event.renderer == null) return;

        int pcx = (int) Math.floor(mc.player.getX() / 16);
        int pcz = (int) Math.floor(mc.player.getZ() / 16);
        int maxChunks = renderRange.get();
        int yMin = minY.get();
        int yMax = maxY.get();
        int height = Math.max(1, yMax - yMin + 1);

        for (Map.Entry<ChunkPos, ClusterInfo> entry : clusters.entrySet()) {
            ChunkPos cp = entry.getKey();
            int dx = cp.x - pcx;
            int dz = cp.z - pcz;
            if (Math.abs(dx) > maxChunks || Math.abs(dz) > maxChunks) continue;

            ClusterInfo info = entry.getValue();

            // tek büyük box per cluster (FPS dostu)
            event.renderer.box(
                cp.x * 16, yMin, cp.z * 16,
                cp.x * 16 + 16, yMax + 1, cp.z * 16 + 16,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0
            );
        }
    }

    private static class ClusterInfo {
        final int cx, cy, cz, count;
        ClusterInfo(int cx, int cy, int cz, int count) {
            this.cx = cx; this.cy = cy; this.cz = cz; this.count = count;
        }
    }
    }
