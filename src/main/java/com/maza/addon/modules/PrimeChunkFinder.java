package com.maza.addon.modules;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Prime Chunk Finder
 *
 * A chunk is flagged when enough independent methods point at it. Nothing is sent to the
 * server, every method reads data the client already has.
 *
 *  Sound:      pistons, observers, redstone, dispensers, and optionally chests/doors,
 *              several times inside a short window (live, fades out).
 *  Mask:       a server that hides part of a base can fill what it does not send with
 *              deepslate. Natural deepslate stops around Y 8, so a section far above that
 *              which is largely deepslate is a mask.
 *  Census:     blocks that only players place (hoppers, observers, pistons, beacons, shulker
 *              boxes, concrete, storage blocks, rotated deepslate), weighted and summed.
 *  Storage:    block entities that hold things (chests, barrels, hoppers, furnaces, ...).
 *  Entities:   item frames, armor stands, villagers, crowded animal pens (live).
 *  Palette:    the block palette of a section lists every block type in it, even when the
 *              server hides the blocks. Several player-only categories in one section
 *              (planks, concrete, glass, wool, redstone parts, storage, workstations, metal
 *              blocks) point at a build. Works where the block scan is blind.
 *  Edge:       deepslate fill that touches player blocks: the border of a masked base.
 *  Light:      a sky-light array under the surface that exists but is all zero was sealed
 *              off after it once had light: a built or dug-out space.
 *  Activity:   light updates for chunks far from you (live, fades out).
 *
 * Flagged chunks that touch each other are grouped, and every group gets one outline around
 * all of it, so a base that is only partly visible still shows its likely extent.
 *
 * Mask, census and storage are saved per server and dimension, so they survive a relog.
 */
public class PrimeChunkFinder extends Module {
    private static final int SOUND = 1;
    private static final int MASK = 2;
    private static final int CENSUS = 4;
    private static final int STORAGE = 8;
    private static final int ENTITY = 16;
    private static final int PALETTE = 32;
    private static final int EDGE = 64;
    private static final int LIGHT = 128;
    private static final int ACTIVITY = 256;
    private static final int STATIC_BITS = MASK | CENSUS | STORAGE | PALETTE | EDGE | LIGHT;

    private static final int LIGHT_SETTLE_TICKS = 6;   // let the light engine apply the packet first
    private static final int LIGHT_SCANS_PER_TICK = 4;
    private static final int GROUP_INTERVAL = 10;

    private static final int SCANS_PER_TICK = 2;
    private static final int ENTITY_INTERVAL = 40;
    private static final int SAVE_DELAY_TICKS = 100;
    private static final Path SAVE_FILE = FabricLoader.getInstance().getGameDir()
        .resolve("meteor-client").resolve("maza-prime-chunks.json");

    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup soundGroup = settings.createGroup("Sound");
    private final SettingGroup maskGroup = settings.createGroup("Deepslate Mask");
    private final SettingGroup censusGroup = settings.createGroup("Block Census");
    private final SettingGroup storageGroup = settings.createGroup("Storage");
    private final SettingGroup entityGroup = settings.createGroup("Entities");
    private final SettingGroup paletteGroup = settings.createGroup("Palette Leak");
    private final SettingGroup lightGroup = settings.createGroup("Light Signature");
    private final SettingGroup activityGroup = settings.createGroup("Light Activity");
    private final SettingGroup render = settings.createGroup("Render");

    // ---- general
    private final Setting<Integer> range = general.add(new IntSetting.Builder()
        .name("range").description("Chunks around you that are looked at and drawn.")
        .defaultValue(16).min(1).sliderMax(32).build());

    private final Setting<Integer> minMethods = general.add(new IntSetting.Builder()
        .name("min-methods")
        .description("Different methods that have to agree before a chunk is flagged. 1 = any single method.")
        .defaultValue(1).min(1).max(9).sliderMin(1).sliderMax(9).build());

    private final Setting<Boolean> notify = general.add(new BoolSetting.Builder()
        .name("notify").description("Chat message when a chunk gets flagged.").defaultValue(false).build());

    // ---- sound
    private final Setting<Boolean> soundOn = soundGroup.add(new BoolSetting.Builder()
        .name("sound").defaultValue(true).build());

    private final Setting<Integer> minSignals = soundGroup.add(new IntSetting.Builder()
        .name("min-signals").description("Sounds inside the window before the chunk counts.")
        .defaultValue(2).min(1).max(10).sliderMax(10).build());

    private final Setting<Integer> windowMs = soundGroup.add(new IntSetting.Builder()
        .name("signal-window-ms").defaultValue(2500).min(250).max(10000).sliderMax(10000).build());

    private final Setting<Integer> soundHoldSeconds = soundGroup.add(new IntSetting.Builder()
        .name("hold-seconds").description("How long a sound flag stays.")
        .defaultValue(60).min(5).max(600).sliderMin(5).sliderMax(600).build());

    private final Setting<Boolean> piston = soundGroup.add(new BoolSetting.Builder()
        .name("pistons").defaultValue(true).build());

    private final Setting<Boolean> observer = soundGroup.add(new BoolSetting.Builder()
        .name("observers").defaultValue(true).build());

