package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AirBypass extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y").description("Minimum Y level")
        .defaultValue(-64).min(-64).max(64).build());

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y").description("Maximum Y level")
        .defaultValue(0).min(-64).max(64).build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug").description("Print found cavity count per chunk")
        .defaultValue(true).build());

    private final Setting<Integer> renderRange = sgRender.add(new IntSetting.Builder()
        .name("render-range").description("Max blocks from player to render")
        .defaultValue(64).min(16).max(256).sliderRange(16, 256).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(255, 0, 0, 40)).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(255, 0, 0, 255)).build());

    private final Set<BlockPos> cavities = ConcurrentHashMap.newKeySet();

    public AirBypass() {
        super(MazaCategory.INSTANCE, "air-bypass", "Shows enclosed air pockets (caves/bases) below y=0");
    }

    @Override
    public void onActivate() {
        cavities.clear();
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
        cavities.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (event == null || event.chunk() == null) return;
        scan(event.chunk());
    }

    // sütun bazlı tarama: üstünde blok olan hava = boşluk (mağara/base)
    private void scan(WorldChunk chunk) {
        if (mc == null || mc.world == null || chunk == null) return;
        ChunkPos pos = chunk.getPos();
        int found = 0;

        int yMin = minY.get();
        int yMax = maxY.get();

        try {
            for (int x = 0; x < 16; x += 2) {
                for (int z = 0; z < 16; z += 2) {
                    boolean seenSolid = false;

                    for (int y = yMax; y >= yMin; y--) {
                        BlockPos p = new BlockPos(pos.x * 16 + x, y, pos.z * 16 + z);
                        boolean air = chunk.getBlockState(p).isAir();

                        if (!air) {
                            seenSolid = true;
                        } else if (seenSolid) {
                            // hava ama üstünde blok var = kapalı boşluk
                            if (cavities.size() < 100000) {
                                cavities.add(p);
                                found++;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (debug.get()) {
            info("Chunk %s | %d cavity blocks", pos, found);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc == null || mc.player == null || event == null || event.renderer == null) return;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        double maxDist = renderRange.get();
        double maxDistSq = maxDist * maxDist;

        for (BlockPos p : cavities) {
            double dx = p.getX() + 0.5 - px;
            double dy = p.getY() + 0.5 - py;
            double dz = p.getZ() + 0.5 - pz;
            if (dx * dx + dy * dy + dz * dz > maxDistSq) continue;

            try {
                event.renderer.box(
                    p.getX(), p.getY(), p.getZ(),
                    p.getX() + 1, p.getY() + 1, p.getZ() + 1,
                    sideColor.get(), lineColor.get(), shapeMode.get(), 0
                );
            } catch (Exception ignored) {}
        }
    }
}
