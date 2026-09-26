package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
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
        .description("Minimum Y level to render.")
        .defaultValue(-64)
        .min(-64)
        .max(320)
        .sliderMin(-64)
        .sliderMax(320)
        .build()
    );

    private final Setting<Integer> maxY = general.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level to render.")
        .defaultValue(320)
        .min(-64)
        .max(320)
        .sliderMin(-64)
        .sliderMax(320)
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
        .description("Delay between storage scans in milliseconds.")
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

    private final Setting<Boolean> tracers = renderGroup.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draws a tracer from the crosshair/player to each storage block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = renderGroup.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> chestColor = renderGroup.add(new ColorSetting.Builder()
        .name("chest-color")
        .defaultValue(new SettingColor(255, 160, 0, 255))
        .build()
    );

    private final Setting<SettingColor> barrelColor = renderGroup.add(new ColorSetting.Builder()
        .name("barrel-color")
        .defaultValue(new SettingColor(255, 160, 0, 255))
        .build()
    );

    private final Setting<SettingColor> shulkerColor = renderGroup.add(new ColorSetting.Builder()
        .name("shulker-color")
        .defaultValue(new SettingColor(180, 70, 255, 255))
        .build()
    );

    private final Setting<SettingColor> enderChestColor = renderGroup.add(new ColorSetting.Builder()
        .name("ender-chest-color")
        .defaultValue(new SettingColor(120, 0, 255, 255))
        .build()
    );

    private final Setting<SettingColor> hopperColor = renderGroup.add(new ColorSetting.Builder()
        .name("hopper-color")
        .defaultValue(new SettingColor(140, 140, 140, 255))
        .build()
    );

    private final Setting<SettingColor> tracerColor = renderGroup.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Optional tracer color. Set alpha to 0 to use each storage type's color.")
        .defaultValue(new SettingColor(255, 255, 255, 0))
        .build()
    );

    private final List<StorageEntry> storage = new ArrayList<>();
    private long lastScan;

    public StorageESP() {
        super(
            MazaCategory.INSTANCE,
            "maza-storage-esp",
            "Highlights storage blocks with Meteor-style colors and tracers."
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

        for (StorageEntry entry : storage) {
            if (entry == null || entry.pos == null) continue;

            try {
                BlockPos pos = entry.pos;

                if (pos.getY() < minY.get() || pos.getY() > maxY.get()) continue;

                int dx = (pos.getX() >> 4) - playerChunkX;
                int dz = (pos.getZ() >> 4) - playerChunkZ;
                if (Math.max(Math.abs(dx), Math.abs(dz)) > chunkRange) continue;

                SettingColor color = entry.color;

                event.renderer.box(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    pos.getX() + 1,
                    pos.getY() + 1,
                    pos.getZ() + 1,
                    color,
                    color,
                    shapeMode.get(),
                    0
                );

                if (tracers.get()) {
                    SettingColor line = tracerColor.get().a > 0 ? tracerColor.get() : color;

                    event.renderer.line(
                        RenderUtils.center.x,
                        RenderUtils.center.y,
                        RenderUtils.center.z,
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5,
                        line
                    );
                }
            } catch (Exception ignored) {
                // A bad/stale block entity must not take down the render loop.
            }
        }
    }

    private void safeScan() {
        try {
            scanLoadedChunks();
        } catch (Exception ignored) {
            // Keep the module alive if a chunk unloads during the scan.
        }
    }

    private void scanLoadedChunks() {
        if (mc.world == null || mc.player == null) return;

        int playerChunkX = mc.player.getChunkPos().x;
        int playerChunkZ = mc.player.getChunkPos().z;
        int chunkRange = range.get();

        List<StorageEntry> found = new ArrayList<>();

        for (int dx = -chunkRange; dx <= chunkRange; dx++) {
            for (int dz = -chunkRange; dz <= chunkRange; dz++) {
                if (mc.world == null || mc.player == null) return;

                try {
                    WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(
                        playerChunkX + dx,
                        playerChunkZ + dz
                    );

                    if (chunk == null) continue;

                    for (BlockEntity entity : chunk.getBlockEntities().values()) {
                        if (entity == null || entity.isRemoved()) continue;

                        SettingColor color = getColor(entity);
                        if (color == null) continue;

                        BlockPos pos = entity.getPos();
                        if (pos.getY() < minY.get() || pos.getY() > maxY.get()) continue;

                        found.add(new StorageEntry(pos.toImmutable(), color));
                    }
                } catch (Exception ignored) {
                    // Chunk may have unloaded between getWorldChunk and iteration.
                }
            }
        }

        storage.clear();
        storage.addAll(found);
    }

    private SettingColor getColor(BlockEntity entity) {
        if (entity instanceof ChestBlockEntity) {
            return chests.get() ? chestColor.get() : null;
        }

        if (entity instanceof BarrelBlockEntity) {
            return barrels.get() ? barrelColor.get() : null;
        }

        if (entity instanceof ShulkerBoxBlockEntity) {
            return shulkers.get() ? shulkerColor.get() : null;
        }

        if (entity instanceof EnderChestBlockEntity) {
            return enderChests.get() ? enderChestColor.get() : null;
        }

        if (entity instanceof HopperBlockEntity) {
            return hoppers.get() ? hopperColor.get() : null;
        }

        return null;
    }

    private static class StorageEntry {
        final BlockPos pos;
        final SettingColor color;

        StorageEntry(BlockPos pos, SettingColor color) {
            this.pos = pos;
            this.color = color;
        }
    }
}
