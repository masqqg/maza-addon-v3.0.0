package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpawnerESP extends Module {
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup render = settings.createGroup("Render");

    private final Setting<Integer> range = general.add(new IntSetting.Builder()
        .name("range")
        .defaultValue(8).min(1).sliderMax(32).build());

    private final Setting<Integer> topY = general.add(new IntSetting.Builder()
        .name("top-y")
        .description("Top of the vertical white marker.")
        .defaultValue(316).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Boolean> verticalBar = render.add(new BoolSetting.Builder()
        .name("vertical-bar").defaultValue(true).build());

    private final Setting<SettingColor> fill = render.add(new ColorSetting.Builder()
        .name("fill")
        .defaultValue(new SettingColor(255, 255, 255, 55)).build());

    private final Setting<SettingColor> line = render.add(new ColorSetting.Builder()
        .name("line")
        .defaultValue(new SettingColor(255, 255, 255, 180)).build());

    private final Map<Long, BlockPos> spawners = new HashMap<>();
    private long lastScan;

    public SpawnerESP() {
        super(MazaCategory.INSTANCE, "spawner-esp", "Shows mob spawners as a 4x4 horizontal marker with a white vertical marker up to Y 316.");
    }

    @Override
    public void onActivate() {
        spawners.clear();
        lastScan = 0L;
        scanLoadedChunks();
    }

    @Override
    public void onDeactivate() {
        spawners.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastScan >= 500L) {
            scanLoadedChunks();
            lastScan = now;
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (event.chunk() == null) return;
        scanChunk(event.chunk());
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null || event.pos == null) return;
        if (event.oldState.isOf(Blocks.SPAWNER) || event.newState.isOf(Blocks.SPAWNER)) {
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(event.pos.getX() >> 4, event.pos.getZ() >> 4);
            if (chunk != null) scanChunk(chunk);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;

        for (BlockPos pos : new ArrayList<>(spawners.values())) {
            if (pos == null) continue;
            int dx = (pos.getX() >> 4) - pcx;
            int dz = (pos.getZ() >> 4) - pcz;
            if (Math.max(Math.abs(dx), Math.abs(dz)) > range.get()) continue;

            // Exact 4x4 horizontal marker centered on the spawner.
            double minX = pos.getX() - 1.5;
            double maxX = pos.getX() + 2.5;
            double minZ = pos.getZ() - 1.5;
            double maxZ = pos.getZ() + 2.5;
            double y = pos.getY() + 1.02;

            event.renderer.quad(
                minX, y, minZ,
                maxX, y, minZ,
                maxX, y, maxZ,
                minX, y, maxZ,
                fill.get()
            );

            if (verticalBar.get()) {
                double cx = pos.getX() + 0.5;
                double cz = pos.getZ() + 0.5;
                event.renderer.line(cx, y, cz, cx, topY.get(), cz, line.get());
            }
        }
    }

    private void scanLoadedChunks() {
        if (mc.world == null || mc.player == null) return;

        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;
        int r = range.get();
        Map<Long, BlockPos> found = new HashMap<>();

        for (int x = pcx - r; x <= pcx + r; x++) {
            for (int z = pcz - r; z <= pcz + r; z++) {
                if (Math.max(Math.abs(x - pcx), Math.abs(z - pcz)) > r) continue;
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x, z);
                if (chunk != null) collectSpawners(chunk, found);
            }
        }

        spawners.clear();
        spawners.putAll(found);
    }

    private void scanChunk(WorldChunk chunk) {
        if (chunk == null) return;
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;
        if (mc.player != null && Math.max(Math.abs(cx - mc.player.getChunkPos().x), Math.abs(cz - mc.player.getChunkPos().z)) > range.get()) return;

        Map<Long, BlockPos> found = new HashMap<>();
        for (Map.Entry<Long, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            BlockEntity entity = entry.getValue();
            if (entity instanceof MobSpawnerBlockEntity && !entity.isRemoved()) {
                BlockPos pos = entity.getPos();
                found.put(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()), pos.toImmutable());
            }
        }

        for (Long key : new ArrayList<>(spawners.keySet())) {
            BlockPos old = spawners.get(key);
            if (old != null && (old.getX() >> 4) == cx && (old.getZ() >> 4) == cz) spawners.remove(key);
        }
        spawners.putAll(found);
    }

    private void collectSpawners(WorldChunk chunk, Map<Long, BlockPos> found) {
        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            if (entity instanceof MobSpawnerBlockEntity && !entity.isRemoved()) {
                BlockPos pos = entity.getPos();
                found.put(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()), pos.toImmutable());
            }
        }
    }
}
