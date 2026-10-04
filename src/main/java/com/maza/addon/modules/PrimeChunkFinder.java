package com.maza.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import com.maza.addon.MazaCategory;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

public class PrimeChunkFinder extends Module {
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup detection = settings.createGroup("Detection");
    private final SettingGroup render = settings.createGroup("Render");

    private final Setting<Integer> range = general.add(new IntSetting.Builder().name("range").defaultValue(16).min(1).sliderMax(32).build());
    private final Setting<Integer> minSignals = detection.add(new IntSetting.Builder().name("min-signals").description("Signals required before a chunk is flagged.").defaultValue(2).min(1).max(10).sliderMax(10).build());
    private final Setting<Integer> windowMs = detection.add(new IntSetting.Builder().name("signal-window-ms").defaultValue(2500).min(250).max(10000).sliderMax(10000).build());
    private final Setting<Boolean> piston = detection.add(new BoolSetting.Builder().name("pistons").defaultValue(true).build());
    private final Setting<Boolean> observer = detection.add(new BoolSetting.Builder().name("observers").defaultValue(true).build());
    private final Setting<Boolean> redstone = detection.add(new BoolSetting.Builder().name("redstone-sounds").defaultValue(true).build());
    private final Setting<SettingColor> color = render.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(0, 255, 0, 100, true)).build());

    private final Map<Long, Deque<Long>> signals = new HashMap<>();
    private final LinkedHashMap<Long, Long> flagged = new LinkedHashMap<>();
    private final Map<Long, Long> lastSound = new HashMap<>();

    public PrimeChunkFinder() { super(MazaCategory.INSTANCE, "prime-chunk-finder", "Flags chunks that repeatedly produce piston/observer/redstone sound activity."); }

    @Override public void onActivate() { signals.clear(); flagged.clear(); lastSound.clear(); }
    @Override public void onDeactivate() { signals.clear(); flagged.clear(); lastSound.clear(); }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof PlaySoundS2CPacket packet) || mc.player == null || mc.world == null) return;
        double x = packet.getX(), z = packet.getZ();
        double dx0 = mc.player.getX() - x;
        double dz0 = mc.player.getZ() - z;
        double limit = range.get() * 16.0;
        if (dx0 * dx0 + dz0 * dz0 > limit * limit) return;

        SoundEvent sound = packet.getSound().value();
        var soundId = Registries.SOUND_EVENT.getId(sound);
        if (soundId == null) return; // custom or unregistered sound, nothing to match
        String id = soundId.getPath();
        boolean isPiston = id.contains("piston_extend") || id.contains("piston_contract") || id.contains("piston_retract");
        boolean isObserver = id.contains("observer_click");
        boolean isRedstone = id.contains("redstone") || id.contains("tripwire_click") || id.contains("dispenser");
        if ((!piston.get() || !isPiston) && (!observer.get() || !isObserver) && (!redstone.get() || !isRedstone)) return;

        ChunkPos cp = new ChunkPos((int)Math.floor(x) >> 4, (int)Math.floor(z) >> 4);
        long key = cp.toLong();
        long now = System.currentTimeMillis();
        if (now - lastSound.getOrDefault(key, 0L) < 80) return;
        lastSound.put(key, now);

        Deque<Long> q = signals.computeIfAbsent(key, k -> new ArrayDeque<>());
        q.addLast(now);
        while (!q.isEmpty() && now - q.peekFirst() > windowMs.get()) q.removeFirst();
        if (q.size() >= minSignals.get()) flagged.put(key, now);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        flagged.entrySet().removeIf(e -> now - e.getValue() > 15000);
        double max = range.get() * 16.0;
        for (long key : flagged.keySet()) {
            ChunkPos cp = new ChunkPos(key);
            double cx = cp.getStartX(), cz = cp.getStartZ();
            double dx = mc.player.getX() - (cx + 8);
            double dz = mc.player.getZ() - (cz + 8);
            if (dx * dx + dz * dz > max * max) continue;
            // LARP-style 3D flag: thin filled chunk cell, rendered through
            // the Render3DEvent renderer rather than a heavy outline.
            double y = mc.player.getY();
            double topY = y + 0.10;
            event.renderer.boxSides(
                cx, y, cz,
                cx + 16.0, topY, cz + 16.0,
                color.get(),
                0
            );

        }
    }
}
