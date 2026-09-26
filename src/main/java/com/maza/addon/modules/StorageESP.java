package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

public class StorageESP extends Module {
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup storageGroup = settings.createGroup("Storage");
    private final SettingGroup renderGroup = settings.createGroup("Render");

    private final Setting<Integer> minY = general.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level.")
        .defaultValue(-64)
        .min(-64)
        .max(0)
        .sliderMin(-64)
        .sliderMax(0)
        .build()
    );

    private final Setting<Integer> maxY = general.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level.")
        .defaultValue(0)
        .min(-64)
        .max(0)
        .sliderMin(-64)
        .sliderMax(0)
        .build()
    );

    private final Setting<Integer> range = general.add(new IntSetting.Builder()
        .name("range")
        .description("Maximum chunk distance to scan.")
        .defaultValue(4)
        .min(1)
        .max(16)
        .sliderMin(1)
        .sliderMax(16)
        .build()
    );

    private final Setting<Integer> scanDelay = general.add(new IntSetting.Builder()
        .name("scan-delay")
        .description("Delay between scans in milliseconds.")
        .defaultValue(500)
        .min(100)
        .max(5000)
        .sliderMin(100)
        .sliderMax(5000)
        .build()
    );

    private final Setting<Boolean> chests = storageGroup.add(new BoolSetting.Builder()
        .name("chests")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> barrels = storageGroup.add(new BoolSetting.Builder()
        .name("barrels")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shulkers = storageGroup.add(new BoolSetting.Builder()
        .name("shulkers")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enderChests = storageGroup.add(new BoolSetting.Builder()
        .name("ender-chests")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hoppers = storageGroup.add(new BoolSetting.Builder()
        .name("hoppers")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = renderGroup.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> sideColor =
        renderGroup.add(
            new ColorSetting.Builder()
                .name("side-color")
                .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(0, 255, 255, 45))
                .build()
        );

    private final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> lineColor =
        renderGroup.add(
            new ColorSetting.Builder()
                .name("line-color")
                .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(0, 255, 255, 255))
                .build()
        );

    private final List<BlockPos> storage = new ArrayList<>();

    private long lastScan;

    public StorageESP() {
        super(
            MazaCategory.INSTANCE,
            "maza-storage-esp",
            "Highlights storage blocks without constantly scanning the world."
        );
    }

    @Override
    public void onActivate() {
        storage.clear();
        lastScan = 0L;
    }

    @Override
    public void onDeactivate() {
        storage.clear();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();

        if (now - lastScan >= scanDelay.get()) {
            safeScan();
            lastScan = now;
        }

        int playerChunkX = mc.player.getChunkPos().x;
        int playerChunkZ = mc.player.getChunkPos().z;
        int chunkRange = range.get();

        for (BlockPos pos : storage) {
            if (pos == null) continue;

            try {
                if (pos.getY() < minY.get() || pos.getY() > maxY.get()) {
                    continue;
                }

                int dx = (pos.getX() >> 4) - playerChunkX;
                int dz = (pos.getZ() >> 4) - playerChunkZ;

                if (Math.max(Math.abs(dx), Math.abs(dz)) > chunkRange) {
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
                    shapeMode.get(),
                    0
                );
            } catch (Exception ignored) {
            }
        }
    }

    private void safeScan() {
        try {
            scanLoadedChunks();
        } catch (Exception ignored) {
        }
    }

    private void scanLoadedChunks() {
        if (mc.world == null || mc.player == null) return;

        int playerChunkX = mc.player.getChunkPos().x;
        int playerChunkZ = mc.player.getChunkPos().z;
        int chunkRange = range.get();

        List<BlockPos> found = new ArrayList<>();

        for (int dx = -chunkRange; dx <= chunkRange; dx++) {
            for (int dz = -chunkRange; dz <= chunkRange; dz++) {

                if (mc.world == null || mc.player == null) return;

                try {
                    WorldChunk chunk = mc.world.getChunkManager()
                        .getWorldChunk(
                            playerChunkX + dx,
                            playerChunkZ + dz
                        );

                    if (chunk == null) continue;

                    for (BlockEntity entity : chunk.getBlockEntities().values()) {
                        if (entity == null || entity.isRemoved()) continue;

                        if (!isStorage(entity)) continue;

                        BlockPos pos = entity.getPos();

                        if (pos.getY() < minY.get() || pos.getY() > maxY.get()) {
                            continue;
                        }

                        found.add(pos.toImmutable());
                    }
                } catch (Exception ignored) {
                }
            }
        }

        storage.clear();
        storage.addAll(found);
    }

    private boolean isStorage(BlockEntity entity) {
        if (chests.get() && entity instanceof ChestBlockEntity) {
            return true;
        }

        if (barrels.get() && entity instanceof BarrelBlockEntity) {
            return true;
        }

        if (shulkers.get() && entity instanceof ShulkerBoxBlockEntity) {
            return true;
        }

        if (enderChests.get() && entity instanceof EnderChestBlockEntity) {
            return true;
        }

        if (hoppers.get() && entity instanceof HopperBlockEntity) {
            return true;
        }

        return false;
    }
}
