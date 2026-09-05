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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    private final Setting<Integer> renderRange = sgRender.add(new IntSetting.Builder()
        .name("render-range").description("Max blocks from player to render")
        .defaultValue(64).min(16).max(256).sliderRange(16, 256).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(255, 0, 0, 40)).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(255, 0, 0, 255)).build());

    private final Set<BlockPos> airBlocks = ConcurrentHashMap.newKeySet();

    public AirBypass() {
        super(MazaCategory.INSTANCE, "air-bypass", "Shows hollow areas block by block");
    }

    @Override
    public void onActivate() {
        airBlocks.clear();
        if (mc == null || mc.world == null || mc.player == null) return;
        ChunkPos here = mc.player.getChunkPos();
        for (int cx = here.x - 4; cx <= here.x + 4; cx++) {
            for (int cz = here.z - 4; cz <= here.z + 4; cz++) {
                WorldChunk c = mc.world.getChunk(cx, cz);
                if (c != null) scan(c);
            }
        }
    }

    @Override
    public void onDeactivate() {
        airBlocks.clear();
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

        for (int y = yMin; y <= yMax; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    try {
                        BlockPos p = new BlockPos(pos.x * 16 + x, y, pos.z * 16 + z);
                        if (chunk.getBlockState(p).isAir()) {
                            airBlocks.add(p);
                        }
                    } catch (Exception ignored) {}
                }
            }
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

        for (BlockPos p : airBlocks) {
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
