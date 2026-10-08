package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * PlayerBypass
 *
 * Passive chunk flagging from the light data a server sends. Nothing is ever sent back.
 *
 *  Signature: below a ceiling height, a sky-light section that exists but is entirely
 *             zero is a light array that once held sky light and was later sealed off.
 *             A space like that underground is something a player built or dug out, so
 *             the chunk is flagged.
 *  Activity:  a light update for a chunk far from you means something changed there
 *             (blocks placed, mined, doors, lamps). Several updates in a short window
 *             flag the chunk for a while. Updates in the first seconds after a chunk
 *             loads, and updates close to you, are ignored.
 *
 * Own implementation of both ideas. The signature rule is untested on any particular
 * server, turn on debug to see what every scanned chunk looks like and tune the settings.
 */
public class PlayerBypass extends Module {
    public enum Mode { Signature, Activity, Both }

    private static final int SETTLE_TICKS = 6;     // let the light engine apply the packet first
    private static final int SCANS_PER_TICK = 4;
    private static final int PRUNE_INTERVAL = 40;

    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup signatureGroup = settings.createGroup("Signature");
    private final SettingGroup activityGroup = settings.createGroup("Activity");
    private final SettingGroup rendering = settings.createGroup("Render");

    // ---- general
    private final Setting<Mode> mode = general.add(new EnumSetting.Builder<Mode>()
        .name("mode").defaultValue(Mode.Both).build());

    private final Setting<Boolean> notify = general.add(new BoolSetting.Builder()
        .name("notify")
        .description("Chat message when a chunk gets flagged.")
        .defaultValue(false).build());

    private final Setting<Boolean> debug = general.add(new BoolSetting.Builder()
        .name("debug").defaultValue(false).build());

