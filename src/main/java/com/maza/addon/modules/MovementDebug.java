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

    private final Setting<Boolean> showScore = sgGeneral.add(new BoolSetting.Builder()
        .name("show-score")
        .description("Show score in chat.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> showReasons = sgGeneral.add(new BoolSetting.Builder()
        .name("show-reasons")
        .description("Show detection reasons.")
        .defaultValue(true)
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

    private final Setting<Double> burstWindowSec = sgGeneral.add(new DoubleSetting.Builder()
        .name("burst-window")
        .description("Time window for burst detection.")
        .defaultValue(3.0)
        .min(1.0)
        .sliderMax(10.0)
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
    private final Map<ChunkPos, List<Long>> burstTimestamps = new ConcurrentHashMap<>();

    private static class ChunkActivity {
        double score = 0;
        long lastUpdate = 0;
        String lastReason = "";
        double surfaceY = 64;
    }

    public MovementDebug() {
        super(Category.Misc, "movement-debug", "Tracks chunk activity with scoring.");
    }

    @Override
    public void onActivate() {
        activities.clear();
        burstTimestamps.clear();
    }

    @Override
    public void onDeactivate() {
        activities.clear();
        burstTimestamps.clear();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;

        if (event.packet instanceof BlockUpdateS2CPacket p) {
            detectBlockUpd(p.getPos());
        }
        else if (event.packet instanceof BlockEntityUpdateS2CPacket p) {
            detectBlockEv(p.getPos());
        }
        else if (event.packet instanceof ChunkDeltaUpdateS2CPacket p) {
            p.visitUpdates((pos, state) -> detectMulti(pos));
        }
        else if (event.packet instanceof ExplosionS2CPacket p) {
            detectExplosion(p.getX(), p.getY(), p.getZ());
        }
        else if (event.packet instanceof BlockBreakingProgressS2CPacket p) {
            detectMining(p.getPos());
        }
        else if (event.packet instanceof PlaySoundS2CPacket p) {
            detectSounds(p.getX(), p.getY(), p.getZ(), p.getSound().value().getId().toString());
        }
        else if (event.packet instanceof EntitySpawnS2CPacket p) {
            detectEntity(p.getX(), p.getY(), p.getZ());
        }
    }

    private void detectBlockUpd(BlockPos pos) {
        scoreBlockUpd(pos, 3.0, "block update");
    }

    private void detectBlockEv(BlockPos pos) {
        scoreBlockUpd(pos, 4.0, "block entity");
    }

    private void detectMulti(BlockPos pos) {
        scoreBlockUpd(pos, 5.0, "multi update");
    }

    private void detectExplosion(double x, double y, double z) {
        scoreExplosion(new BlockPos((int)x, (int)y, (int)z), 10.0);
    }

    private void detectMining(BlockPos pos) {
        scoreMining(pos, 2.0);
    }

    private void detectSounds(double x, double y, double z, String soundId) {
        BlockPos pos = new BlockPos((int)x, (int)y, (int)z);

        if (soundId.contains("chest") || soundId.contains("barrel")) {
            scoreChest(pos, 6.0);
        }
        else if (soundId.contains("step") || soundId.contains("footstep")) {
            scoreFootstep(pos, 1.5);
        }
        else if (soundId.contains("piston")) {
            scorePiston(pos, 4.0);
        }
        else if (soundId.contains("item.pickup") || soundId.contains("item.drop")) {
            scoreItemDrop(pos, 2.0);
        }
        else {
            scoreBlockSnd(pos, 1.0, soundId);
        }
    }

    private void detectEntity(double x, double y, double z) {
        BlockPos pos = new BlockPos((int)x, (int)y, (int)z);
        scoreProjectile(pos, 3.0);
    }

    private void scoreBlockUpd(BlockPos pos, double points, String reason) {
        addScore(pos, points, reason);
    }

    private void scoreBlockSnd(BlockPos pos, double points, String sound) {
        addScore(pos, points, "sound: " + sound);
    }

    private void scoreChest(BlockPos pos, double points) {
        addScore(pos, points, "chest open");
    }

    private void scoreExplosion(BlockPos pos, double points) {
        addScore(pos, points, "explosion");
    }

    private void scoreFootstep(BlockPos pos, double points) {
        addScore(pos, points, "footstep");
    }

    private void scoreInteract(BlockPos pos, double points) {
        addScore(pos, points, "interact");
    }

    private void scoreItemDrop(BlockPos pos, double points) {
        addScore(pos, points, "item drop");
    }

    private void scoreMining(BlockPos pos, double points) {
        addScore(pos, points, "mining");
    }

    private void scoreMulti(BlockPos pos, double points) {
        addScore(pos, points, "multi");
    }

    private void scorePiston(BlockPos pos, double points) {
        addScore(pos, points, "piston");
    }

    private void scoreProjectile(BlockPos pos, double points) {
        addScore(pos, points, "projectile");
    }

    private void scoreXp(BlockPos pos, double points) {
        addScore(pos, points, "xp");
    }

    private void addScore(BlockPos pos, double points, String reason) {
        if (mc.player == null) return;
        if (filterSelf.get() && isSelfActivity(pos)) return;

        ChunkPos cp = new ChunkPos(pos);
        ChunkActivity act = activities.computeIfAbsent(cp, k -> new ChunkActivity());
        act.score += points;
        act.lastUpdate = System.currentTimeMillis();
        act.lastReason = reason;
        act.surfaceY = getSurfaceY(pos);

        if (burstEnabled.get()) {
            checkBurst(cp, points);
        }

        if (chatAlert.get() && act.score >= burstThreshold.get()) {
            ChatUtils.info("MovementDebug",
                "Chunk (%d, %d) — score %.1f [%s]",
                cp.x, cp.z, act.score, reason);
        }
    }

    private boolean isSelfActivity(BlockPos pos) {
        if (mc.player == null) return false;
        Vec3d playerPos = mc.player.getPos();
        double dist = Math.sqrt(
            Math.pow(pos.getX() - playerPos.x, 2) +
            Math.pow(pos.getZ() - playerPos.z, 2)
        );
        return dist < 5;
    }

    private void checkBurst(ChunkPos cp, double points) {
        long now = System.currentTimeMillis();
        List<Long> times = burstTimestamps.computeIfAbsent(cp, k -> new ArrayList<>());
        times.add(now);

        double windowMs = burstWindowSec.get() * 1000;
        times.removeIf(t -> now - t > windowMs);

        if (times.size() >= 3) {
            ChunkActivity act = activities.get(cp);
            if (act != null) {
                act.score += burstThreshold.get();
                if (chatAlert.get()) {
                    ChatUtils.warning("MovementDebug",
                        "BURST detected! Chunk (%d, %d)", cp.x, cp.z);
                }
            }
        }
    }

    private double getSurfaceY(BlockPos pos) {
        if (mc.world == null) return 64;
        for (int y = 320; y > -64; y--) {
            BlockPos check = new BlockPos(pos.getX(), y, pos.getZ());
            if (!mc.world.getBlockState(check).isAir()) {
                return y;
            }
        }
        return 64;
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
            double y1 = act.surfaceY;
            double y2 = y1 + 2;

            event.renderer.box(x1, y1, z1, x2, y2, z2, side, line, shapeMode.get(), 0);
        }
    }
  }
          