    private final Setting<Boolean> redstone = soundGroup.add(new BoolSetting.Builder()
        .name("redstone-sounds").defaultValue(true).build());

    private final Setting<Boolean> storageSounds = soundGroup.add(new BoolSetting.Builder()
        .name("storage-sounds").description("Chests, barrels, shulker boxes and ender chests opening and closing.")
        .defaultValue(true).build());

    private final Setting<Boolean> doorSounds = soundGroup.add(new BoolSetting.Builder()
        .name("door-sounds").description("Doors, trapdoors, gates, levers, buttons. Villages make these too.")
        .defaultValue(false).build());

    // ---- deepslate mask
    private final Setting<Boolean> maskOn = maskGroup.add(new BoolSetting.Builder()
        .name("deepslate-mask")
        .description("Flag chunks with a section that is mostly deepslate far above where deepslate generates.")
        .defaultValue(true).build());

    private final Setting<Integer> maskAboveY = maskGroup.add(new IntSetting.Builder()
        .name("above-y").description("Only blocks at or above this height count. Natural deepslate ends around Y 8.")
        .defaultValue(16).min(0).max(200).sliderMin(0).sliderMax(200).build());

    private final Setting<Integer> maskPercent = maskGroup.add(new IntSetting.Builder()
        .name("min-percent").description("Share of a section that has to be deepslate.")
        .defaultValue(35).min(5).max(100).sliderMin(5).sliderMax(100).build());

    private final Setting<Integer> maskSections = maskGroup.add(new IntSetting.Builder()
        .name("min-sections").description("Sections of one chunk that have to look like a mask.")
        .defaultValue(1).min(1).max(8).sliderMin(1).sliderMax(8).build());

    private final Setting<Boolean> edgeOn = maskGroup.add(new BoolSetting.Builder()
        .name("mask-edge")
        .description("Deepslate fill touching player blocks: the border where a masked base starts.")
        .defaultValue(true).build());

    private final Setting<Integer> edgeMin = maskGroup.add(new IntSetting.Builder()
        .name("edge-min-contacts").description("Deepslate blocks above the height that touch a player block.")
        .defaultValue(4).min(1).max(100).sliderMin(1).sliderMax(50).build());

    // ---- census
    private final Setting<Boolean> censusOn = censusGroup.add(new BoolSetting.Builder()
        .name("block-census").description("Weighted count of blocks that only players place.")
        .defaultValue(true).build());

    private final Setting<Integer> censusMin = censusGroup.add(new IntSetting.Builder()
        .name("min-score").description("Census score a chunk needs.")
        .defaultValue(14).min(1).max(400).sliderMin(1).sliderMax(100).build());

    // ---- storage
    private final Setting<Boolean> storageOn = storageGroup.add(new BoolSetting.Builder()
        .name("storage").description("Count chests, barrels, hoppers, furnaces and similar block entities.")
        .defaultValue(true).build());

    private final Setting<Integer> storageMin = storageGroup.add(new IntSetting.Builder()
        .name("min-blocks").description("Storage block entities a chunk needs. Villages have a handful.")
        .defaultValue(10).min(1).max(200).sliderMin(1).sliderMax(60).build());

    // ---- entities
    private final Setting<Boolean> entityOn = entityGroup.add(new BoolSetting.Builder()
        .name("entities").description("Item frames, armor stands, villagers and crowded pens.")
        .defaultValue(true).build());

    private final Setting<Integer> entityMin = entityGroup.add(new IntSetting.Builder()
        .name("min-score").description("Entity score a chunk needs.")
        .defaultValue(10).min(1).max(200).sliderMin(1).sliderMax(60).build());

    // ---- palette leak
    private final Setting<Boolean> paletteOn = paletteGroup.add(new BoolSetting.Builder()
        .name("palette-leak")
        .description("Count player-only block categories in the palette of each section. Works when blocks are hidden.")
        .defaultValue(true).build());

    private final Setting<Integer> paletteCategories = paletteGroup.add(new IntSetting.Builder()
        .name("min-categories")
        .description("Categories one section needs: planks, concrete, glass, wool, redstone parts, storage, workstations, metal blocks. Villages reach 3 or 4.")
        .defaultValue(4).min(2).max(8).sliderMin(2).sliderMax(8).build());

    private final Setting<Integer> paletteSections = paletteGroup.add(new IntSetting.Builder()
        .name("min-sections").description("Sections of one chunk that have to reach that.")
        .defaultValue(2).min(1).max(12).sliderMin(1).sliderMax(12).build());

    // ---- light signature
    private final Setting<Boolean> lightOn = lightGroup.add(new BoolSetting.Builder()
        .name("light-signature")
        .description("A sky-light array under the surface that exists but is all zero: a space that was sealed off.")
        .defaultValue(true).build());