    // ---- signature
    private final Setting<Integer> ceilingY = signatureGroup.add(new IntSetting.Builder()
        .name("ceiling-y")
        .description("Only sections that end below this height count.")
        .defaultValue(62).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Integer> minSections = signatureGroup.add(new IntSetting.Builder()
        .name("min-sections")
        .description("Sealed (all zero) sections a chunk needs before it is flagged.")
        .defaultValue(1).min(1).max(12).sliderMin(1).sliderMax(12).build());

    private final Setting<Boolean> holdBelowZero = signatureGroup.add(new BoolSetting.Builder()
        .name("hold-below-zero")
        .description("While you are below Y 0, wait with scanning until you are back up.")
        .defaultValue(true).build());

    private final Setting<Boolean> ignoreVisited = signatureGroup.add(new BoolSetting.Builder()
        .name("ignore-visited")
        .description("A chunk you stood in that showed no signal, and the chunks around it, are never flagged afterwards.")
        .defaultValue(true).build());

    private final Setting<Integer> visitedRadius = signatureGroup.add(new IntSetting.Builder()
        .name("visited-radius")
        .description("Chunks around a visited chunk that are ignored too.")
        .defaultValue(2).min(0).max(6).sliderMin(0).sliderMax(6).build());

    // ---- activity
    private final Setting<Integer> ignoreRadius = activityGroup.add(new IntSetting.Builder()
        .name("ignore-radius")
        .description("Chunks around you whose updates are ignored (that is you). 0 = your simulation distance.")
        .defaultValue(0).min(0).max(32).sliderMin(0).sliderMax(32).build());

    private final Setting<Integer> minUpdates = activityGroup.add(new IntSetting.Builder()
        .name("min-updates")
        .description("Light updates inside the window before a chunk is flagged.")
        .defaultValue(3).min(1).max(30).sliderMin(1).sliderMax(30).build());

    private final Setting<Integer> windowSeconds = activityGroup.add(new IntSetting.Builder()
        .name("window-seconds")
        .defaultValue(30).min(2).max(300).sliderMin(2).sliderMax(300).build());

    private final Setting<Integer> holdSeconds = activityGroup.add(new IntSetting.Builder()
        .name("hold-seconds")
        .description("How long an activity chunk stays marked after its last update.")
        .defaultValue(120).min(5).max(1800).sliderMin(5).sliderMax(1800).build());

    private final Setting<Integer> settleSeconds = activityGroup.add(new IntSetting.Builder()
        .name("settle-seconds")
        .description("Updates this soon after a chunk loaded are ignored.")
        .defaultValue(3).min(0).max(30).sliderMin(0).sliderMax(30).build());

    // ---- render
    private final Setting<Integer> renderRange = rendering.add(new IntSetting.Builder()
        .name("range-chunks")
        .defaultValue(16).min(2).max(64).sliderMin(2).sliderMax(64).build());

    private final Setting<Integer> renderY = rendering.add(new IntSetting.Builder()
        .name("render-y")
        .defaultValue(64).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Double> slabHeight = rendering.add(new DoubleSetting.Builder()
        .name("slab-height")
        .defaultValue(1.0).min(0.1).max(8.0).sliderMin(0.1).sliderMax(8.0).build());

    private final Setting<Integer> thickness = rendering.add(new IntSetting.Builder()
        .name("outline-thickness")
        .description("Lines are one pixel wide, so this draws that many nested outlines.")
        .defaultValue(3).min(1).max(8).sliderMin(1).sliderMax(8).build());

    private final Setting<Boolean> tracers = rendering.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Line from your crosshair to every marked chunk.")
        .defaultValue(false).build());

    private final Setting<SettingColor> signatureColor = rendering.add(new ColorSetting.Builder()
        .name("signature-color")
        .defaultValue(new SettingColor(255, 0, 0, 230)).build());

    private final Setting<SettingColor> activityColor = rendering.add(new ColorSetting.Builder()
        .name("activity-color")
        .defaultValue(new SettingColor(255, 150, 0, 230)).build());

    // Only touched on the client thread.
    private final Map<Long, Integer> pending = new LinkedHashMap<>();        // chunk -> tick it may be scanned
    private final Set<Long> scanned = new HashSet<>();
    private final Set<Long> signatureFlagged = new HashSet<>();
    private final Set<Long> visited = new HashSet<>();
    private final Set<Long> protectedChunks = new HashSet<>();
    private final Map<Long, Long> loadedAt = new HashMap<>();                // chunk -> ms the client got it
    private final Map<Long, ArrayDeque<Long>> hits = new HashMap<>();        // chunk -> light update times (ms)
    private final Map<Long, Long> activeUntil = new HashMap<>();             // chunk -> ms the mark expires
    private ClientWorld lastWorld;
    private int tick;

    public PlayerBypass() {
        super(MazaCategory.INSTANCE, "player-bypass",
            "Flags chunks with signs of player bases from the light data the server sends.");
    }

    @Override
    public void onActivate() {
        clearAll();
        lastWorld = null;
    }

    @Override
    public void onDeactivate() {
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

    // ----------------------------------------------------------------- events

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null || event.chunk() == null) return;

        long key = event.chunk().getPos().toLong();
        loadedAt.put(key, System.currentTimeMillis());

        // A reloaded chunk starts over.
        signatureFlagged.remove(key);
        scanned.remove(key);
        hits.remove(key);
        activeUntil.remove(key);

        if (signatureOn()) schedule(key);
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof LightUpdateS2CPacket light)) return;

        int cx = light.getChunkX();
        int cz = light.getChunkZ();

        // Network thread: hop to the client thread before touching any state.
        mc.execute(() -> {
            if (mc.world == null || mc.player == null) return;
            long key = ChunkPos.toLong(cx, cz);

            if (signatureOn()) schedule(key);
            if (activityOn()) recordActivity(cx, cz, key);
        });
    }

    private void schedule(long key) {
        pending.put(key, tick + SETTLE_TICKS);
    }

    // --------------------------------------------------------------- activity

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

            if (fresh && notify.get()) info("Activity at chunk %d, %d", cx, cz);
        }
    }

    // -------------------------------------------------------------- signature

    @EventHandler
    private void onTick(TickEvent.Post event) {
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

    /** Count sky-light sections under the ceiling that exist but hold nothing but zeros. */
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
            if (sectionY * 16 + 15 >= ceiling) break; // sections come bottom to top

            ChunkNibbleArray array = sky.getLightSection(ChunkSectionPos.from(cp.x, sectionY, cp.z));
            if (array == null || array.isUninitialized()) continue;

            present++;
            if (allZero(array)) sealed++;
        }

        boolean flag = sealed >= minSections.get();
        boolean was = signatureFlagged.contains(key);

        if (flag) signatureFlagged.add(key);
        else signatureFlagged.remove(key);

        if (debug.get()) info("Chunk %d, %d: %d sky sections under Y%d, %d sealed%s", cp.x, cp.z, present, ceiling, sealed,
            flag ? " -> flagged" : "");

        if (flag && !was && notify.get()) info("Signature at chunk %d, %d", cp.x, cp.z);
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

    /**
     * The chunk you stand in, once it was scanned without a signal, is clean. It and the
     * chunks around it are never flagged afterwards, so your own digging and building does
     * not show up as somebody else's base.
     */
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

    // ----------------------------------------------------------------- render

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        if (signatureFlagged.isEmpty() && activeUntil.isEmpty()) return;

        ChunkPos pc = mc.player.getChunkPos();
        int range = renderRange.get();

        // Activity first, so a chunk that has both keeps the signature colour on top.
        if (activityOn()) {
            for (long key : activeUntil.keySet()) draw(event, key, pc, range, activityColor.get());
        }
        if (signatureOn()) {
            for (long key : signatureFlagged) draw(event, key, pc, range, signatureColor.get());
        }
    }

    private void draw(Render3DEvent event, long key, ChunkPos pc, int range, SettingColor line) {
        ChunkPos cp = new ChunkPos(key);
        if (Math.max(Math.abs(cp.x - pc.x), Math.abs(cp.z - pc.z)) > range) return;

        double x1 = cp.getStartX();
        double z1 = cp.getStartZ();
        double x2 = x1 + 16.0;
        double z2 = z1 + 16.0;
        double y1 = renderY.get();
        double y2 = y1 + slabHeight.get();

        Color fill = new Color(line.r, line.g, line.b, Math.max(20, line.a / 4));
        Color none = new Color(0, 0, 0, 0);

        event.renderer.box(x1, y1, z1, x2, y2, z2, fill, none, ShapeMode.Sides, 0);

        int passes = thickness.get();
        for (int k = 0; k < passes; k++) {
            double g = (k - (passes - 1) / 2.0) * 0.05;
            event.renderer.box(x1 - g, y1 - g, z1 - g, x2 + g, y2 + g, z2 + g, none, line, ShapeMode.Lines, 0);
        }

        if (tracers.get()) {
            var center = RenderUtils.center;
            if (center != null) {
                event.renderer.line(center.x, center.y, center.z, x1 + 8.0, y2, z1 + 8.0, line);
            }
        }
    }
}
