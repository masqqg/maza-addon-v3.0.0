package com.maza.client.module.modules.world;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Prime Chunk Finder - Standalone Fabric
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

    private static final int LIGHT_SETTLE_TICKS = 6;
    private static final int LIGHT_SCANS_PER_TICK = 4;
    private static final int GROUP_INTERVAL = 10;

    private static final int SCANS_PER_TICK = 2;
    private static final int ENTITY_INTERVAL = 40;
    private static final int SAVE_DELAY_TICKS = 100;
    private static final Path SAVE_FILE = FabricLoader.getInstance().getConfigDir()
        .resolve("maza").resolve("maza-prime-chunks.json");

    // Settings
    public final IntSetting range = addSetting(new IntSetting.Builder()
        .name("range").defaultValue(16).min(1).sliderMax(32).build());

    public final IntSetting minMethods = addSetting(new IntSetting.Builder()
        .name("min-methods").defaultValue(1).min(1).max(9).sliderMin(1).sliderMax(9).build());

    public final BoolSetting notify = addSetting(new BoolSetting.Builder()
        .name("notify").defaultValue(false).build());

    public final BoolSetting soundOn = addSetting(new BoolSetting.Builder()
        .name("sound").defaultValue(true).build());

    public final IntSetting minSignals = addSetting(new IntSetting.Builder()
        .name("min-signals").defaultValue(2).min(1).max(10).sliderMax(10).build());

    public final IntSetting windowMs = addSetting(new IntSetting.Builder()
        .name("signal-window-ms").defaultValue(2500).min(250).max(10000).sliderMax(10000).build());

    public final IntSetting soundHoldSeconds = addSetting(new IntSetting.Builder()
        .name("hold-seconds").defaultValue(60).min(5).max(600).sliderMin(5).sliderMax(600).build());

    public final BoolSetting piston = addSetting(new BoolSetting.Builder()
        .name("pistons").defaultValue(true).build());

    public final BoolSetting observer = addSetting(new BoolSetting.Builder()
        .name("observers").defaultValue(true).build());

    public final BoolSetting redstone = addSetting(new BoolSetting.Builder()
        .name("redstone-sounds").defaultValue(true).build());

    public final BoolSetting storageSounds = addSetting(new BoolSetting.Builder()
        .name("storage-sounds").defaultValue(true).build());

    public final BoolSetting doorSounds = addSetting(new BoolSetting.Builder()
        .name("door-sounds").defaultValue(false).build());

    public final BoolSetting maskOn = addSetting(new BoolSetting.Builder()
        .name("deepslate-mask").defaultValue(true).build());

    public final IntSetting maskAboveY = addSetting(new IntSetting.Builder()
        .name("above-y").defaultValue(16).min(0).max(200).sliderMin(0).sliderMax(200).build());

    public final IntSetting maskPercent = addSetting(new IntSetting.Builder()
        .name("min-percent").defaultValue(35).min(5).max(100).sliderMin(5).sliderMax(100).build());

    public final IntSetting maskSections = addSetting(new IntSetting.Builder()
        .name("min-sections").defaultValue(1).min(1).max(8).sliderMin(1).sliderMax(8).build());

    public final BoolSetting edgeOn = addSetting(new BoolSetting.Builder()
        .name("mask-edge").defaultValue(true).build());

    public final IntSetting edgeMin = addSetting(new IntSetting.Builder()
        .name("edge-min-contacts").defaultValue(4).min(1).max(100).sliderMin(1).sliderMax(50).build());

    public final BoolSetting censusOn = addSetting(new BoolSetting.Builder()
        .name("block-census").defaultValue(true).build());

    public final IntSetting censusMin = addSetting(new IntSetting.Builder()
        .name("min-score").defaultValue(14).min(1).max(400).sliderMin(1).sliderMax(100).build());

    public final BoolSetting storageOn = addSetting(new BoolSetting.Builder()
        .name("storage").defaultValue(true).build());

    public final IntSetting storageMin = addSetting(new IntSetting.Builder()
        .name("min-blocks").defaultValue(10).min(1).max(200).sliderMin(1).sliderMax(60).build());

    public final BoolSetting entityOn = addSetting(new BoolSetting.Builder()
        .name("entities").defaultValue(true).build());

    public final IntSetting entityMin = addSetting(new IntSetting.Builder()
        .name("min-score").defaultValue(10).min(1).max(200).sliderMin(1).sliderMax(60).build());

    public final BoolSetting paletteOn = addSetting(new BoolSetting.Builder()
        .name("palette-leak").defaultValue(true).build());

    public final IntSetting paletteCategories = addSetting(new IntSetting.Builder()
        .name("min-categories").defaultValue(4).min(2).max(8).sliderMin(2).sliderMax(8).build());

    public final IntSetting paletteSections = addSetting(new IntSetting.Builder()
        .name("min-sections").defaultValue(2).min(1).max(12).sliderMin(1).sliderMax(12).build());

    public final BoolSetting lightOn = addSetting(new BoolSetting.Builder()
        .name("light-signature").defaultValue(true).build());

    public final IntSetting lightCeiling = addSetting(new IntSetting.Builder()
        .name("ceiling-y").defaultValue(62).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    public final IntSetting lightSections = addSetting(new IntSetting.Builder()
        .name("min-sealed-sections").defaultValue(1).min(1).max(12).sliderMin(1).sliderMax(12).build());

    public final BoolSetting activityOn = addSetting(new BoolSetting.Builder()
        .name("light-activity").defaultValue(true).build());

    public final IntSetting activityIgnore = addSetting(new IntSetting.Builder()
        .name("ignore-radius").defaultValue(0).min(0).max(32).sliderMin(0).sliderMax(32).build());

    public final IntSetting activityMin = addSetting(new IntSetting.Builder()
        .name("min-updates").defaultValue(3).min(1).max(30).sliderMin(1).sliderMax(30).build());

    public final IntSetting activityWindow = addSetting(new IntSetting.Builder()
        .name("window-seconds").defaultValue(30).min(2).max(300).sliderMin(2).sliderMax(300).build());

    public final IntSetting activityHold = addSetting(new IntSetting.Builder()
        .name("hold-seconds").defaultValue(120).min(5).max(1800).sliderMin(5).sliderMax(1800).build());

    public final IntSetting activitySettle = addSetting(new IntSetting.Builder()
        .name("settle-seconds").defaultValue(3).min(0).max(30).sliderMin(0).sliderMax(30).build());

    public final IntSetting renderY = addSetting(new IntSetting.Builder()
        .name("render-y").defaultValue(64).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    public final DoubleSetting slabHeight = addSetting(new DoubleSetting.Builder()
        .name("slab-height").defaultValue(1.0).min(0.1).max(8.0).sliderMin(0.1).sliderMax(8.0).build());

    public final IntSetting thickness = addSetting(new IntSetting.Builder()
        .name("outline-thickness").defaultValue(3).min(1).max(8).sliderMin(1).sliderMax(8).build());

    public final BoolSetting tracers = addSetting(new BoolSetting.Builder()
        .name("tracers").defaultValue(false).build());

    public final ColorSetting color = addSetting(new ColorSetting.Builder()
        .name("color").defaultValue(new SettingColor(0, 255, 0, 230)).build());

    public final BoolSetting showBounds = addSetting(new BoolSetting.Builder()
        .name("group-outline").defaultValue(true).build());

    public final IntSetting linkRadius = addSetting(new IntSetting.Builder()
        .name("group-link-chunks").defaultValue(3).min(1).max(10).sliderMin(1).sliderMax(10).build());

    public final IntSetting minGroup = addSetting(new IntSetting.Builder()
        .name("group-min-chunks").defaultValue(2).min(1).max(50).sliderMin(1).sliderMax(50).build());

    public final ColorSetting boundsColor = addSetting(new ColorSetting.Builder()
        .name("group-color").defaultValue(new SettingColor(0, 220, 255, 230)).build());

    public final ColorSetting strongColor = addSetting(new ColorSetting.Builder()
        .name("strong-color").defaultValue(new SettingColor(255, 220, 0, 230)).build());

    // State
    private final Map<Long, Integer> staticHits = new HashMap<>();
    private final Map<Long, Long> soundUntil = new HashMap<>();
    private final Map<Long, Long> entityUntil = new HashMap<>();
    private final Map<Long, Deque<Long>> signals = new HashMap<>();
    private final Map<Long, Long> lastSound = new HashMap<>();
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private final Set<Long> announced = new HashSet<>();
    private final IdentityHashMap<Block, Integer> weights = new IdentityHashMap<>();
    private final IdentityHashMap<Block, Integer> categories = new IdentityHashMap<>();
    private final Map<Long, Integer> lightDue = new LinkedHashMap<>();
    private final Map<Long, Long> loadedAt = new HashMap<>();
    private final Map<Long, Deque<Long>> activityTimes = new HashMap<>();
    private final Map<Long, Long> activityUntil = new HashMap<>();
    private List<int[]> bounds = new ArrayList<>();

    private JsonObject saveRoot;
    private String activeKey;
    private ClientWorld lastWorld;
    private boolean dirty;
    private int dirtySince;
    private int tick;

    public PrimeChunkFinder() {
        super("PrimeChunkFinder", "Flags chunks that sound, look or hold like a base", Category.WORLD);
    }

    @Override
    public void onEnable() {
        clearAll();
        lastWorld = null;
        activeKey = null;
        saveRoot = loadSaveFile();
    }

    @Override
    public void onDisable() {
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

    @EventHandler
    public void onTick(TickEvent.Post event) {
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
            if (activeKey != null) save(false);
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
    public void onPacket(PacketEvent.Receive event) {
        if (event.packet instanceof ChunkDataS2CPacket chunkData) {
            if (mc.world == null) return;
            long key = ChunkPos.toLong(chunkData.getChunkX(), chunkData.getChunkZ());
            loadedAt.put(key, System.currentTimeMillis());
            activityTimes.remove(key);
            activityUntil.remove(key);

            enqueue(key);
            scheduleLight(key);
            return;
        }

        if (event.packet instanceof LightUpdateS2CPacket light) {
            int cx = light.getChunkX();
            int cz = light.getChunkZ();
            mc.execute(() -> handleLightPacket(cx, cz));
            return;
        }

        if (!soundOn.get() || !(event.packet instanceof PlaySoundS2CPacket packet)) return;

        double x = packet.getX();
        double z = packet.getZ();
        var soundId = Registries.SOUND_EVENT.getId(packet.getSound().value());
        if (soundId == null) return;
        String id = soundId.getPath();

        mc.execute(() -> handleSound(x, z, id));
    }

    private void handleLightPacket(int cx, int cz) {
        if (mc.world == null || mc.player == null) return;

        long key = ChunkPos.toLong(cx, cz);
        scheduleLight(key);
        if (activityOn.get()) recordActivity(cx, cz, key);
    }

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
            if (sectionY * 16 + 15 >= ceiling) break;

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
        mask |= before & LIGHT;
        if (mask == 0) staticHits.remove(key);
        else staticHits.put(key, mask);

        if (before != mask) {
            markDirty();
            if (notify.get() && mask != 0 && before == 0 && announced.add(key)) {
                ChunkPos cp = chunk.getPos();
                ChatUtils.info(String.format("Chunk %d, %d flagged (%s)", cp.x, cp.z, describe(mask)));
            }
        }
    }

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

    private static final int CATEGORY_COUNT = 8;

    private int categoriesIn(ChunkSection section) {
        int found = 0;
        for (int c = 0; c < CATEGORY_COUNT; c++) {
            final int wanted = c;
            if (section.hasAny(state -> categoryOf(state.getBlock()) == wanted)) found++;
        }
        return found;
    }

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

    private void scanEntities() {
        Map<Long, Double> scores = new HashMap<>();

        for (Entity entity : mc.world.getEntities()) {
            double w;
            if (entity instanceof ItemFrameEntity || entity instanceof ArmorStandEntity || entity instanceof VillagerEntity) w = 1.0;
            else if (entity instanceof AnimalEntity) w = 0.25;
            else continue;

            long key = ChunkPos.toLong(entity.getBlockX() >> 4, entity.getBlockZ() >> 4);
            scores.merge(key, w, Double::sum);
        }

        long until = System.currentTimeMillis() + 10_000L;
        for (Map.Entry<Long, Double> e : scores.entrySet()) {
            if (e.getValue() >= entityMin.get()) entityUntil.put(e.getKey(), until);
        }
    }

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
    public void onRender(Render3DEvent event) {
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

            draw(cp, hits >= 2 ? strongColor.get() : color.get());
        }

        if (showBounds.get()) {
            for (int[] b : bounds) {
                if (b[2] < pc.x - max || b[0] > pc.x + max || b[3] < pc.z - max || b[1] > pc.z + max) continue;
                drawBounds(b);
            }
        }
    }

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

    private void drawBounds(int[] b) {
        SettingColor line = boundsColor.get();
        double x1 = b[0] * 16.0;
        double z1 = b[1] * 16.0;
        double x2 = (b[2] + 1) * 16.0;
        double z2 = (b[3] + 1) * 16.0;
        double y1 = renderY.get();
        double y2 = y1 + slabHeight.get() + 1.0;

        Render3DUtils.drawBox(new Box(x1, y1, z1, x2, y2, z2), null, line, ShapeMode.Lines);
    }

    private void draw(ChunkPos cp, SettingColor line) {
        double x1 = cp.getStartX();
        double z1 = cp.getStartZ();
        double x2 = x1 + 16.0;
        double z2 = z1 + 16.0;
        double y1 = renderY.get();
        double y2 = y1 + slabHeight.get();

        SettingColor fill = new SettingColor(line.r, line.g, line.b, Math.max(20, line.a / 4));
        Render3DUtils.drawBox(new Box(x1, y1, z1, x2, y2, z2), fill, line, ShapeMode.Both);

        if (tracers.get() && mc.player != null) {
            double[] start = tracerStart();
            Vec3d s = new Vec3d(start[0], start[1], start[2]);
            Render3DUtils.drawLine(s, new Vec3d(x1 + 8.0, y2, z1 + 8.0), line, 1.5f);
        }
    }

    private double[] tracerStart() {
        if (mc.player == null) return new double[]{0, 0, 0};
        double yaw = Math.toRadians(mc.player.getYaw());
        double pitch = Math.toRadians(mc.player.getPitch());
        double cosPitch = Math.cos(pitch);
        double lx = -Math.sin(yaw) * cosPitch;
        double ly = -Math.sin(pitch);
        double lz = Math.cos(yaw) * cosPitch;
        return new double[]{
            mc.player.getX() + lx * 0.6,
            mc.player.getEyeY() + ly * 0.6,
            mc.player.getZ() + lz * 0.6
        };
    }

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
                } catch (RuntimeException ignored) {}
            }
        } catch (RuntimeException ignored) {}
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
            } catch (IOException ignored) {}
        };

        if (async) CompletableFuture.runAsync(write);
        else write.run();
    }
}
