package com.maza.addon.modules;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Spawner Flag
 *
 * Every spawner the client sees is remembered, per server and dimension, and drawn as a
 * thin red column from the bottom of the world to the build limit. The list is saved to
 * disk: after a relog the flags are there right away.
 *
 * Natural spawners are told apart by the 14x14x14 box around them:
 *  Dungeon:    the room around the spawner is closed, its walls are cobblestone and mossy
 *              cobblestone, and one or two chests stand in it.
 *  Mineshaft:  cobwebs together with rails, planks or fences (cave spider spawners).
 *  Fortress:   a lot of nether bricks (blaze spawners).
 *  Stronghold: stone bricks next to end portal frames (silverfish spawners).
 * Natural ones are hidden, or drawn in another colour, as set. Everything else is flagged.
 *
 * A flag is removed when the spawner is seen to be gone (a block update replaces it, or its
 * chunk loads and the block reads as air). A chunk that merely does not show the spawner,
 * for example because the server hides it, never deletes a flag.
 */
public class SpawnerFlag extends Module {
    public enum NaturalMode { Hide, Mark }

    // Kinds of a remembered spawner.
    private static final int PENDING = 0;     // not looked at yet, chunks around it still loading
    private static final int PLAYER = 1;      // flagged
    private static final int DUNGEON = 2;
    private static final int MINESHAFT = 3;
    private static final int FORTRESS = 4;
    private static final int STRONGHOLD = 5;

    private static final int BOX_BELOW = 7;   // 14 blocks: 7 below / 6 above the spawner on every axis
    private static final int BOX_ABOVE = 6;
    private static final int ROOM_LIMIT = 600; // a dungeon room is far smaller than this
    private static final int GIVE_UP_CHECKS = 12; // 12 checks, 2 seconds apart, then it counts as player-made
    private static final int CHECK_INTERVAL = 40;
    private static final int SAVE_DELAY_TICKS = 100;   // 5 s after the last change
    private static final Path SAVE_FILE = FabricLoader.getInstance().getGameDir()
        .resolve("meteor-client").resolve("maza-spawner-flags.json");

    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup naturalGroup = settings.createGroup("Natural Spawners");
    private final SettingGroup rendering = settings.createGroup("Render");

    private final Setting<Boolean> notify = general.add(new BoolSetting.Builder()
        .name("notify")
        .description("Chat message when a new player-made spawner is found.")
        .defaultValue(false).build());

    // ---- natural spawners
    private final Setting<NaturalMode> naturalMode = naturalGroup.add(new EnumSetting.Builder<NaturalMode>()
        .name("natural-spawners")
        .description("Hide the spawners that belong to a natural structure, or draw them in their own colour.")
        .defaultValue(NaturalMode.Hide).build());

    private final Setting<Boolean> detectDungeons = naturalGroup.add(new BoolSetting.Builder()
        .name("dungeons")
        .description("Closed room of cobblestone and mossy cobblestone with one or two chests.")
        .defaultValue(true).build());

    private final Setting<Boolean> detectMineshafts = naturalGroup.add(new BoolSetting.Builder()
        .name("mineshafts")
        .description("Cobwebs together with rails, planks or fences.")
        .defaultValue(true).build());

    private final Setting<Boolean> detectFortresses = naturalGroup.add(new BoolSetting.Builder()
        .name("nether-fortresses")
        .description("A lot of nether bricks around the spawner.")
        .defaultValue(true).build());

    private final Setting<Boolean> detectStrongholds = naturalGroup.add(new BoolSetting.Builder()
        .name("strongholds")
        .description("Stone bricks next to end portal frames.")
        .defaultValue(true).build());

    private final Setting<SettingColor> naturalColor = naturalGroup.add(new ColorSetting.Builder()
        .name("natural-color")
        .description("Colour of natural spawners when they are marked instead of hidden.")
        .defaultValue(new SettingColor(255, 200, 0, 220))
        .visible(() -> naturalMode.get() == NaturalMode.Mark).build());

