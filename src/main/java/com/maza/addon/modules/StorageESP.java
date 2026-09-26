package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StorageESP extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> minY = sgGeneral.add(
        new IntSetting.Builder()
            .name("min-y")
            .description("Storage ESP minimum Y.")
            .defaultValue(-64)
            .min(-64)
            .max(0)
            .sliderRange(-64, 0)
            .build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(
        new IntSetting.Builder()
            .name("max-y")
            .description("Storage ESP maximum Y.")
            .defaultValue(0)
            .min(-64)
            .max(0)
            .sliderRange(-64, 0)
            .build()
    );

    private final Setting<Integer> renderRange = sgGeneral.add(
        new IntSetting.Builder()
            .name("render-range")
            .defaultValue(8)
            .min(1)
            .max(32)
            .sliderRange(1, 16)
            .build()
    );

    private final Setting<Boolean> chests = sgGeneral.add(
        new BoolSetting.Builder()
            .name("chests")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> barrels = sgGeneral.add(
        new BoolSetting.Builder()
            .name("barrels")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> shulkers = sgGeneral.add(
        new BoolSetting.Builder()
            .name("shulkers")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> enderChests = sgGeneral.add(
        new BoolSetting.Builder()
            .name("ender-chests")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> hoppers = sgGeneral.add(
        new BoolSetting.Builder()
            .name("hoppers")
            .defaultValue(true)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(
        new ColorSetting.Builder()
            .name("side-color")
            .defaultValue(new SettingColor(0, 120, 255, 25))
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(
        new ColorSetting.Builder()
            .name("line-color")
            .defaultValue(new SettingColor(0, 120, 255, 255))
            .build()
    );

    private final Map<BlockPos, BlockEntity> storages =
        new ConcurrentHashMap<>();

    public StorageESP() {
        super(
            MazaCategory.INSTANCE,
            "storage-esp",
            "Shows storage blocks between Y -64 and Y 0."
        );
    }

    @Override
    public void onActivate() {

        storages.clear();

        if (mc == null ||
            mc.world == null ||
            mc.player == null) {
            return;
        }

        scanLoadedChunks();
    }

    @Override
    public void onDeactivate() {
        storages.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {

        if (event == null ||
            event.chunk() == null) {
            return;
        }

        scanChunk(event.chunk());
    }

    private void scanLoadedChunks() {

        if (mc.world == null) return;

        ChunkPos center =
            mc.player.getChunkPos();

        int radius =
            renderRange.get();

        for (int cx = center.x - radius;
             cx <= center.x + radius;
             cx++) {

            for (int cz = center.z - radius;
                 cz <= center.z + radius;
                 cz++) {

                WorldChunk chunk =
                    mc.world.getChunkManager()
                        .getWorldChunk(cx, cz);

                if (chunk != null) {
                    scanChunk(chunk);
                }
            }
        }
    }

    private void scanChunk(WorldChunk chunk) {

        if (chunk == null) return;

        try {

            for (BlockEntity entity :
                chunk.getBlockEntities().values()) {

                if (entity == null) continue;

                BlockPos pos =
                    entity.getPos();

                int y = pos.getY();

                /*
                 * Tam olarak:
                 *
                 * -64 <= Y <= 0
                 */
                if (y < minY.get() ||
                    y > maxY.get()) {
                    continue;
                }

                if (isStorage(entity)) {
                    storages.put(pos.toImmutable(), entity);
                }
            }

        } catch (Exception ignored) {
        }
    }

    private boolean isStorage(BlockEntity entity) {

        if (chests.get() &&
            entity instanceof ChestBlockEntity) {
            return true;
        }

        if (barrels.get() &&
            entity instanceof BarrelBlockEntity) {
            return true;
        }

        if (shulkers.get() &&
            entity instanceof ShulkerBoxBlockEntity) {
            return true;
        }

        if (enderChests.get() &&
            entity instanceof EnderChestBlockEntity) {
            return true;
        }

        if (hoppers.get() &&
            entity instanceof HopperBlockEntity) {
            return true;
        }

        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {

        if (mc == null ||
            mc.player == null ||
            event == null ||
            event.renderer == null) {
            return;
        }

        int range =
            renderRange.get();

        double px =
            mc.player.getX();

        double pz =
            mc.player.getZ();

        for (BlockPos pos : storages.keySet()) {

            double dx =
                pos.getX() - px;

            double dz =
                pos.getZ() - pz;

            if (dx * dx + dz * dz >
                (range * 16.0) *
                (range * 16.0)) {
                continue;
            }

            event.renderer.box(
                pos.getX(),
                pos.getY(),
                pos.getZ(),

                pos.getX() + 1,
                pos.getY() + 1,
                pos.getZ() + 1,

                sideColor.get(),
                lineColor.get(),

                ShapeMode.Both,
                0
            );
        }
    }
}
