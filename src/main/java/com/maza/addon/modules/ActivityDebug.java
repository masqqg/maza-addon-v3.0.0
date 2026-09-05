package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ActivityDebug extends Module {

    private final SettingGroup sgPlayerDebug = settings.createGroup("Player Debug");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> showEntityLooks = sgPlayerDebug.add(new BoolSetting.Builder()
        .name("show-entity-looks").description("Show where entities look (F3+B style)")
        .defaultValue(true).build());

    private final Setting<Boolean> showPlayers = sgPlayerDebug.add(new BoolSetting.Builder()
        .name("show-players").description("Show player look direction")
        .defaultValue(true).build());

    private final Setting<Boolean> showItemFrames = sgPlayerDebug.add(new BoolSetting.Builder()
        .name("show-item-frames").description("Show item frame facing")
        .defaultValue(true).build());

    private final Setting<Boolean> showArmorStands = sgPlayerDebug.add(new BoolSetting.Builder()
        .name("show-armor-stands").description("Show armor stand facing")
        .defaultValue(true).build());

    private final Setting<Boolean> showMinecarts = sgPlayerDebug.add(new BoolSetting.Builder()
        .name("show-minecarts").description("Show minecart/hopper minecart facing")
        .defaultValue(true).build());

    private final Setting<Boolean> showSpawners = sgPlayerDebug.add(new BoolSetting.Builder()
        .name("show-spawners").description("Show mobs with nametags (player spawners)")
        .defaultValue(true).build());

    private final Setting<Boolean> markChunkRed = sgPlayerDebug.add(new BoolSetting.Builder()
        .name("mark-chunk-red").description("Mark chunk red when entity found")
        .defaultValue(true).build());

    private final Setting<Boolean> ignoreY = sgPlayerDebug.add(new BoolSetting.Builder()
        .name("ignore-y-limit").description("Show entities even below y=0")
        .defaultValue(true).build());

    private final Setting<Double> lookLineLength = sgPlayerDebug.add(new DoubleSetting.Builder()
        .name("look-line-length").defaultValue(3.0).min(0.5).max(10.0).sliderRange(0.5, 10.0).build());

    private final Setting<Double> renderRange = sgPlayerDebug.add(new DoubleSetting.Builder()
        .name("render-range").defaultValue(128.0).min(16.0).max(512.0).sliderRange(16.0, 512.0).build());

    private final Setting<SettingColor> playerLookColor = sgRender.add(new ColorSetting.Builder()
        .name("player-look-color").defaultValue(new SettingColor(0, 255, 255, 255)).build());

    private final Setting<SettingColor> entityLookColor = sgRender.add(new ColorSetting.Builder()
        .name("entity-look-color").defaultValue(new SettingColor(255, 255, 0, 255)).build());

    private final Setting<SettingColor> spawnerColor = sgRender.add(new ColorSetting.Builder()
        .name("spawner-color").defaultValue(new SettingColor(255, 0, 255, 255)).build());

    private final Setting<SettingColor> redSide = sgRender.add(new ColorSetting.Builder()
        .name("red-side").defaultValue(new SettingColor(255, 0, 0, 30)).build());

    private final Setting<SettingColor> redLine = sgRender.add(new ColorSetting.Builder()
        .name("red-line").defaultValue(new SettingColor(255, 0, 0, 255)).build());

    private final Set<ChunkPos> redChunks = ConcurrentHashMap.newKeySet();

    public ActivityDebug() {
        super(MazaCategory.INSTANCE, "activity-debug", "Entity look tracking + red chunk marking (no chat)");
    }

    @Override
    public void onActivate() {
        redChunks.clear();
    }

    @Override
    public void onDeactivate() {
        redChunks.clear();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc == null || mc.world == null || mc.player == null) return;
        if (event == null || event.renderer == null) return;

        double maxDist = renderRange.get();
        double maxDistSq = maxDist * maxDist;

        Iterable<Entity> entities;
        try {
            entities = mc.world.getEntities();
        } catch (Exception e) {
            return;
        }
        if (entities == null) return;

        for (Entity entity : entities) {
            if (entity == null || entity == mc.player) continue;

            double distSq = entity.squaredDistanceTo(mc.player);
            if (distSq > maxDistSq) continue;
            if (!ignoreY.get() && entity.getY() < 0) continue;

            boolean show = false;
            boolean isPlayer = entity instanceof PlayerEntity;
            boolean isSpawner = false;

            if (isPlayer && showPlayers.get()) show = true;
            else if (entity instanceof ItemFrameEntity && showItemFrames.get()) show = true;
            else if (entity instanceof ArmorStandEntity && showArmorStands.get()) show = true;
            else if ((entity instanceof MinecartEntity || entity instanceof HopperMinecartEntity) && showMinecarts.get()) show = true;
            else if (showSpawners.get() && hasNametag(entity)) {
                show = true;
                isSpawner = true;
            }

            if (!show) continue;

            if (markChunkRed.get()) {
                redChunks.add(entity.getChunkPos());
            }

            if (showEntityLooks.get()) {
                try {
                    Vec3d eye = entity.getEyePos();
                    Vec3d look = entity.getRotationVector().multiply(lookLineLength.get());
                    Vec3d end = eye.add(look);

                    var color = isPlayer ? playerLookColor.get() :
                               isSpawner ? spawnerColor.get() :
                               entityLookColor.get();

                    event.renderer.line(eye.x, eye.y, eye.z, end.x, end.y, end.z, color);
                } catch (Exception ignored) {}
            }
        }

        // kırmızı chunk kutuları
        int pcx = (int) Math.floor(mc.player.getX() / 16);
        int pcz = (int) Math.floor(mc.player.getZ() / 16);

        for (ChunkPos cp : redChunks) {
            int dx = cp.x - pcx;
            int dz = cp.z - pcz;
            if (Math.abs(dx) > 8 || Math.abs(dz) > 8) continue;

            try {
                event.renderer.box(
                    cp.x * 16, -64, cp.z * 16,
                    cp.x * 16 + 16, 128, cp.z * 16 + 16,
                    redSide.get(), redLine.get(), ShapeMode.Both, 0
                );
            } catch (Exception ignored) {}
        }
    }

    private boolean hasNametag(Entity entity) {
        if (entity == null) return false;
        Text customName = entity.getCustomName();
        return customName != null && !customName.getString().isEmpty();
    }
}
