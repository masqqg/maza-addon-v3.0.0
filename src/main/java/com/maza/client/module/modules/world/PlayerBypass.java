package com.maza.client.module.modules.world;

import com.maza.client.event.EventHandler;
import com.maza.client.event.PacketEvent;
import com.maza.client.event.Render3DEvent;
import com.maza.client.event.TickEvent;
import com.maza.client.module.Category;
import com.maza.client.module.Module;
import com.maza.client.setting.*;
import com.maza.client.utils.ChatUtils;
import com.maza.client.utils.Render3DUtils;
import com.maza.client.utils.ShapeMode;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;

import java.util.*;

public class PlayerBypass extends Module {
    public enum Mode { Signature, Activity, Both }

    private static final int SETTLE_TICKS = 6;
    private static final int SCANS_PER_TICK = 4;
    private static final int PRUNE_INTERVAL = 40;

    public final EnumSetting<Mode> mode = addSetting(new EnumSetting.Builder<Mode>()
        .name("mode").defaultValue(Mode.Both).build());

    public final BoolSetting notify = addSetting(new BoolSetting.Builder()
        .name("notify").description("Chat message when a chunk gets flagged.").defaultValue(false).build());

    public final BoolSetting debug = addSetting(new BoolSetting.Builder()
        .name("debug").defaultValue(false).build());

