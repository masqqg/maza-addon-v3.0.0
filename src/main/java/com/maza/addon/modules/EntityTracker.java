package com.maza.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EntityTracker extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender  = settings.createGroup("Render");

    private final Setting<Boolean> trackPlayers = sgGeneral.add(new BoolSetting.Builder()
        .name("track-players")
        .description("Track player spawns.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> trackMobs = sgGeneral.add(new BoolSetting.Builder()
        .name("track-mobs")
        .description("Track mob spawns.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> trackItems = sgGeneral.add(new BoolSetting.Builder()
        .name("track-items")
        .description("Track item spawns.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> trackProjectiles = sgGeneral.add(new BoolSetting.Builder()
        .name("track-projectiles")
        .description("Track projectile spawns.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> chatAlert = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-alert")
        .description("Chat message on entity spawn.")
        .defaultValue(true)
        .build());

    private final Setting<Double> cooldown = sgGeneral.add(new DoubleSetting.Builder()
        .name("cooldown")
        .description("Seconds between alerts.")
        .defaultValue(5.0)
        .min(0.0)
        .sliderMax(30.0)
        .build());

    private final Setting<Double> decayTime = sgGeneral.add(new DoubleSetting.Builder()
        .name("decay-time")
        .description("Seconds before marker fades.")
        .defaultValue(15.0)
        .min(5.0)
        .sliderMax(60.0)
        .build());

    private final Setting<SettingColor> colorPlayer = sgRender.add(new ColorSetting.Builder()
        .name("color-player")
        .description("Player spawn color.")
        .defaultValue(new SettingColor(255, 0, 0, 200))
        .build());

    private final Setting<SettingColor> colorMob = sgRender.add(new ColorSetting.Builder()
        .name("color-mob")
        .description("Mob spawn color.")
        .defaultValue(new SettingColor(255, 255, 0, 180))
        .build());

    private final Setting<SettingColor> colorItem = sgRender.add(new ColorSetting.Builder()
        .name("color-item")
        .description("Item spawn color.")
        .defaultValue(new SettingColor(0, 255, 255, 150))
        .build());

    private final Setting<SettingColor> colorProjectile = sgRender.add(new ColorSetting.Builder()
        .name("color-projectile")
        .description("Projectile spawn color.")
        .defaultValue(new SettingColor(255, 100, 255, 180))
        .build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Double> renderDistance = sgRender.add(new DoubleSetting.Builder()
        .name("render-distance")
        .description("Max distance to render.")
        .defaultValue(256.0)
        .min(16.0)
        .sliderMax(512.0)
        .build());

    private final Map<BlockPos, TrackedEntity> tracked = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAlert = new ConcurrentHashMap<>();

    private static class TrackedEntity {
        String type;
        long spawnTime;
        double x, y, z;

        TrackedEntity(String type, double x, double y, double z) {
            this.type = type;
            this.spawnTime = System.currentTimeMillis();
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public EntityTracker() {
        super(Categories.Misc, "entity-tracker", "Tracks entity spawns in world.");
    }

    @Override
    public void onActivate() {
        tracked.clear();
        lastAlert.clear();
    }

    @Override
    public void onDeactivate() {
        tracked.clear();
        lastAlert.clear();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;

        if (event.packet instanceof EntitySpawnS2CPacket p) {
            if (p.getY() < -64) return;
            handleEntitySpawn(p.getX(), p.getY(), p.getZ(), p.getEntityType());
        }
    }

    private void handleEntitySpawn(double x, double y, double z, EntityType<?> type) {
        String typeName = type.toString().toLowerCase();
        BlockPos pos = new BlockPos((int)x, (int)y, (int)z);

        if (typeName.contains("player") && trackPlayers.get()) {
            addTracked(pos, "player", x, y, z);
        }
        else if ((typeName.contains("zombie") || typeName.contains("skeleton") ||
                  typeName.contains("creeper") || typeName.contains("spider")) && trackMobs.get()) {
            addTracked(pos, "mob", x, y, z);
        }
        else if (typeName.contains("item") && trackItems.get()) {
            addTracked(pos, "item", x, y, z);
        }
        else if ((typeName.contains("arrow") || typeName.contains("snowball") ||
                  typeName.contains("ender_pearl") || typeName.contains("potion")) && trackProjectiles.get()) {
            addTracked(pos, "projectile", x, y, z);
        }
    }

    private void addTracked(BlockPos pos, String type, double x, double y, double z) {
        tracked.put(pos, new TrackedEntity(type, x, y, z));

        long now = System.currentTimeMillis();
        long last = lastAlert.getOrDefault(type, 0L);
        if (now - last < cooldown.get() * 1000) return;
        lastAlert.put(type, now);

        if (chatAlert.get()) {
            ChatUtils.info("EntityTracker",
                "%s at [%.0f, %.0f, %.0f]",
                type.toUpperCase(), x, y, z);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (tracked.isEmpty()) return;
        if (mc.player == null || mc.world == null) return;

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        long now = System.currentTimeMillis();
        double decayMs = decayTime.get() * 1000;

        tracked.entrySet().removeIf(e -> now - e.getValue().spawnTime > decayMs);

        for (Map.Entry<BlockPos, TrackedEntity> entry : tracked.entrySet()) {
            BlockPos pos = entry.getKey();
            TrackedEntity te = entry.getValue();

            double dist = Math.sqrt(
                Math.pow(te.x - cam.x, 2) +
                Math.pow(te.z - cam.z, 2)
            );
            if (dist > renderDistance.get()) continue;

            SettingColor sc;
            switch (te.type) {
                case "player": sc = colorPlayer.get(); break;
                case "mob": sc = colorMob.get(); break;
                case "item": sc = colorItem.get(); break;
                case "projectile": sc = colorProjectile.get(); break;
                default: sc = colorMob.get();
            }

            Color side = new Color(sc.r, sc.g, sc.b, (int)(sc.a * 0.4));
            Color line = new Color(sc.r, sc.g, sc.b, sc.a);

            double x1 = pos.getX() - 0.5;
            double y1 = Math.max(-64, pos.getY());
            double z1 = pos.getZ() - 0.5;
            double x2 = x1 + 1;
            double y2 = y1 + 2;
            double z2 = z1 + 1;

            event.renderer.box(x1, y1, z1, x2, y2, z2, side, line, shapeMode.get(), 0);
        }
    }
}
