package com.maza.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MovementDebug extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender  = settings.createGroup("Render");

    private final Setting<Boolean> chatAlert = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-alert")
        .description("Chat message on detection.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> filterSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("filter-self")
        .description("Ignore your own activity.")
        .defaultValue(false)
        .build());

    private final Setting<Double> decayTimeSec = sgGeneral.add(new DoubleSetting.Builder()
        .name("decay-time")
        .description("Seconds before activity fades.")
        .defaultValue(30.0)
        .min(5.0)
        .sliderMax(120.0)
        .build());

    private final Setting<Integer> maxChunks = sgGeneral.add(new IntSetting.Builder()
        .name("max-chunks")
        .description("Max chunks to track.")
        .defaultValue(50)
        .min(10)
        .sliderMax(200)
        .build());

    private final Setting<Boolean> burstEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("burst-enabled")
        .description("Highlight burst activity.")
        .defaultValue(true)
        .build());

    private final Setting<Double> burstThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("burst-threshold")
        .description("Score threshold for burst.")
        .defaultValue(15.0)
        .min(5.0)
        .sliderMax(50.0)
        .build());

    private final Setting<SettingColor> colorLow = sgRender.add(new ColorSetting.Builder()
        .name("color-low")
        .description("Low score color.")
        .defaultValue(new SettingColor(0, 255, 0, 120))
        .build());

    private final Setting<SettingColor> colorMid = sgRender.add(new ColorSetting.Builder()
        .name("color-mid")
        .description("Mid score color.")
        .defaultValue(new SettingColor(255, 255, 0, 150))
        .build());

    private final Setting<SettingColor> colorHigh = sgRender.add(new ColorSetting.Builder()
        .name("color-high")
        .description("High score color.")
        .defaultValue(new SettingColor(255, 100, 0, 180))
        .build());

    private final Setting<SettingColor> colorBurst = sgRender.add(new ColorSetting.Builder()
        .name("color-burst")
        .description("Burst activity color.")
        .defaultValue(new SettingColor(255, 0, 0, 200))
        .build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How chunk boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Double> renderDistance = sgRender.add(new DoubleSetting.Builder()
        .name("render-distance")
        .description("Max distance to render.")
        .defaultValue(256.0)
        .min(16.0)
        .sliderMax(512.0)
        .build());

    private final Map<ChunkPos, ChunkActivity> activities = new ConcurrentHashMap<>();

    private static class ChunkActivity {
        double score = 0;
        long lastUpdate = 0;
        double surfaceY = 64;
    }

    public MovementDebug() {
        super(Categories.Misc, "movement-debug", "Tracks chunk activity with scoring.");
    }

    @Override
    public void onActivate() {
        activities.clear();
    }

    @Override
    public void onDeactivate() {
        activities.clear();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;

        if (event.packet instanceof BlockUpdateS2CPacket p) {
            addScore(p.getPos(), 3.0, "block update");
        }
        else if (event.packet instanceof BlockEntityUpdateS2CPacket p) {
            addScore(p.getPos(), 4.0, "block entity");
        }
        else if (event.packet instanceof ChunkDeltaUpdateS2CPacket p) {
            p.visitUpdates((pos, state) -> addScore(pos, 5.0, "multi"));
        }
        else if (event.packet instanceof ExplosionS2CPacket p) {
            addScore(new BlockPos((int)p.getX(), (int)p.getY(), (int)p.getZ()), 10.0, "explosion");
        }
        else if (event.packet instanceof BlockBreakingProgressS2CPacket p) {
            addScore(p.getPos(), 2.0, "mining");
        }
    }

    private void addScore(BlockPos pos, double points, String reason) {
        if (mc.player == null) return;
        if (pos.getY() < -64) return;
        if (filterSelf.get() && isSelfActivity(pos)) return;

        ChunkPos cp = new ChunkPos(pos);
        ChunkActivity act = activities.computeIfAbsent(cp, k -> new ChunkActivity());
        act.score += points;
        act.lastUpdate = System.currentTimeMillis();
        act.surfaceY = getSurfaceY(pos);

        if (chatAlert.get() && act.score >= burstThreshold.get()) {
            ChatUtils.info("MovementDebug",
                "Chunk (%d, %d) | Score: %.1f | %s",
                cp.x, cp.z, act.score, reason);
        }
    }

    private boolean isSelfActivity(BlockPos pos) {
        Vec3d playerPos = mc.player.getPos();
        double dist = Math.sqrt(
            Math.pow(pos.getX() - playerPos.x, 2) +
            Math.pow(pos.getZ() - playerPos.z, 2)
        );
        return dist < 5;
    }

    private double getSurfaceY(BlockPos pos) {
        if (mc.world == null) return 64;
        for (int y = 320; y >= -64; y--) {
            BlockPos check = new BlockPos(pos.getX(), y, pos.getZ());
            if (!mc.world.getBlockState(check).isAir()) {
                return y;
            }
        }
        return -64;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (activities.isEmpty()) return;
        if (mc.player == null || mc.world == null) return;

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        long now = System.currentTimeMillis();
        double decayMs = decayTimeSec.get() * 1000;

        activities.entrySet().removeIf(e -> now - e.getValue().lastUpdate > decayMs);

        if (activities.size() > maxChunks.get()) {
            activities.entrySet().stream()
                .sorted(Comparator.comparingDouble(e -> e.getValue().score))
                .limit(activities.size() - maxChunks.get())
                .forEach(e -> activities.remove(e.getKey()));
        }

        for (Map.Entry<ChunkPos, ChunkActivity> entry : activities.entrySet()) {
            ChunkPos cp = entry.getKey();
            ChunkActivity act = entry.getValue();

            double cx = (cp.x << 4) + 8;
            double cz = (cp.z << 4) + 8;
            double dist = Math.sqrt(Math.pow(cx - cam.x, 2) + Math.pow(cz - cam.z, 2));
            if (dist > renderDistance.get()) continue;

            SettingColor sc;
            if (act.score >= burstThreshold.get() * 2) {
                sc = colorBurst.get();
            } else if (act.score >= burstThreshold.get()) {
                sc = colorHigh.get();
            } else if (act.score >= burstThreshold.get() / 2) {
                sc = colorMid.get();
            } else {
                sc = colorLow.get();
            }

            Color side = new Color(sc.r, sc.g, sc.b, (int)(sc.a * 0.4));
            Color line = new Color(sc.r, sc.g, sc.b, sc.a);

            double x1 = cp.x << 4;
            double z1 = cp.z << 4;
            double x2 = x1 + 16;
            double z2 = z1 + 16;
            double y1 = Math.max(-64, act.surfaceY);
            double y2 = y1 + 2;

            event.renderer.box(x1, y1, z1, x2, y2, z2, side, line, shapeMode.get(), 0);
        }
    }
}
