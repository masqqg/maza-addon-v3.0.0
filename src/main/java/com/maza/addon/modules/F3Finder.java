package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.DropperBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class F3Finder extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> renderRange = sgRender.add(new IntSetting.Builder()
        .name("render-range").description("Max chunks to render")
        .defaultValue(4).min(1).max(16).sliderRange(1, 16).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(0, 120, 255, 30)).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(0, 120, 255, 255)).build());

    private final Set<ChunkPos> susChunks = ConcurrentHashMap.newKeySet();

    public F3Finder() {
        super(MazaCategory.INSTANCE, "f3-finder", "Marks chunks with chests/shulkers/spawners (blue)");
    }

    @Override
    public void onActivate() {
        susChunks.clear();
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
        susChunks.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (event == null || event.chunk() == null) return;
        scan(event.chunk());
    }

    private void scan(WorldChunk chunk) {
        if (chunk == null) return;
        try {
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be == null) continue;
                if (be instanceof ShulkerBoxBlockEntity ||
                    be instanceof EnderChestBlockEntity ||
                    be instanceof MobSpawnerBlockEntity ||
                    be instanceof ChestBlockEntity ||
                    be instanceof BarrelBlockEntity ||
                    be instanceof HopperBlockEntity ||
                    be instanceof DispenserBlockEntity ||
                    be instanceof DropperBlockEntity ||
                    be instanceof BeaconBlockEntity) {
                    susChunks.add(chunk.getPos());
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc == null || mc.player == null || event == null || event.renderer == null) return;

        int pcx = (int) Math.floor(mc.player.getX() / 16);
        int pcz = (int) Math.floor(mc.player.getZ() / 16);
        int max = renderRange.get();

        for (ChunkPos cp : susChunks) {
            int dx = cp.x - pcx;
            int dz = cp.z - pcz;
            if (Math.abs(dx) > max || Math.abs(dz) > max) continue;

            try {
                event.renderer.box(
                    cp.x * 16, -64, cp.z * 16,
                    cp.x * 16 + 16, 128, cp.z * 16 + 16,
                    sideColor.get(), lineColor.get(), ShapeMode.Both, 0
                );
            } catch (Exception ignored) {}
        }
    }
            }
