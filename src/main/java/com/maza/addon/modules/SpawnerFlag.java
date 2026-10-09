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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Spawner Flag
 *
 * Every spawner the client sees is remembered, per server and dimension, and drawn as a
 * thin red column from the bottom of the world to the build limit, so it can be seen from
 * far away. The list is saved to disk: after a relog the flags are there right away.
 *
 * A flag is removed when the spawner is seen to be gone (a block update replaces it, or its
 * chunk loads and the block reads as air). A chunk that merely does not show the spawner,
 * for example because the server hides it, never deletes a flag.
 */
public class SpawnerFlag extends Module {
    private static final int SAVE_DELAY_TICKS = 100;   // 5 s after the last change
    private static final Path SAVE_FILE = FabricLoader.getInstance().getGameDir()
        .resolve("meteor-client").resolve("maza-spawner-flags.json");

    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup rendering = settings.createGroup("Render");

    private final Setting<Boolean> notify = general.add(new BoolSetting.Builder()
        .name("notify")
        .description("Chat message when a new spawner is found.")
        .defaultValue(false).build());

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
    private final Set<Long> flags = new LinkedHashSet<>();
    private JsonObject saveRoot;
    private String activeKey;
    private ClientWorld lastWorld;
    private boolean dirty;
    private int tick;
    private int dirtySince;

    public SpawnerFlag() {
        super(MazaCategory.INSTANCE, "spawner-flag",
            "Remembers every spawner you see and marks it with a tall red line, also after a relog.");
    }

    @Override
    public void onActivate() {
        flags.clear();
        lastWorld = null;
        activeKey = null;
        dirty = false;
        saveRoot = loadSaveFile();
    }

    @Override
    public void onDeactivate() {
        save(false);
        flags.clear();
        lastWorld = null;
        activeKey = null;
    }

    @Override
    public String getInfoString() {
        return flags.isEmpty() ? null : String.valueOf(flags.size());
    }

    // ----------------------------------------------------------------- events

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) {
            if (activeKey != null) {
                save(false);
                flags.clear();
                activeKey = null;
            }
            lastWorld = null;
            return;
        }

        if (mc.world != lastWorld) {
            if (activeKey != null) save(false); // still the old world's key and flags
            flags.clear();
            lastWorld = mc.world;
            activeKey = saveKey();
            loadFlags();
            scanLoadedChunks();
        }

        tick++;
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
            if (flags.add(packed)) {
                markDirty();
                if (notify.get()) info("Spawner at %d, %d, %d", event.pos.getX(), event.pos.getY(), event.pos.getZ());
            }
        } else if (flags.remove(packed)) {
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
        for (Iterator<Long> it = flags.iterator(); it.hasNext(); ) {
            long packed = it.next();
            if ((BlockPos.unpackLongX(packed) >> 4) != cp.x || (BlockPos.unpackLongZ(packed) >> 4) != cp.z) continue;
            if (found.contains(packed)) continue;

            BlockState state = chunk.getBlockState(BlockPos.fromLong(packed));
            if (state.isAir()) {
                it.remove();
                changed = true;
            }
        }

        for (long packed : found) {
            if (!flags.add(packed)) continue;
            changed = true;

            if (notify.get()) {
                info("Spawner at %d, %d, %d", BlockPos.unpackLongX(packed), BlockPos.unpackLongY(packed),
                    BlockPos.unpackLongZ(packed));
            }
        }

        if (changed) markDirty();
    }

    // ----------------------------------------------------------------- render

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null || flags.isEmpty()) return;

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        double reach = range.get();
        double reachSq = reach * reach;

        List<long[]> near = new ArrayList<>();
        for (long packed : flags) {
            double dx = BlockPos.unpackLongX(packed) + 0.5 - px;
            double dz = BlockPos.unpackLongZ(packed) + 0.5 - pz;
            double d = dx * dx + dz * dz;
            if (d <= reachSq) near.add(new long[]{packed, Double.doubleToLongBits(d)});
        }
        if (near.isEmpty()) return;

        near.sort(Comparator.comparingDouble(a -> Double.longBitsToDouble(a[1])));
        int limit = Math.min(near.size(), maxDrawn.get());

        SettingColor line = color.get();
        Color fill = new Color(line.r, line.g, line.b, Math.max(40, line.a / 2));
        Color none = new Color(0, 0, 0, 0);

        double half = widthPixels.get() / 32.0; // pixels / 16 is the width, half of it each side
        double y1 = Math.min(minY.get(), maxY.get());
        double y2 = Math.max(minY.get(), maxY.get());

        for (int i = 0; i < limit; i++) {
            long packed = near.get(i)[0];
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

    private void loadFlags() {
        if (saveRoot == null || activeKey == null) return;

        try {
            JsonElement entry = saveRoot.get(activeKey);
            if (entry == null || !entry.isJsonArray()) return;

            for (JsonElement element : entry.getAsJsonArray()) {
                try {
                    flags.add(element.getAsLong());
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

        JsonArray array = new JsonArray();
        for (long packed : flags) array.add(packed);

        if (saveRoot == null) saveRoot = new JsonObject();
        saveRoot.add(activeKey, array);
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