    // ---- render
    private final Setting<Integer> minY = rendering.add(new IntSetting.Builder()
        .name("min-y").defaultValue(-64).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Integer> maxY = rendering.add(new IntSetting.Builder()
        .name("max-y").defaultValue(300).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    private final Setting<Integer> widthPixels = rendering.add(new IntSetting.Builder()
        .name("width-pixels")
        .description("Width of the column in pixels, 16 pixels are one block.")
        .defaultValue(4).min(1).max(16).sliderMin(1).sliderMax(16).build());

    private final Setting<Integer> range = rendering.add(new IntSetting.Builder()
        .name("range")
        .description("Max distance in blocks for drawn flags.")
        .defaultValue(2048).min(64).max(30000).sliderMin(64).sliderMax(8192).build());

    private final Setting<Integer> maxDrawn = rendering.add(new IntSetting.Builder()
        .name("max-flags")
        .description("Max flags drawn at once (nearest first).")
        .defaultValue(256).min(1).sliderMax(1024).build());

    private final Setting<Boolean> blockOutline = rendering.add(new BoolSetting.Builder()
        .name("block-outline")
        .description("Also outline the spawner block itself.")
        .defaultValue(false).build());

    private final Setting<SettingColor> color = rendering.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(255, 0, 0, 255)).build());

    // Only touched on the client thread.
    private final Map<Long, Integer> kinds = new LinkedHashMap<>();   // spawner -> kind
    private final Map<Long, Integer> checks = new HashMap<>();        // pending spawner -> checks so far
    private JsonObject saveRoot;
    private String activeKey;
    private ClientWorld lastWorld;
    private boolean dirty;
    private int tick;
    private int dirtySince;

    public SpawnerFlag() {
        super(MazaCategory.INSTANCE, "spawner-flag",
            "Remembers every player-made spawner you see and marks it with a tall red line, also after a relog.");
    }

    @Override
    public void onActivate() {
        kinds.clear();
        checks.clear();
        lastWorld = null;
        activeKey = null;
        dirty = false;
        saveRoot = loadSaveFile();
    }

    @Override
    public void onDeactivate() {
        save(false);
        kinds.clear();
        checks.clear();
        lastWorld = null;
        activeKey = null;
    }

    @Override
    public String getInfoString() {
        int count = 0;
        for (int kind : kinds.values()) if (kind == PLAYER) count++;
        return count == 0 ? null : String.valueOf(count);
    }