    private final Setting<Integer> lightCeiling = lightGroup.add(new IntSetting.Builder()
        .name("ceiling-y").description("Only sections that end below this height count.")
        .defaultValue(62).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Integer> lightSections = lightGroup.add(new IntSetting.Builder()
        .name("min-sealed-sections").description("Sealed sections a chunk needs.")
        .defaultValue(1).min(1).max(12).sliderMin(1).sliderMax(12).build());

    // ---- light activity
    private final Setting<Boolean> activityOn = activityGroup.add(new BoolSetting.Builder()
        .name("light-activity")
        .description("Light updates for chunks far from you mean something changed there. Live, fades out.")
        .defaultValue(true).build());

    private final Setting<Integer> activityIgnore = activityGroup.add(new IntSetting.Builder()
        .name("ignore-radius").description("Chunks around you that are ignored. 0 = your simulation distance.")
        .defaultValue(0).min(0).max(32).sliderMin(0).sliderMax(32).build());

    private final Setting<Integer> activityMin = activityGroup.add(new IntSetting.Builder()
        .name("min-updates").description("Light updates inside the window.")
        .defaultValue(3).min(1).max(30).sliderMin(1).sliderMax(30).build());

    private final Setting<Integer> activityWindow = activityGroup.add(new IntSetting.Builder()
        .name("window-seconds").defaultValue(30).min(2).max(300).sliderMin(2).sliderMax(300).build());

    private final Setting<Integer> activityHold = activityGroup.add(new IntSetting.Builder()
        .name("hold-seconds").defaultValue(120).min(5).max(1800).sliderMin(5).sliderMax(1800).build());

    private final Setting<Integer> activitySettle = activityGroup.add(new IntSetting.Builder()
        .name("settle-seconds").description("Updates this soon after a chunk loaded are ignored.")
        .defaultValue(3).min(0).max(30).sliderMin(0).sliderMax(30).build());

    // ---- render
    private final Setting<Integer> renderY = render.add(new IntSetting.Builder()
        .name("render-y").defaultValue(64).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Double> slabHeight = render.add(new DoubleSetting.Builder()
        .name("slab-height").defaultValue(1.0).min(0.1).max(8.0).sliderMin(0.1).sliderMax(8.0).build());

    private final Setting<Integer> thickness = render.add(new IntSetting.Builder()
        .name("outline-thickness").description("Lines are one pixel wide, so this draws that many nested outlines.")
        .defaultValue(3).min(1).max(8).sliderMin(1).sliderMax(8).build());

    private final Setting<Boolean> tracers = render.add(new BoolSetting.Builder()
        .name("tracers").description("Line from your crosshair to every flagged chunk.")
        .defaultValue(false).build());

    private final Setting<SettingColor> color = render.add(new ColorSetting.Builder()
        .name("color").description("Chunks with one method.")
        .defaultValue(new SettingColor(0, 255, 0, 230)).build());

    private final Setting<Boolean> showBounds = render.add(new BoolSetting.Builder()
        .name("group-outline")
        .description("One outline around every group of flagged chunks that touch each other: the likely extent of a base.")
        .defaultValue(true).build());

    private final Setting<Integer> linkRadius = render.add(new IntSetting.Builder()
        .name("group-link-chunks").description("Flagged chunks this close to each other belong to one group.")
        .defaultValue(3).min(1).max(10).sliderMin(1).sliderMax(10).build());

    private final Setting<Integer> minGroup = render.add(new IntSetting.Builder()
        .name("group-min-chunks").description("Flagged chunks a group needs before it gets an outline.")
        .defaultValue(2).min(1).max(50).sliderMin(1).sliderMax(50).build());

    private final Setting<SettingColor> boundsColor = render.add(new ColorSetting.Builder()
        .name("group-color").defaultValue(new SettingColor(0, 220, 255, 230)).build());

    private final Setting<SettingColor> strongColor = render.add(new ColorSetting.Builder()
        .name("strong-color").description("Chunks that two or more methods agree on.")
        .defaultValue(new SettingColor(255, 220, 0, 230)).build());

    // Only touched on the client thread.
    private final Map<Long, Integer> staticHits = new HashMap<>();   // MASK | CENSUS | STORAGE, saved
    private final Map<Long, Long> soundUntil = new HashMap<>();
    private final Map<Long, Long> entityUntil = new HashMap<>();
    private final Map<Long, Deque<Long>> signals = new HashMap<>();
    private final Map<Long, Long> lastSound = new HashMap<>();
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private final Set<Long> announced = new HashSet<>();
    private final IdentityHashMap<Block, Integer> weights = new IdentityHashMap<>();
    private final IdentityHashMap<Block, Integer> categories = new IdentityHashMap<>();
    private final Map<Long, Integer> lightDue = new LinkedHashMap<>();           // chunk -> tick it may be scanned
    private final Map<Long, Long> loadedAt = new HashMap<>();
    private final Map<Long, Deque<Long>> activityTimes = new HashMap<>();
    private final Map<Long, Long> activityUntil = new HashMap<>();
    private List<int[]> bounds = new ArrayList<>();                              // minX, minZ, maxX, maxZ, chunks

    private JsonObject saveRoot;
    private String activeKey;
    private ClientWorld lastWorld;
    private boolean dirty;
    private int dirtySince;
    private int tick;

    public PrimeChunkFinder() {
        super(MazaCategory.INSTANCE, "prime-chunk-finder",
            "Flags chunks that sound, look or hold like a base: redstone sounds, deepslate masks, player-only blocks, storage, entities.");
    }

    @Override
    public void onActivate() {
        clearAll();
        lastWorld = null;
        activeKey = null;
        saveRoot = loadSaveFile();
    }

    @Override
    public void onDeactivate() {
        save(false);
        clearAll();
        lastWorld = null;
        activeKey = null;
    }

    private void clearAll() {
        staticHits.clear();
        soundUntil.clear();
        entityUntil.clear();
        signals.clear();
        lastSound.clear();
        queue.clear();
        queued.clear();
        announced.clear();
        lightDue.clear();
        loadedAt.clear();
        activityTimes.clear();
        activityUntil.clear();
        bounds = new ArrayList<>();
        dirty = false;
    }

    // ------------------------------------------------------------------- events

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) {
            if (activeKey != null) {
                save(false);
                clearAll();
                activeKey = null;
            }
            lastWorld = null;
            return;
        }

        if (mc.world != lastWorld) {
            if (activeKey != null) save(false); // still the old world's key and data
            clearAll();
            lastWorld = mc.world;
            activeKey = saveKey();
            loadHits();
            scanLoadedChunks();
        }

        tick++;

        for (int i = 0; i < SCANS_PER_TICK && !queue.isEmpty(); i++) {
            long key = queue.poll();
            queued.remove(key);

            ChunkPos cp = new ChunkPos(key);
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cp.x, cp.z);
            if (chunk != null) scanChunk(chunk);
        }

