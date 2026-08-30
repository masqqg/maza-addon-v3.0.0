package com.maza.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ActivityDebug extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender  = settings.createGroup("Render");

    private final Setting<Double> yLevel = sgGeneral.add(new DoubleSetting.Builder()
        .name("y-level")
        .description("Maximum Y level to detect. -64 = everything above bedrock.")
        .defaultValue(-64.0)
        .range(-64.0, 320.0)
        .sliderRange(-64.0, 320.0)
        .build());

    private final Setting<Boolean> notification = sgGeneral.add(new BoolSetting.Builder()
        .name("notification")
        .description("Chat message when activity detected.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> playSound = sgGeneral.add(new BoolSetting.Builder()
        .name("play-sound")
        .description("Pling sound when activity detected.")
        .defaultValue(true)
        .build());

    private final Setting<Double> cooldown = sgGeneral.add(new DoubleSetting.Builder()
        .name("cooldown")
        .description("Seconds between notifications per chunk.")
        .defaultValue(3.0)
        .min(0.0)
        .sliderMax(10.0)
        .build());

    private final Setting<SettingColor> renderColor = sgRender.add(new ColorSetting.Builder()
        .name("render-color")
        .description("Chunk box color.")
        .defaultValue(new SettingColor(255, 100, 100, 180))
        .build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How chunk boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Double> renderDistance = sgRender.add(new DoubleSetting.Builder()
        .name("render-distance")
        .description("Max distance to render chunk boxes.")
        .defaultValue(256.0)
        .min(16.0)
        .sliderMax(512.0)
        .build());

    private final Set<ChunkPos> activeChunks = ConcurrentHashMap.newKeySet();
    private final Map<Long, Long> timestamps = new ConcurrentHashMap<>();

    public ActivityDebug() {
        super(Categories.Misc, "activity-debug", "Detects block activity in chunks.");
    }

    @Override
    public void onActivate() {
        activeChunks.clear();
        timestamps.clear();
    }

    @Override
    public void onDeactivate() {
        activeChunks.clear();
        timestamps.clear();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            BlockPos pos = packet.getPos();
            checkActivity(pos.getX(), pos.getY(), pos.getZ());
        }
        else if (event.packet instanceof BlockEntityUpdateS2CPacket packet) {
            BlockPos pos = packet.getPos();
            checkActivity(pos.getX(), pos.getY(), pos.getZ());
        }
        else if (event.packet instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates((pos, state) ->
                checkActivity(pos.getX(), pos.getY(), pos.getZ())
            );
        }
    }

    private void checkActivity(double x, double y, double z) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.getY() < 0) return;
        if (y < -64) return;
        if (y > yLevel.get()) return;

        ChunkPos chunkPos = new ChunkPos((int) x >> 4, (int) z >> 4);
        activeChunks.add(chunkPos);
        handleNotification(chunkPos, y);
    }

    private void handleNotification(ChunkPos chunkPos, double y) {
        if (!notification.get() && !playSound.get()) return;
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        long lastTime = timestamps.getOrDefault(chunkPos.toLong(), 0L);
        double cooldownMs = cooldown.get() * 1000;

        if (now - lastTime < cooldownMs) return;
        timestamps.put(chunkPos.toLong(), now);

        if (notification.get()) {
            ChatUtils.info("ActivityDebug",
                "Activity detected! Chunk (%d, %d) at Y %d",
                chunkPos.x, chunkPos.z, (int) y);
        }

        if (playSound.get()) {
            mc.world.playSound(
                mc.player,
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING,
                net.minecraft.sound.SoundCategory.PLAYERS,
                1.0f, 1.0f
            );
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (activeChunks.isEmpty()) return;
        if (mc.player == null || mc.world == null) return;

        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        SettingColor sc = renderColor.get();
        Color sideColor = new Color(sc.r, sc.g, sc.b, (int) (sc.a * 0.4));
        Color lineColor = new Color(sc.r, sc.g, sc.b, sc.a);

        for (ChunkPos cp : activeChunks) {
            double cx = (cp.x << 4) + 8;
            double cz = (cp.z << 4) + 8;
            double dist = Math.sqrt(
                Math.pow(cx - camPos.x, 2) +
                Math.pow(cz - camPos.z, 2)
            );
            if (dist > renderDistance.get()) continue;

            double x1 = cp.x << 4;
            double z1 = cp.z << 4;
            double x2 = x1 + 16;
            double z2 = z1 + 16;

            event.renderer.box(
                x1, -64, z1,
                x2, 320, z2,
                sideColor, lineColor,
                shapeMode.get(),
                0
            );
        }
    }
          }
                                 