    public final IntSetting ceilingY = addSetting(new IntSetting.Builder()
        .name("ceiling-y").description("Only sections below this height count.").defaultValue(62).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    public final IntSetting minSections = addSetting(new IntSetting.Builder()
        .name("min-sections").description("Sealed sections before flagged.").defaultValue(1).min(1).max(12).sliderMin(1).sliderMax(12).build());

    public final BoolSetting holdBelowZero = addSetting(new BoolSetting.Builder()
        .name("hold-below-zero").defaultValue(true).build());

    public final BoolSetting ignoreVisited = addSetting(new BoolSetting.Builder()
        .name("ignore-visited").defaultValue(true).build());

    public final IntSetting visitedRadius = addSetting(new IntSetting.Builder()
        .name("visited-radius").defaultValue(2).min(0).max(6).sliderMin(0).sliderMax(6).build());

    public final IntSetting ignoreRadius = addSetting(new IntSetting.Builder()
        .name("ignore-radius").defaultValue(0).min(0).max(32).sliderMin(0).sliderMax(32).build());

    public final IntSetting minUpdates = addSetting(new IntSetting.Builder()
        .name("min-updates").defaultValue(3).min(1).max(30).sliderMin(1).sliderMax(30).build());

    public final IntSetting windowSeconds = addSetting(new IntSetting.Builder()
        .name("window-seconds").defaultValue(30).min(2).max(300).sliderMin(2).sliderMax(300).build());

    public final IntSetting holdSeconds = addSetting(new IntSetting.Builder()
        .name("hold-seconds").defaultValue(120).min(5).max(1800).sliderMin(5).sliderMax(1800).build());

    public final IntSetting settleSeconds = addSetting(new IntSetting.Builder()
        .name("settle-seconds").defaultValue(3).min(0).max(30).sliderMin(0).sliderMax(30).build());

    public final IntSetting renderRange = addSetting(new IntSetting.Builder()
        .name("range-chunks").defaultValue(16).min(2).max(64).sliderMin(2).sliderMax(64).build());

    public final IntSetting renderY = addSetting(new IntSetting.Builder()
        .name("render-y").defaultValue(64).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    public final DoubleSetting slabHeight = addSetting(new DoubleSetting.Builder()
        .name("slab-height").defaultValue(1.0).min(0.1).max(8.0).sliderMin(0.1).sliderMax(8.0).build());

    public final BoolSetting tracers = addSetting(new BoolSetting.Builder()
        .name("tracers").defaultValue(false).build());

    public final ColorSetting signatureColor = addSetting(new ColorSetting.Builder()
        .name("signature-color").defaultValue(new SettingColor(255, 0, 0, 230)).build());

    public final ColorSetting activityColor = addSetting(new ColorSetting.Builder()
        .name("activity-color").defaultValue(new SettingColor(255, 150, 0, 230)).build());

    private final Map<Long, Integer> pending = new LinkedHashMap<>();
    private final Set<Long> scanned = new HashSet<>();
    private final Set<Long> signatureFlagged = new HashSet<>();
    private final Set<Long> visited = new HashSet<>();
    private final Set<Long> protectedChunks = new HashSet<>();
    private final Map<Long, Long> loadedAt = new HashMap<>();
    private final Map<Long, ArrayDeque<Long>> hits = new HashMap<>();
    private final Map<Long, Long> activeUntil = new HashMap<>();
    private ClientWorld lastWorld;
    private int tick;

    public PlayerBypass() {
        super("PlayerBypass", "Flags chunks with signs of player bases from light data", Category.WORLD);
    }

    @Override
    public void onEnable() {
        clearAll();
        lastWorld = null;
    }

    @Override
    public void onDisable() {
        clearAll();
        lastWorld = null;
    }

    private void clearAll() {
        pending.clear();
        scanned.clear();
        signatureFlagged.clear();
        visited.clear();
        protectedChunks.clear();
        loadedAt.clear();
        hits.clear();
        activeUntil.clear();
    }

    private boolean signatureOn() {
        return mode.get() != Mode.Activity;
    }

    private boolean activityOn() {
        return mode.get() != Mode.Signature;
    }

    @EventHandler
    public void onPacket(PacketEvent.Receive event) {
        if (event.packet instanceof ChunkDataS2CPacket chunkData) {
            long key = ChunkPos.toLong(chunkData.getChunkX(), chunkData.getChunkZ());
            loadedAt.put(key, System.currentTimeMillis());
            signatureFlagged.remove(key);
            scanned.remove(key);
            hits.remove(key);
            activeUntil.remove(key);
            if (signatureOn()) schedule(key);
        } else if (event.packet instanceof LightUpdateS2CPacket light) {
            int cx = light.getChunkX();
            int cz = light.getChunkZ();
            mc.execute(() -> {
                if (mc.world == null || mc.player == null) return;
                long key = ChunkPos.toLong(cx, cz);
                if (signatureOn()) schedule(key);
                if (activityOn()) recordActivity(cx, cz, key);
            });
        }
    }

    private void schedule(long key) {
        pending.put(key, tick + SETTLE_TICKS);
    }

    private void recordActivity(int cx, int cz, long key) {
        int ignore = ignoreRadius.get() > 0 ? ignoreRadius.get() : mc.options.getSimulationDistance().getValue();
        ChunkPos pc = mc.player.getChunkPos();
        if (Math.max(Math.abs(cx - pc.x), Math.abs(cz - pc.z)) <= ignore) return;

        Long born = loadedAt.get(key);
        long now = System.currentTimeMillis();
        if (born == null || now - born < settleSeconds.get() * 1000L) return;

        ArrayDeque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        times.addLast(now);

        long cutoff = now - windowSeconds.get() * 1000L;
        while (!times.isEmpty() && times.peekFirst() < cutoff) times.pollFirst();

        if (times.size() >= minUpdates.get()) {
            boolean fresh = !activeUntil.containsKey(key);
            activeUntil.put(key, now + holdSeconds.get() * 1000L);
            if (fresh && notify.get()) ChatUtils.info(String.format("Activity at chunk %d, %d", cx, cz));
        }
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) {
            clearAll();
            lastWorld = null;
            return;
        }

        if (mc.world != lastWorld) {
            clearAll();
            lastWorld = mc.world;
        }
        tick++;

        if (signatureOn()) {
            runScans();
            protectVisited();
        }

        if (tick % PRUNE_INTERVAL == 0) prune();
    }

    private void runScans() {
        if (pending.isEmpty()) return;
        if (holdBelowZero.get() && mc.player.getY() < 0) return;

        int budget = SCANS_PER_TICK;
        Iterator<Map.Entry<Long, Integer>> it = pending.entrySet().iterator();

        while (it.hasNext() && budget > 0) {
            Map.Entry<Long, Integer> entry = it.next();
            if (entry.getValue() > tick) continue;

            long key = entry.getKey();
            it.remove();
            budget--;
            scanSignature(key);
        }
    }

    private void scanSignature(long key) {
        ChunkPos cp = new ChunkPos(key);
        if (mc.world.getChunkManager().getWorldChunk(cp.x, cp.z) == null) return;

        scanned.add(key);

        if (protectedChunks.contains(key)) {
            signatureFlagged.remove(key);
            return;
        }

        var sky = mc.world.getLightingProvider().get(LightType.SKY);
        int bottom = mc.world.getBottomSectionCoord();
        int count = mc.world.countVerticalSections();
        int ceiling = ceilingY.get();
        int sealed = 0;
        int present = 0;

        for (int i = 0; i < count; i++) {
            int sectionY = bottom + i;
            if (sectionY * 16 + 15 >= ceiling) break;

            ChunkNibbleArray array = sky.getLightSection(ChunkSectionPos.from(cp.x, sectionY, cp.z));
            if (array == null || array.isUninitialized()) continue;

            present++;
            if (allZero(array)) sealed++;
        }

        boolean flag = sealed >= minSections.get();
        boolean was = signatureFlagged.contains(key);

        if (flag) signatureFlagged.add(key);
        else signatureFlagged.remove(key);

        if (debug.get()) {
            ChatUtils.info(String.format("Chunk %d, %d: %d sky sections under Y%d, %d sealed%s", cp.x, cp.z, present, ceiling, sealed, flag ? " -> flagged" : ""));
        }
        if (flag && !was && notify.get()) {
            ChatUtils.info(String.format("Signature at chunk %d, %d", cp.x, cp.z));
        }
    }

    private static boolean allZero(ChunkNibbleArray array) {
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (array.get(x, y, z) != 0) return false;
                }
            }
        }
        return true;
    }

    private void protectVisited() {
        if (!ignoreVisited.get()) return;

        long here = mc.player.getChunkPos().toLong();
        if (visited.contains(here) || !scanned.contains(here) || signatureFlagged.contains(here)) return;

        visited.add(here);

        ChunkPos pc = mc.player.getChunkPos();
        int r = visitedRadius.get();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                long key = ChunkPos.toLong(pc.x + dx, pc.z + dz);
                protectedChunks.add(key);
                signatureFlagged.remove(key);
            }
        }
    }

    private void prune() {
        long now = System.currentTimeMillis();
        activeUntil.values().removeIf(until -> until < now);
        hits.keySet().removeIf(key -> !activeUntil.containsKey(key) && hits.get(key).isEmpty());

        signatureFlagged.removeIf(key -> !chunkLoaded(key));
        scanned.removeIf(key -> !chunkLoaded(key));
        loadedAt.keySet().removeIf(key -> !chunkLoaded(key));
        activeUntil.keySet().removeIf(key -> !chunkLoaded(key));
        hits.keySet().removeIf(key -> !chunkLoaded(key));
    }

    private boolean chunkLoaded(long key) {
        ChunkPos cp = new ChunkPos(key);
        return mc.world.getChunkManager().getWorldChunk(cp.x, cp.z) != null;
    }

    @EventHandler
    public void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        if (signatureFlagged.isEmpty() && activeUntil.isEmpty()) return;

        ChunkPos pc = mc.player.getChunkPos();
        int range = renderRange.get();

        if (activityOn()) {
            for (long key : activeUntil.keySet()) draw(key, pc, range, activityColor.get());
        }
        if (signatureOn()) {
            for (long key : signatureFlagged) draw(key, pc, range, signatureColor.get());
        }
    }

    private void draw(long key, ChunkPos pc, int range, SettingColor line) {
        ChunkPos cp = new ChunkPos(key);
        if (Math.max(Math.abs(cp.x - pc.x), Math.abs(cp.z - pc.z)) > range) return;

        double x1 = cp.getStartX();
        double z1 = cp.getStartZ();
        double x2 = x1 + 16.0;
        double z2 = z1 + 16.0;
        double y1 = renderY.get();
        double y2 = y1 + slabHeight.get();

        SettingColor fill = new SettingColor(line.r, line.g, line.b, Math.max(20, line.a / 4));
        Box box = new Box(x1, y1, z1, x2, y2, z2);
        Render3DUtils.drawBox(box, fill, line, ShapeMode.Both);

        if (tracers.get() && mc.player != null) {
            Render3DUtils.drawLine(mc.player.getEyePos(), new Vec3d(x1 + 8.0, y2, z1 + 8.0), line, 1.5f);
        }
    }
}
