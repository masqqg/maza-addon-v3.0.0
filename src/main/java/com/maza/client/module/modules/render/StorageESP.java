package com.maza.client.module.modules.render;

import com.maza.client.event.EventHandler;
import com.maza.client.event.Render3DEvent;
import com.maza.client.module.Category;
import com.maza.client.module.Module;
import com.maza.client.setting.*;
import com.maza.client.utils.Render3DUtils;
import com.maza.client.utils.ShapeMode;
import net.minecraft.block.entity.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

public class StorageESP extends Module {
    public final IntSetting minY = addSetting(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level to render.")
        .defaultValue(-64).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    public final IntSetting maxY = addSetting(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level to render.")
        .defaultValue(320).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    public final IntSetting range = addSetting(new IntSetting.Builder()
        .name("range")
        .description("Maximum chunk distance to scan.")
        .defaultValue(4).min(1).max(16).sliderMin(1).sliderMax(16).build());

    public final IntSetting scanDelay = addSetting(new IntSetting.Builder()
        .name("scan-delay")
        .description("Delay between storage scans in milliseconds.")
        .defaultValue(500).min(100).max(5000).sliderMin(100).sliderMax(5000).build());

    public final BoolSetting chests = addSetting(new BoolSetting.Builder()
        .name("chests").defaultValue(true).build());

    public final BoolSetting barrels = addSetting(new BoolSetting.Builder()
        .name("barrels").defaultValue(true).build());

    public final BoolSetting shulkers = addSetting(new BoolSetting.Builder()
        .name("shulkers").defaultValue(true).build());

    public final BoolSetting enderChests = addSetting(new BoolSetting.Builder()
        .name("ender-chests").defaultValue(true).build());

    public final BoolSetting hoppers = addSetting(new BoolSetting.Builder()
        .name("hoppers").defaultValue(true).build());

    public final BoolSetting tracers = addSetting(new BoolSetting.Builder()
        .name("tracers")
        .description("Draws a tracer from the player to each storage block.")
        .defaultValue(true).build());

    public final EnumSetting<ShapeMode> shapeMode = addSetting(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());

    public final ColorSetting chestColor = addSetting(new ColorSetting.Builder()
        .name("chest-color").defaultValue(new SettingColor(255, 160, 0, 100)).build());

    public final ColorSetting barrelColor = addSetting(new ColorSetting.Builder()
        .name("barrel-color").defaultValue(new SettingColor(255, 160, 0, 100)).build());

    public final ColorSetting shulkerColor = addSetting(new ColorSetting.Builder()
        .name("shulker-color").defaultValue(new SettingColor(180, 70, 255, 100)).build());

    public final ColorSetting enderChestColor = addSetting(new ColorSetting.Builder()
        .name("ender-chest-color").defaultValue(new SettingColor(120, 0, 255, 100)).build());

    public final ColorSetting hopperColor = addSetting(new ColorSetting.Builder()
        .name("hopper-color").defaultValue(new SettingColor(140, 140, 140, 100)).build());

    public final ColorSetting tracerColor = addSetting(new ColorSetting.Builder()
        .name("tracer-color").defaultValue(new SettingColor(255, 255, 255, 0)).build());

    private final List<StorageEntry> storage = new ArrayList<>();
    private long lastScan;

    public StorageESP() {
        super("StorageESP", "Highlights storage blocks with ESP boxes and tracers", Category.RENDER);
    }

    @Override
    public void onEnable() {
        storage.clear();
        lastScan = 0L;
    }

    @Override
    public void onDisable() {
        storage.clear();
    }

    @EventHandler
    public void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        if (now - lastScan >= scanDelay.get()) {
            safeScan();
            lastScan = now;
        }

        int playerChunkX = mc.player.getChunkPos().x;
        int playerChunkZ = mc.player.getChunkPos().z;
        int chunkRange = range.get();

        Vec3d eyePos = mc.player.getEyePos();

        for (StorageEntry entry : storage) {
            if (entry == null || entry.pos == null) continue;

            try {
                BlockPos pos = entry.pos;

                if (pos.getY() < minY.get() || pos.getY() > maxY.get()) continue;

                int dx = (pos.getX() >> 4) - playerChunkX;
                int dz = (pos.getZ() >> 4) - playerChunkZ;
                if (Math.max(Math.abs(dx), Math.abs(dz)) > chunkRange) continue;

                SettingColor color = entry.color;
                Render3DUtils.drawBlock(pos, color, color, shapeMode.get());

                if (tracers.get()) {
                    SettingColor line = tracerColor.get().a > 0 ? tracerColor.get() : color;
                    Render3DUtils.drawLine(eyePos, new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), line, 1.5f);
                }
            } catch (Exception ignored) {}
        }
    }

    private void safeScan() {
        try {
            scanLoadedChunks();
        } catch (Exception ignored) {}
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
                    WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(playerChunkX + dx, playerChunkZ + dz);
                    if (chunk == null) continue;

                    for (BlockEntity entity : chunk.getBlockEntities().values()) {
                        if (entity == null || entity.isRemoved()) continue;

                        SettingColor color = getColor(entity);
                        if (color == null) continue;

                        BlockPos pos = entity.getPos();
                        if (pos.getY() < minY.get() || pos.getY() > maxY.get()) continue;

                        found.add(new StorageEntry(pos.toImmutable(), color));
                    }
                } catch (Exception ignored) {}
            }
        }

        storage.clear();
        storage.addAll(found);
    }

    private SettingColor getColor(BlockEntity entity) {
        if (entity instanceof ChestBlockEntity) return chests.get() ? chestColor.get() : null;
        if (entity instanceof BarrelBlockEntity) return barrels.get() ? barrelColor.get() : null;
        if (entity instanceof ShulkerBoxBlockEntity) return shulkers.get() ? shulkerColor.get() : null;
        if (entity instanceof EnderChestBlockEntity) return enderChests.get() ? enderChestColor.get() : null;
        if (entity instanceof HopperBlockEntity) return hoppers.get() ? hopperColor.get() : null;
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