    // ----------------------------------------------------------------- events

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) {
            if (activeKey != null) {
                save(false);
                kinds.clear();
                checks.clear();
                activeKey = null;
            }
            lastWorld = null;
            return;
        }

        if (mc.world != lastWorld) {
            if (activeKey != null) save(false); // still the old world's key and flags
            kinds.clear();
            checks.clear();
            lastWorld = mc.world;
            activeKey = saveKey();
            loadFlags();
            scanLoadedChunks();
        }

        tick++;
        if (tick % CHECK_INTERVAL == 0) classifyPending();
        if (dirty && tick - dirtySince >= SAVE_DELAY_TICKS) save(true);
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null || event.chunk() == null || activeKey == null) return;
        scanChunk(event.chunk());
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null || event.pos == null || activeKey == null) return;

        long packed = event.pos.asLong();
        if (event.newState.isOf(Blocks.SPAWNER)) {
            if (!kinds.containsKey(packed)) {
                kinds.put(packed, PENDING);
                checks.put(packed, 0);
                markDirty();
            }
        } else if (kinds.remove(packed) != null) {
            checks.remove(packed);
            markDirty(); // seen to be gone
        }
    }

    // --------------------------------------------------------------- scanning

    private void scanLoadedChunks() {
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;
        int radius = Math.max(2, mc.options.getViewDistance().getValue());

        for (int x = pcx - radius; x <= pcx + radius; x++) {
            for (int z = pcz - radius; z <= pcz + radius; z++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x, z);
                if (chunk != null) scanChunk(chunk);
            }
        }
    }

    private void scanChunk(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        if (sections == null) return;

        ChunkPos cp = chunk.getPos();
        int bottom = chunk.getBottomY();
        Set<Long> found = new HashSet<>();

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            if (!section.hasAny(state -> state.isOf(Blocks.SPAWNER))) continue;

            int sectionBottom = bottom + i * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!section.getBlockState(x, y, z).isOf(Blocks.SPAWNER)) continue;
                        found.add(BlockPos.asLong(cp.getStartX() + x, sectionBottom + y, cp.getStartZ() + z));
                    }
                }
            }
        }

        boolean changed = false;

        // A remembered flag only goes when its block now reads as air: really mined away.
        // Anything else (stone from anti-xray, a section the server left out) keeps the flag.
        for (Iterator<Long> it = kinds.keySet().iterator(); it.hasNext(); ) {
            long packed = it.next();
            if ((BlockPos.unpackLongX(packed) >> 4) != cp.x || (BlockPos.unpackLongZ(packed) >> 4) != cp.z) continue;
            if (found.contains(packed)) continue;

            BlockState state = chunk.getBlockState(BlockPos.fromLong(packed));
            if (state.isAir()) {
                it.remove();
                checks.remove(packed);
                changed = true;
            }
        }

        for (long packed : found) {
            if (kinds.containsKey(packed)) continue;

            kinds.put(packed, PENDING);
            checks.put(packed, 0);
            changed = true;
        }

        if (changed) markDirty();
    }

    // ---------------------------------------------------------- classification

    /** Looks at every spawner that has not been classified, as soon as the chunks around it are loaded. */
    private void classifyPending() {
        if (checks.isEmpty()) return;

        boolean changed = false;
        List<Long> pending = new ArrayList<>(checks.keySet());

        for (long packed : pending) {
            if (!kinds.containsKey(packed)) {
                checks.remove(packed);
                continue;
            }

            int x = BlockPos.unpackLongX(packed);
            int y = BlockPos.unpackLongY(packed);
            int z = BlockPos.unpackLongZ(packed);

            int kind;
            if (boxLoaded(x, z)) {
                kind = classify(x, y, z);
            } else {
                int tries = checks.merge(packed, 1, Integer::sum);
                if (tries < GIVE_UP_CHECKS) continue; // wait for the neighbouring chunks
                kind = PLAYER;                        // never fully seen: do not hide it
            }

            kinds.put(packed, kind);
            checks.remove(packed);
            changed = true;

            if (kind == PLAYER && notify.get()) info("Spawner at %d, %d, %d", x, y, z);
        }

        if (changed) markDirty();
    }

    /** Every chunk the 14x14x14 box touches has to be loaded to judge it. */
    private boolean boxLoaded(int x, int z) {
        for (int cx = (x - BOX_BELOW) >> 4; cx <= (x + BOX_ABOVE) >> 4; cx++) {
            for (int cz = (z - BOX_BELOW) >> 4; cz <= (z + BOX_ABOVE) >> 4; cz++) {
                if (mc.world.getChunkManager().getWorldChunk(cx, cz) == null) return false;
            }
        }
        return true;
    }

    private int classify(int sx, int sy, int sz) {
        BlockPos.Mutable pos = new BlockPos.Mutable();

        int cobwebs = 0;
        int rails = 0;
        int planks = 0;
        int fences = 0;
        int netherBricks = 0;
        int stoneBricks = 0;
        int portalFrames = 0;

        for (int y = sy - BOX_BELOW; y <= sy + BOX_ABOVE; y++) {
            for (int z = sz - BOX_BELOW; z <= sz + BOX_ABOVE; z++) {
                for (int x = sx - BOX_BELOW; x <= sx + BOX_ABOVE; x++) {
                    BlockState state = mc.world.getBlockState(pos.set(x, y, z));
                    if (state.isAir()) continue;

                    if (state.isOf(Blocks.COBWEB)) cobwebs++;
                    else if (state.isOf(Blocks.RAIL)) rails++;
                    else if (state.isOf(Blocks.OAK_PLANKS)) planks++;
                    else if (state.isOf(Blocks.OAK_FENCE)) fences++;
                    else if (state.isOf(Blocks.NETHER_BRICKS)) netherBricks++;
                    else if (state.isOf(Blocks.STONE_BRICKS) || state.isOf(Blocks.MOSSY_STONE_BRICKS)
                        || state.isOf(Blocks.CRACKED_STONE_BRICKS)) stoneBricks++;
                    else if (state.isOf(Blocks.END_PORTAL_FRAME)) portalFrames++;
                }
            }
        }

        if (detectMineshafts.get() && cobwebs >= 4 && (rails >= 2 || planks >= 6 || fences >= 4)) return MINESHAFT;
        if (detectFortresses.get() && netherBricks >= 30) return FORTRESS;
        if (detectStrongholds.get() && stoneBricks >= 40 && portalFrames >= 1) return STRONGHOLD;
        if (detectDungeons.get() && isDungeonRoom(sx, sy, sz)) return DUNGEON;

        return PLAYER;
    }

    /**
     * A dungeon room: the air around the spawner is a small closed space, its walls are
     * cobblestone and mossy cobblestone, at least some of it mossy, and one or two chests
     * stand in it. The fill starts next to the spawner and may not reach the edge of the
     * 14x14x14 box, so a big cave or a base never counts.
     */
    private boolean isDungeonRoom(int sx, int sy, int sz) {
        int loX = sx - BOX_BELOW, hiX = sx + BOX_ABOVE;
        int loY = sy - BOX_BELOW, hiY = sy + BOX_ABOVE;
        int loZ = sz - BOX_BELOW, hiZ = sz + BOX_ABOVE;

        BlockPos.Mutable pos = new BlockPos.Mutable();
        Set<Long> room = new HashSet<>();
        ArrayDeque<Long> open = new ArrayDeque<>();
        room.add(BlockPos.asLong(sx, sy, sz));

        int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

        for (int[] d : around) {
            long start = BlockPos.asLong(sx + d[0], sy + d[1], sz + d[2]);
            if (passable(pos, sx + d[0], sy + d[1], sz + d[2]) && room.add(start)) open.add(start);
        }
        if (open.isEmpty()) return false;

        int chests = 0;

        while (!open.isEmpty()) {
            long cell = open.poll();
            int x = BlockPos.unpackLongX(cell);
            int y = BlockPos.unpackLongY(cell);
            int z = BlockPos.unpackLongZ(cell);

            // Reaching the edge of the box means the space goes on: not a closed room.
            if (x <= loX || x >= hiX || y <= loY || y >= hiY || z <= loZ || z >= hiZ) return false;
            if (room.size() > ROOM_LIMIT) return false;

            if (mc.world.getBlockState(pos.set(x, y, z)).isOf(Blocks.CHEST)) chests++;

            for (int[] d : around) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                long next = BlockPos.asLong(nx, ny, nz);
                if (room.contains(next) || !passable(pos, nx, ny, nz)) continue;

                room.add(next);
                open.add(next);
            }
        }

        if (chests < 1 || chests > 2) return false;

        // The shell: every solid block that touches the room.
        int shell = 0;
        int cobble = 0;
        int mossy = 0;
        Set<Long> seen = new HashSet<>();

        for (long cell : room) {
            int x = BlockPos.unpackLongX(cell);
            int y = BlockPos.unpackLongY(cell);
            int z = BlockPos.unpackLongZ(cell);

            for (int[] d : around) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                long next = BlockPos.asLong(nx, ny, nz);
                if (room.contains(next) || !seen.add(next)) continue;

                BlockState state = mc.world.getBlockState(pos.set(nx, ny, nz));
                shell++;
                if (state.isOf(Blocks.MOSSY_COBBLESTONE)) {
                    mossy++;
                    cobble++;
                } else if (state.isOf(Blocks.COBBLESTONE)) {
                    cobble++;
                }
            }
        }

        // Enough wall to be a room, almost all of it cobblestone, some of it mossy.
        return shell >= 20 && cobble * 100 >= shell * 90 && mossy >= 1;
    }

    /** Open space of a room: air, the spawner itself and the chests that stand in it. */
    private boolean passable(BlockPos.Mutable pos, int x, int y, int z) {
        BlockState state = mc.world.getBlockState(pos.set(x, y, z));
        return state.isAir() || state.isOf(Blocks.SPAWNER) || state.isOf(Blocks.CHEST);
    }

    // ----------------------------------------------------------------- render

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null || kinds.isEmpty()) return;

        boolean markNatural = naturalMode.get() == NaturalMode.Mark;

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        double reach = range.get();
        double reachSq = reach * reach;

        List<long[]> near = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : kinds.entrySet()) {
            int kind = entry.getValue();
            if (kind == PENDING) continue;                 // not judged yet
            if (kind != PLAYER && !markNatural) continue;  // natural and hidden

            long packed = entry.getKey();
            double dx = BlockPos.unpackLongX(packed) + 0.5 - px;
            double dz = BlockPos.unpackLongZ(packed) + 0.5 - pz;
            double d = dx * dx + dz * dz;
            if (d <= reachSq) near.add(new long[]{packed, Double.doubleToLongBits(d), kind});
        }
        if (near.isEmpty()) return;

        near.sort(Comparator.comparingDouble(a -> Double.longBitsToDouble(a[1])));
        int limit = Math.min(near.size(), maxDrawn.get());

        Color none = new Color(0, 0, 0, 0);
        double half = widthPixels.get() / 32.0; // pixels / 16 is the width, half of it each side
        double y1 = Math.min(minY.get(), maxY.get());
        double y2 = Math.max(minY.get(), maxY.get());

        for (int i = 0; i < limit; i++) {
            long packed = near.get(i)[0];
            SettingColor line = near.get(i)[2] == PLAYER ? color.get() : naturalColor.get();
            Color fill = new Color(line.r, line.g, line.b, Math.max(40, line.a / 2));

            double x = BlockPos.unpackLongX(packed);
            double y = BlockPos.unpackLongY(packed);
            double z = BlockPos.unpackLongZ(packed);

            event.renderer.box(x + 0.5 - half, y1, z + 0.5 - half, x + 0.5 + half, y2, z + 0.5 + half,
                fill, line, ShapeMode.Both, 0);

            if (blockOutline.get()) {
                event.renderer.box(x, y, z, x + 1, y + 1, z + 1, none, line, ShapeMode.Lines, 0);
            }
        }
    }

    // ------------------------------------------------------------ persistence

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

    /**
     * Saved as {"player": [...], "natural": [[position, kind], ...]}. The first version of the
     * file was a plain list of positions: those come back as not judged yet and are looked at
     * again as soon as their chunks load.
     */
    private void loadFlags() {
        if (saveRoot == null || activeKey == null) return;

        try {
            JsonElement entry = saveRoot.get(activeKey);
            if (entry == null) return;

            if (entry.isJsonArray()) {
                for (JsonElement element : entry.getAsJsonArray()) addSaved(element, PENDING);
                return;
            }
            if (!entry.isJsonObject()) return;

            JsonObject object = entry.getAsJsonObject();

            if (object.has("player") && object.get("player").isJsonArray()) {
                for (JsonElement element : object.getAsJsonArray("player")) addSaved(element, PLAYER);
            }
            if (object.has("natural") && object.get("natural").isJsonArray()) {
                for (JsonElement element : object.getAsJsonArray("natural")) {
                    try {
                        JsonArray pair = element.getAsJsonArray();
                        kinds.put(pair.get(0).getAsLong(), pair.get(1).getAsInt());
                    } catch (RuntimeException ignored) {
                        // skip a broken entry
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // corrupt file section: start clean
        }
    }

    private void addSaved(JsonElement element, int kind) {
        try {
            long packed = element.getAsLong();
            kinds.put(packed, kind);
            if (kind == PENDING) checks.put(packed, 0);
        } catch (RuntimeException ignored) {
            // skip a broken entry
        }
    }

    private void save(boolean async) {
        if (activeKey == null) return;

        JsonArray player = new JsonArray();
        JsonArray natural = new JsonArray();

        for (Map.Entry<Long, Integer> entry : kinds.entrySet()) {
            int kind = entry.getValue();

            if (kind == PLAYER || kind == PENDING) {
                player.add(entry.getKey());
            } else {
                JsonArray pair = new JsonArray();
                pair.add(entry.getKey());
                pair.add(kind);
                natural.add(pair);
            }
        }

        JsonObject object = new JsonObject();
        object.add("player", player);
        object.add("natural", natural);

        if (saveRoot == null) saveRoot = new JsonObject();
        saveRoot.add(activeKey, object);
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