        runLightScans();

        if (entityOn.get() && tick % ENTITY_INTERVAL == 0) scanEntities();
        if (showBounds.get() && tick % GROUP_INTERVAL == 0) rebuildGroups();
        if (tick % 100 == 0) prune();
        if (dirty && tick - dirtySince >= SAVE_DELAY_TICKS) save(true);
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null || event.chunk() == null) return;

        long key = event.chunk().getPos().toLong();
        loadedAt.put(key, System.currentTimeMillis());
        activityTimes.remove(key);
        activityUntil.remove(key);

        enqueue(key);
        scheduleLight(key);
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.packet instanceof LightUpdateS2CPacket light) {
            int cx = light.getChunkX();
            int cz = light.getChunkZ();
            mc.execute(() -> handleLightPacket(cx, cz));
            return;
        }

        if (!soundOn.get() || !(event.packet instanceof PlaySoundS2CPacket packet)) return;

        // Network thread: pull the plain values out, the maps are only used on the client thread.
        double x = packet.getX();
        double z = packet.getZ();
        var soundId = Registries.SOUND_EVENT.getId(packet.getSound().value());
        if (soundId == null) return; // custom or unregistered sound, nothing to match
        String id = soundId.getPath();

        mc.execute(() -> handleSound(x, z, id));
    }

    private void handleLightPacket(int cx, int cz) {
        if (mc.world == null || mc.player == null) return;

        long key = ChunkPos.toLong(cx, cz);
        scheduleLight(key);
        if (activityOn.get()) recordActivity(cx, cz, key);
    }

    /** A light update for a chunk far from you: something changed there. */
    private void recordActivity(int cx, int cz, long key) {
        int ignore = activityIgnore.get() > 0 ? activityIgnore.get() : mc.options.getSimulationDistance().getValue();
        ChunkPos pc = mc.player.getChunkPos();
        if (Math.max(Math.abs(cx - pc.x), Math.abs(cz - pc.z)) <= ignore) return;

        Long born = loadedAt.get(key);
        long now = System.currentTimeMillis();
        if (born == null || now - born < activitySettle.get() * 1000L) return;

        Deque<Long> times = activityTimes.computeIfAbsent(key, k -> new ArrayDeque<>());
        times.addLast(now);

        long cutoff = now - activityWindow.get() * 1000L;
        while (!times.isEmpty() && times.peekFirst() < cutoff) times.pollFirst();

        if (times.size() >= activityMin.get()) activityUntil.put(key, now + activityHold.get() * 1000L);
    }

    // ------------------------------------------------------------ light signature

    private void scheduleLight(long key) {
        if (lightOn.get()) lightDue.put(key, tick + LIGHT_SETTLE_TICKS);
    }

    private void runLightScans() {
        if (lightDue.isEmpty()) return;

        int budget = LIGHT_SCANS_PER_TICK;
        var it = lightDue.entrySet().iterator();

        while (it.hasNext() && budget > 0) {
            var entry = it.next();
            if (entry.getValue() > tick) continue;

            long key = entry.getKey();
            it.remove();
            budget--;
            scanLightSignature(key);
        }
    }

    /** Sky-light sections under the ceiling that exist but hold nothing but zeros. */
    private void scanLightSignature(long key) {
        ChunkPos cp = new ChunkPos(key);
        if (mc.world.getChunkManager().getWorldChunk(cp.x, cp.z) == null) return;

        var sky = mc.world.getLightingProvider().get(LightType.SKY);
        int bottom = mc.world.getBottomSectionCoord();
        int count = mc.world.countVerticalSections();
        int ceiling = lightCeiling.get();
        int sealed = 0;

        for (int i = 0; i < count; i++) {
            int sectionY = bottom + i;
            if (sectionY * 16 + 15 >= ceiling) break; // sections come bottom to top

            ChunkNibbleArray array = sky.getLightSection(ChunkSectionPos.from(cp.x, sectionY, cp.z));
            if (array == null || array.isUninitialized()) continue;
            if (allZero(array)) sealed++;
        }

        int current = staticHits.getOrDefault(key, 0);
        int next = sealed >= lightSections.get() ? (current | LIGHT) : (current & ~LIGHT);
        if (next == current) return;

        if (next == 0) staticHits.remove(key);
        else staticHits.put(key, next);
        markDirty();
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

    private void handleSound(double x, double z, String id) {
        if (mc.player == null || mc.world == null) return;

        double dx = mc.player.getX() - x;
        double dz = mc.player.getZ() - z;
        double limit = range.get() * 16.0;
        if (dx * dx + dz * dz > limit * limit) return;

        boolean isPiston = id.contains("piston_extend") || id.contains("piston_contract") || id.contains("piston_retract");
        boolean isObserver = id.contains("observer_click");
        boolean isRedstone = id.contains("redstone") || id.contains("tripwire_click") || id.contains("dispenser");
        boolean isStorage = (id.contains("chest") || id.contains("barrel") || id.contains("shulker_box"))
            && (id.contains("open") || id.contains("close"));
        boolean isDoor = id.contains("door") || id.contains("fence_gate") || id.contains("lever")
            || id.contains("button") || id.contains("pressure_plate");

        boolean wanted = (piston.get() && isPiston) || (observer.get() && isObserver) || (redstone.get() && isRedstone)
            || (storageSounds.get() && isStorage) || (doorSounds.get() && isDoor);
        if (!wanted) return;

        long key = ChunkPos.toLong((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
        long now = System.currentTimeMillis();
        if (now - lastSound.getOrDefault(key, 0L) < 80) return;
        lastSound.put(key, now);

        Deque<Long> q = signals.computeIfAbsent(key, k -> new ArrayDeque<>());
        q.addLast(now);
        while (!q.isEmpty() && now - q.peekFirst() > windowMs.get()) q.removeFirst();

        if (q.size() >= minSignals.get()) soundUntil.put(key, now + soundHoldSeconds.get() * 1000L);
    }

    // ----------------------------------------------------------------- scanning

    private void enqueue(long key) {
        if (!(maskOn.get() || censusOn.get() || storageOn.get() || paletteOn.get())) return;
        if (queued.add(key)) queue.add(key);
    }

    private void scanLoadedChunks() {
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;
        int radius = Math.max(2, mc.options.getViewDistance().getValue());

        for (int x = pcx - radius; x <= pcx + radius; x++) {
            for (int z = pcz - radius; z <= pcz + radius; z++) {
                if (mc.world.getChunkManager().getWorldChunk(x, z) != null) enqueue(ChunkPos.toLong(x, z));
            }
        }
    }

    /** Mask, census and storage of one chunk. The result replaces what was known about it. */
    private void scanChunk(WorldChunk chunk) {
        long key = chunk.getPos().toLong();
        int mask = 0;

        boolean overworld = inMainWorld();
        ChunkSection[] sections = chunk.getSectionArray();

        if (sections != null && ((maskOn.get() && overworld) || censusOn.get())) {
            int bottom = chunk.getBottomY();
            int aboveY = maskAboveY.get();
            int maskedSections = 0;
            int census = 0;
            int rotated = 0;
            int contacts = 0;

            for (int i = 0; i < sections.length; i++) {
                ChunkSection section = sections[i];
                if (section == null || section.isEmpty()) continue;

                int sectionBottom = bottom + i * 16;

                boolean wantMask = maskOn.get() && overworld && sectionBottom + 15 >= aboveY
                    && section.hasAny(state -> state.isOf(Blocks.DEEPSLATE));
                boolean wantCensus = censusOn.get() && section.hasAny(state -> weightOf(state.getBlock()) > 0);
                if (!wantMask && !wantCensus) continue;

                int deepslate = 0;
                int considered = 0;

                for (int y = 0; y < 16; y++) {
                    int worldY = sectionBottom + y;
                    boolean counted = wantMask && worldY >= aboveY;

                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (counted) {
                                considered++;
                                if (state.isOf(Blocks.DEEPSLATE)) {
                                    deepslate++;
                                    if (edgeOn.get() && contacts < 1000 && touchesPlayerBlock(section, x, y, z)) contacts++;
                                }
                            }

                            if (!wantCensus || state.isAir()) continue;

                            // A deepslate block that is not on the Y axis was placed by a player.
                            if (state.isOf(Blocks.DEEPSLATE) && state.contains(net.minecraft.state.property.Properties.AXIS)
                                && state.get(net.minecraft.state.property.Properties.AXIS) != Direction.Axis.Y) {
                                if (rotated < 8) rotated++;
                                continue;
                            }

                            int w = weightOf(state.getBlock());
                            if (w > 0 && census < 1000) census += w;
                        }
                    }
                }

                if (wantMask && considered > 0 && deepslate * 100 >= considered * maskPercent.get()) maskedSections++;
            }

            if (maskedSections >= maskSections.get()) mask |= MASK;
            if (edgeOn.get() && contacts >= edgeMin.get()) mask |= EDGE;
            if (censusOn.get() && census + rotated >= censusMin.get()) mask |= CENSUS;
        }

        if (storageOn.get() && storageScore(chunk) >= storageMin.get()) mask |= STORAGE;

        if (paletteOn.get() && sections != null) {
            int hot = 0;
            for (ChunkSection section : sections) {
                if (section == null || section.isEmpty()) continue;
                if (categoriesIn(section) >= paletteCategories.get()) hot++;
            }
            if (hot >= paletteSections.get()) mask |= PALETTE;
        }

        int before = staticHits.getOrDefault(key, 0);
        mask |= before & LIGHT; // the light signature has its own, later scan
        if (mask == 0) staticHits.remove(key);
        else staticHits.put(key, mask);

        if (before != mask) {
            markDirty();
            if (notify.get() && mask != 0 && before == 0 && announced.add(key)) {
                ChunkPos cp = chunk.getPos();
                info("Chunk %d, %d flagged (%s)", cp.x, cp.z, describe(mask));
            }
        }
    }

    /** A neighbour inside the same section that only players place. */
    private boolean touchesPlayerBlock(ChunkSection section, int x, int y, int z) {
        for (Direction d : Direction.values()) {
            int nx = x + d.getOffsetX();
            int ny = y + d.getOffsetY();
            int nz = z + d.getOffsetZ();
            if (nx < 0 || ny < 0 || nz < 0 || nx > 15 || ny > 15 || nz > 15) continue;

            if (weightOf(section.getBlockState(nx, ny, nz).getBlock()) > 0) return true;
        }
        return false;
    }

    /** How many player-only categories the palette of a section lists. */
    private int categoriesIn(ChunkSection section) {
        int found = 0;
        for (int c = 0; c < CATEGORY_COUNT; c++) {
            final int wanted = c;
            if (section.hasAny(state -> categoryOf(state.getBlock()) == wanted)) found++;
        }
        return found;
    }

    private static final int CATEGORY_COUNT = 8;

    /** 0 planks, 1 concrete, 2 glass, 3 wool, 4 redstone parts, 5 storage, 6 workstations, 7 metal blocks, -1 none. */
    private int categoryOf(Block block) {
        Integer cached = categories.get(block);
        if (cached != null) return cached;

        String id = Registries.BLOCK.getId(block).getPath();
        int category;

        if (id.endsWith("_planks")) {
            category = 0;
        } else if (id.contains("concrete") || id.contains("glazed_terracotta")) {
            category = 1;
        } else if (id.contains("glass") && !id.contains("tinted")) {
            category = 2;
        } else if (id.endsWith("_wool") || id.endsWith("_carpet")) {
            category = 3;
        } else if (id.endsWith("shulker_box") || id.equals("barrel") || id.equals("trapped_chest") || id.equals("ender_chest")) {
            category = 5;
        } else {
            category = switch (id) {
                case "hopper", "dropper", "dispenser", "observer", "piston", "sticky_piston", "comparator",
                     "repeater", "redstone_lamp", "note_block", "daylight_detector", "target", "crafter" -> 4;
                case "furnace", "blast_furnace", "smoker", "crafting_table", "smithing_table", "cartography_table",
                     "fletching_table", "loom", "grindstone", "stonecutter", "anvil", "chipped_anvil", "damaged_anvil",
                     "enchanting_table", "brewing_stand", "beacon", "lectern", "composter" -> 6;
                case "iron_block", "gold_block", "diamond_block", "emerald_block", "netherite_block", "lapis_block",
                     "copper_block", "redstone_block" -> 7;
                default -> -1;
            };
        }

        categories.put(block, category);
        return category;
    }

    private int storageScore(WorldChunk chunk) {
        int score = 0;

        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            BlockEntityType<?> type = entity.getType();

            if (type == BlockEntityType.BEACON || type == BlockEntityType.CONDUIT) score += 6;
            else if (type == BlockEntityType.BED || type == BlockEntityType.BANNER || type == BlockEntityType.LECTERN
                || type == BlockEntityType.ENCHANTING_TABLE || type == BlockEntityType.SIGN
                || type == BlockEntityType.HANGING_SIGN) score++;
            else if (type == BlockEntityType.CHEST || type == BlockEntityType.TRAPPED_CHEST
                || type == BlockEntityType.BARREL || type == BlockEntityType.SHULKER_BOX
                || type == BlockEntityType.HOPPER || type == BlockEntityType.FURNACE
                || type == BlockEntityType.BLAST_FURNACE || type == BlockEntityType.SMOKER
                || type == BlockEntityType.DISPENSER || type == BlockEntityType.DROPPER
                || type == BlockEntityType.BREWING_STAND || type == BlockEntityType.ENDER_CHEST
                || type == BlockEntityType.JUKEBOX || type == BlockEntityType.CRAFTER) {
                score++;
            }
        }
        return score;
    }

    /** Entity score per chunk, refreshed every couple of seconds. */
    private void scanEntities() {
        Map<Long, Double> scores = new HashMap<>();

        for (Entity entity : mc.world.getEntities()) {
            double w;
            if (entity instanceof ItemFrameEntity || entity instanceof ArmorStandEntity || entity instanceof VillagerEntity) w = 1.0;
            else if (entity instanceof AnimalEntity) w = 0.25; // a crowded pen, not a lone cow
            else continue;

            long key = ChunkPos.toLong(entity.getBlockX() >> 4, entity.getBlockZ() >> 4);
            scores.merge(key, w, Double::sum);
        }

        long until = System.currentTimeMillis() + 10_000L;
        for (Map.Entry<Long, Double> e : scores.entrySet()) {
            if (e.getValue() >= entityMin.get()) entityUntil.put(e.getKey(), until);
        }
    }

    /** Blocks that players place and nature does not, with how much each one counts. */
    private int weightOf(Block block) {
        Integer cached = weights.get(block);
        if (cached != null) return cached;

        String id = Registries.BLOCK.getId(block).getPath();
        int w;

        if (id.contains("concrete") || id.contains("glazed_terracotta")) {
            w = 1;
        } else if (id.endsWith("shulker_box")) {
            w = 2;
        } else {
            w = switch (id) {
                case "hopper", "dropper", "observer", "piston", "sticky_piston", "comparator", "repeater",
                     "redstone_lamp", "daylight_detector", "target", "beacon", "enchanting_table", "crafter",
                     "note_block", "jukebox", "conduit", "slime_block", "honey_block", "redstone_block" -> 2;
                case "iron_block", "gold_block", "diamond_block", "emerald_block", "lapis_block",
                     "netherite_block", "coal_block", "copper_block" -> 1;
                default -> 0;
            };
        }

        weights.put(block, w);
        return w;
    }

    private static String describe(int mask) {
        StringBuilder out = new StringBuilder();
        if ((mask & SOUND) != 0) out.append("sound ");
        if ((mask & MASK) != 0) out.append("deepslate mask ");
        if ((mask & CENSUS) != 0) out.append("player blocks ");
        if ((mask & STORAGE) != 0) out.append("storage ");
        if ((mask & ENTITY) != 0) out.append("entities ");
        if ((mask & PALETTE) != 0) out.append("palette ");
        if ((mask & EDGE) != 0) out.append("mask edge ");
        if ((mask & LIGHT) != 0) out.append("sealed light ");
        if ((mask & ACTIVITY) != 0) out.append("light activity ");
        return out.toString().trim();
    }

    private boolean inMainWorld() {
        if (mc.world.getRegistryKey() == World.OVERWORLD) return true;

        String id = mc.world.getRegistryKey().getValue().getPath();
        return !id.contains("nether") && !id.contains("end");
    }

    private void prune() {
        long now = System.currentTimeMillis();
        soundUntil.values().removeIf(until -> until < now);
        entityUntil.values().removeIf(until -> until < now);
        activityUntil.values().removeIf(until -> until < now);
        activityTimes.entrySet().removeIf(e -> e.getValue().isEmpty() || now - e.getValue().peekLast() > activityWindow.get() * 1000L);
        loadedAt.keySet().removeIf(k -> {
            ChunkPos cp = new ChunkPos(k);
            return mc.world.getChunkManager().getWorldChunk(cp.x, cp.z) == null;
        });
        signals.entrySet().removeIf(e -> e.getValue().isEmpty() || now - e.getValue().peekLast() > windowMs.get());
        lastSound.values().removeIf(t -> now - t > 10_000L);
    }

    // ------------------------------------------------------------------- render

    /** All methods that currently point at the chunk, limited to the ones that are switched on. */
    private int hitsOf(long key, long now) {
        int mask = staticHits.getOrDefault(key, 0);
        if (soundOn.get() && soundUntil.getOrDefault(key, 0L) > now) mask |= SOUND;
        if (entityOn.get() && entityUntil.getOrDefault(key, 0L) > now) mask |= ENTITY;
        if (activityOn.get() && activityUntil.getOrDefault(key, 0L) > now) mask |= ACTIVITY;

        int enabled = (soundOn.get() ? SOUND : 0) | (maskOn.get() ? MASK : 0) | (censusOn.get() ? CENSUS : 0)
            | (storageOn.get() ? STORAGE : 0) | (entityOn.get() ? ENTITY : 0) | (paletteOn.get() ? PALETTE : 0)
            | (maskOn.get() && edgeOn.get() ? EDGE : 0) | (lightOn.get() ? LIGHT : 0) | (activityOn.get() ? ACTIVITY : 0);
        return mask & enabled;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (staticHits.isEmpty() && soundUntil.isEmpty() && entityUntil.isEmpty() && activityUntil.isEmpty()) return;

        long now = System.currentTimeMillis();
        ChunkPos pc = mc.player.getChunkPos();
        int max = range.get();

        Set<Long> keys = new HashSet<>(staticHits.keySet());
        keys.addAll(soundUntil.keySet());
        keys.addAll(entityUntil.keySet());
        keys.addAll(activityUntil.keySet());

        for (long key : keys) {
            int hits = Integer.bitCount(hitsOf(key, now));
            if (hits < minMethods.get()) continue;

            ChunkPos cp = new ChunkPos(key);
            if (Math.max(Math.abs(cp.x - pc.x), Math.abs(cp.z - pc.z)) > max) continue;

            draw(event, cp, hits >= 2 ? strongColor.get() : color.get());
        }

        if (showBounds.get()) {
            for (int[] b : bounds) {
                if (b[2] < pc.x - max || b[0] > pc.x + max || b[3] < pc.z - max || b[1] > pc.z + max) continue;
                drawBounds(event, b);
            }
        }
    }

    /** Groups of flagged chunks that touch each other, with the box around each group. */
    private void rebuildGroups() {
        if (mc.player == null || mc.world == null) {
            bounds = new ArrayList<>();
            return;
        }

        long now = System.currentTimeMillis();
        Set<Long> keys = new HashSet<>(staticHits.keySet());
        keys.addAll(soundUntil.keySet());
        keys.addAll(entityUntil.keySet());
        keys.addAll(activityUntil.keySet());

        List<Long> flagged = new ArrayList<>();
        for (long key : keys) {
            if (Integer.bitCount(hitsOf(key, now)) >= minMethods.get()) flagged.add(key);
        }

        int link = linkRadius.get();
        Set<Long> seen = new HashSet<>();
        Set<Long> flaggedSet = new HashSet<>(flagged);
        List<int[]> result = new ArrayList<>();

        for (long start : flagged) {
            if (!seen.add(start)) continue;

            ArrayDeque<Long> open = new ArrayDeque<>();
            open.add(start);

            ChunkPos sp = new ChunkPos(start);
            int minX = sp.x, maxX = sp.x, minZ = sp.z, maxZ = sp.z, count = 0;

            while (!open.isEmpty()) {
                ChunkPos cp = new ChunkPos(open.poll());
                count++;
                minX = Math.min(minX, cp.x);
                maxX = Math.max(maxX, cp.x);
                minZ = Math.min(minZ, cp.z);
                maxZ = Math.max(maxZ, cp.z);

                for (int dx = -link; dx <= link; dx++) {
                    for (int dz = -link; dz <= link; dz++) {
                        long next = ChunkPos.toLong(cp.x + dx, cp.z + dz);
                        if (flaggedSet.contains(next) && seen.add(next)) open.add(next);
                    }
                }
            }

            if (count >= minGroup.get()) result.add(new int[]{minX, minZ, maxX, maxZ, count});
        }

        bounds = result;
    }

    private void drawBounds(Render3DEvent event, int[] b) {
        SettingColor line = boundsColor.get();
        double x1 = b[0] * 16.0;
        double z1 = b[1] * 16.0;
        double x2 = (b[2] + 1) * 16.0;
        double z2 = (b[3] + 1) * 16.0;
        double y1 = renderY.get();
        double y2 = y1 + slabHeight.get() + 1.0;
        Color none = new Color(0, 0, 0, 0);

        int passes = thickness.get() + 1;
        for (int k = 0; k < passes; k++) {
            double g = (k - (passes - 1) / 2.0) * 0.06;
            event.renderer.box(x1 - g, y1 - g, z1 - g, x2 + g, y2 + g, z2 + g, none, line, ShapeMode.Lines, 0);
        }
    }

    private void draw(Render3DEvent event, ChunkPos cp, SettingColor line) {
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

        if (tracers.get() && RenderUtils.center != null) {
            event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, x1 + 8.0, y2, z1 + 8.0, line);
        }
    }

    // -------------------------------------------------------------- persistence

    private String saveKey() {
        var entry = mc.getCurrentServerEntry();
        String server = entry != null ? entry.address : "singleplayer";
        return server + "|" + mc.world.getRegistryKey().getValue();
    }

    private void markDirty() {
        if (!dirty) dirtySince = tick;
        dirty = true;
    }

    private JsonObject loadSaveFile() {
        try {
            if (!Files.exists(SAVE_FILE)) return new JsonObject();
            return JsonParser.parseString(Files.readString(SAVE_FILE)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            return new JsonObject();
        }
    }

    private void loadHits() {
        if (saveRoot == null || activeKey == null) return;

        try {
            JsonElement entry = saveRoot.get(activeKey);
            if (entry == null || !entry.isJsonObject()) return;

            for (Map.Entry<String, JsonElement> e : entry.getAsJsonObject().entrySet()) {
                try {
                    String[] parts = e.getKey().split(",");
                    long key = ChunkPos.toLong(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                    int mask = e.getValue().getAsInt() & STATIC_BITS;
                    if (mask != 0) staticHits.put(key, mask);
                } catch (RuntimeException ignored) {
                    // skip a broken entry
                }
            }
        } catch (RuntimeException ignored) {
            // corrupt file section: start clean
        }
    }

    private void save(boolean async) {
        if (activeKey == null) return;

        JsonObject chunks = new JsonObject();
        for (Map.Entry<Long, Integer> e : staticHits.entrySet()) {
            ChunkPos cp = new ChunkPos(e.getKey());
            chunks.addProperty(cp.x + "," + cp.z, e.getValue());
        }

        if (saveRoot == null) saveRoot = new JsonObject();
        saveRoot.add(activeKey, chunks);
        dirty = false;

        String json = new Gson().toJson(saveRoot);
        Runnable write = () -> {
            try {
                Files.createDirectories(SAVE_FILE.getParent());
                Files.writeString(SAVE_FILE, json);
            } catch (IOException ignored) {
                // saving is best effort
            }
        };

        if (async) CompletableFuture.runAsync(write);
        else write.run();
    }
}
