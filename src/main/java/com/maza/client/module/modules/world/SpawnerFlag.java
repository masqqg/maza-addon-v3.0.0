package com.maza.client.module.modules.world;

import com.google.gson.*;
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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SpawnerFlag extends Module {
    public enum NaturalMode { Hide, Mark }

    private static final int PENDING = 0;
    private static final int PLAYER = 1;
    private static final int DUNGEON = 2;
    private static final int MINESHAFT = 3;
    private static final int FORTRESS = 4;
    private static final int STRONGHOLD = 5;

    private static final int BOX_BELOW = 7;
    private static final int BOX_ABOVE = 6;
    private static final int ROOM_LIMIT = 600;
    private static final int GIVE_UP_CHECKS = 12;
    private static final int CHECK_INTERVAL = 40;
    private static final int SAVE_DELAY_TICKS = 100;
    private static final Path SAVE_FILE = FabricLoader.getInstance().getConfigDir()
        .resolve("maza").resolve("maza-spawner-flags.json");

    public final BoolSetting notify = addSetting(new BoolSetting.Builder()
        .name("notify").description("Chat message when spawner found.").defaultValue(false).build());

    public final EnumSetting<NaturalMode> naturalMode = addSetting(new EnumSetting.Builder<NaturalMode>()
        .name("natural-spawners").defaultValue(NaturalMode.Hide).build());

    public final BoolSetting detectDungeons = addSetting(new BoolSetting.Builder()
        .name("dungeons").defaultValue(true).build());

    public final BoolSetting detectMineshafts = addSetting(new BoolSetting.Builder()
        .name("mineshafts").defaultValue(true).build());

    public final BoolSetting detectFortresses = addSetting(new BoolSetting.Builder()
        .name("nether-fortresses").defaultValue(true).build());

    public final BoolSetting detectStrongholds = addSetting(new BoolSetting.Builder()
        .name("strongholds").defaultValue(true).build());

    public final ColorSetting naturalColor = addSetting(new ColorSetting.Builder()
        .name("natural-color").defaultValue(new SettingColor(255, 200, 0, 220)).build());

    public final IntSetting minY = addSetting(new IntSetting.Builder()
        .name("min-y").defaultValue(-64).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    public final IntSetting maxY = addSetting(new IntSetting.Builder()
        .name("max-y").defaultValue(300).min(-64).max(320).sliderMin(-64).sliderMax(320).build());

    public final IntSetting widthPixels = addSetting(new IntSetting.Builder()
        .name("width-pixels").defaultValue(4).min(1).max(16).sliderMin(1).sliderMax(16).build());

    public final IntSetting range = addSetting(new IntSetting.Builder()
        .name("range").defaultValue(2048).min(64).max(30000).sliderMin(64).sliderMax(8192).build());

    public final IntSetting maxDrawn = addSetting(new IntSetting.Builder()
        .name("max-flags").defaultValue(256).min(1).sliderMax(1024).build());

    public final BoolSetting blockOutline = addSetting(new BoolSetting.Builder()
        .name("block-outline").defaultValue(false).build());

    public final ColorSetting color = addSetting(new ColorSetting.Builder()
        .name("color").defaultValue(new SettingColor(255, 0, 0, 255)).build());

    private final Map<Long, Integer> kinds = new LinkedHashMap<>();
    private final Map<Long, Integer> checks = new HashMap<>();
    private JsonObject saveRoot;
    private String activeKey;
    private ClientWorld lastWorld;
    private boolean dirty;
    private int tick;
    private int dirtySince;

    public SpawnerFlag() {
        super("SpawnerFlag", "Marks player-made spawners with persistent beacons", Category.WORLD);
    }

    @Override
    public void onEnable() {
        kinds.clear();
        checks.clear();
        lastWorld = null;
        activeKey = null;
        dirty = false;
        saveRoot = loadSaveFile();
    }

    @Override
    public void onDisable() {
        save(false);
        kinds.clear();
        checks.clear();
        lastWorld = null;
        activeKey = null;
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
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
            if (activeKey != null) save(false);
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
    public void onPacket(PacketEvent.Receive event) {
        if (event.packet instanceof BlockUpdateS2CPacket blockUpdate) {
            if (mc.world == null || activeKey == null) return;
            long packed = blockUpdate.getPos().asLong();
            if (blockUpdate.getState().isOf(Blocks.SPAWNER)) {
                if (!kinds.containsKey(packed)) {
                    kinds.put(packed, PENDING);
                    checks.put(packed, 0);
                    markDirty();
                }
            } else if (kinds.remove(packed) != null) {
                checks.remove(packed);
                markDirty();
            }
        } else if (event.packet instanceof ChunkDataS2CPacket chunkData) {
            if (mc.world == null || activeKey == null) return;
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(chunkData.getChunkX(), chunkData.getChunkZ());
            if (chunk != null) scanChunk(chunk);
        }
    }

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

    private void classifyPending() {
        if (checks.isEmpty()) return;

        boolean changed = false;
        List<Long> pendingList = new ArrayList<>(checks.keySet());

        for (long packed : pendingList) {
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
                if (tries < GIVE_UP_CHECKS) continue;
                kind = PLAYER;
            }

            kinds.put(packed, kind);
            checks.remove(packed);
            changed = true;

            if (kind == PLAYER && notify.get()) {
                ChatUtils.info(String.format("Spawner at %d, %d, %d", x, y, z));
            }
        }

        if (changed) markDirty();
    }

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
        int cobwebs = 0, rails = 0, planks = 0, fences = 0, netherBricks = 0, stoneBricks = 0, portalFrames = 0;

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
                    else if (state.isOf(Blocks.STONE_BRICKS) || state.isOf(Blocks.MOSSY_STONE_BRICKS) || state.isOf(Blocks.CRACKED_STONE_BRICKS)) stoneBricks++;
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

        int shell = 0, cobble = 0, mossy = 0;
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
                    mossy++; cobble++;
                } else if (state.isOf(Blocks.COBBLESTONE)) {
                    cobble++;
                }
            }
        }
        return shell >= 20 && cobble * 100 >= shell * 90 && mossy >= 1;
    }

    private boolean passable(BlockPos.Mutable pos, int x, int y, int z) {
        BlockState state = mc.world.getBlockState(pos.set(x, y, z));
        return state.isAir() || state.isOf(Blocks.SPAWNER) || state.isOf(Blocks.CHEST);
    }

    @EventHandler
    public void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null || kinds.isEmpty()) return;

        boolean markNatural = naturalMode.get() == NaturalMode.Mark;
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        double reach = range.get();
        double reachSq = reach * reach;

        List<long[]> near = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : kinds.entrySet()) {
            int kind = entry.getValue();
            if (kind == PENDING) continue;
            if (kind != PLAYER && !markNatural) continue;

            long packed = entry.getKey();
            double dx = BlockPos.unpackLongX(packed) + 0.5 - px;
            double dz = BlockPos.unpackLongZ(packed) + 0.5 - pz;
            double d = dx * dx + dz * dz;
            if (d <= reachSq) near.add(new long[]{packed, Double.doubleToLongBits(d), kind});
        }
        if (near.isEmpty()) return;

        near.sort(Comparator.comparingDouble(a -> Double.longBitsToDouble(a[1])));
        int limit = Math.min(near.size(), maxDrawn.get());

        double half = widthPixels.get() / 32.0;
        double y1 = Math.min(minY.get(), maxY.get());
        double y2 = Math.max(minY.get(), maxY.get());

        for (int i = 0; i < limit; i++) {
            long packed = near.get(i)[0];
            SettingColor line = near.get(i)[2] == PLAYER ? color.get() : naturalColor.get();
            SettingColor fill = new SettingColor(line.r, line.g, line.b, Math.max(40, line.a / 2));

            double x = BlockPos.unpackLongX(packed);
            double y = BlockPos.unpackLongY(packed);
            double z = BlockPos.unpackLongZ(packed);

            Box box = new Box(x + 0.5 - half, y1, z + 0.5 - half, x + 0.5 + half, y2, z + 0.5 + half);
            Render3DUtils.drawBox(box, fill, line, ShapeMode.Both);

            if (blockOutline.get()) {
                Render3DUtils.drawBox(new Box(x, y, z, x + 1, y + 1, z + 1), fill, line, ShapeMode.Lines);
            }
        }
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

    private void loadFlags() {
        if (saveRoot == null || activeKey == null) return;
        try {
            JsonElement entry = saveRoot.get(activeKey);
            if (entry == null) return;
            if (entry.isJsonObject()) {
                JsonObject object = entry.getAsJsonObject();
                if (object.has("player") && object.get("player").isJsonArray()) {
                    for (JsonElement element : object.getAsJsonArray("player")) addSaved(element, PLAYER);
                }
                if (object.has("natural") && object.get("natural").isJsonArray()) {
                    for (JsonElement element : object.getAsJsonArray("natural")) {
                        try {
                            JsonArray pair = element.getAsJsonArray();
                            kinds.put(pair.get(0).getAsLong(), pair.get(1).getAsInt());
                        } catch (RuntimeException ignored) {}
                    }
                }
            }
        } catch (RuntimeException ignored) {}
    }

    private void addSaved(JsonElement element, int kind) {
        try {
            long packed = element.getAsLong();
            kinds.put(packed, kind);
            if (kind == PENDING) checks.put(packed, 0);
        } catch (RuntimeException ignored) {}
    }

    private void save(boolean async) {
        if (activeKey == null) return;
        JsonArray player = new JsonArray();
        JsonArray natural = new JsonArray();

        for (Map.Entry<Long, Integer> entry : kinds.entrySet()) {
            int kind = entry.getValue();
            if (kind == PLAYER || kind == PENDING) player.add(entry.getKey());
            else {
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
            } catch (IOException ignored) {}
        };

        if (async) CompletableFuture.runAsync(write);
        else write.run();
    }
}
