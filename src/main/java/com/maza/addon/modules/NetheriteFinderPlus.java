package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
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
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.DimensionTypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NetheriteFinder+
 *
 * Passive finder, nothing is ever sent to the server and nothing is written to chat.
 *
 *  1. Hidden sections. The chunk packet is parsed and every 16x16x16 section whose block
 *     palette lists ancient debris while no block uses that entry is remembered. The
 *     cube itself stays see-through (optional thin outline only).
 *  2. Enclosed netherrack. Each remembered section is scanned from the chunk data the
 *     client already has. Air and fluids (lava) count as open space and are discarded.
 *     Only netherrack that has solid blocks on all six sides, repeated for N layers
 *     (default 2), is drawn, block by block, in one dark blue. That is where hidden
 *     debris can sit, so it is the only rock worth looking at inside the cube.
 *  3. Inferred netherite. Netherrack with six netherrack neighbours that are closed on all
 *     sides (no air or lava around) is drawn in yellow.
 *  4. Revealed debris. Ancient debris the client actually knows about is drawn in red
 *     with a tracer.
 *  5. Item ESP. Ancient debris, scrap and ingots lying on the ground get a box, in any
 *     dimension, so drops are easy to find after mining.
 *
 * Palette-scan idea follows codexNetheritechunk (MIT). Targets the 1.21.5+ chunk
 * format (no length prefix on paletted-container data arrays).
 */
public class NetheriteFinderPlus extends Module {
    private static final int MAX_CANDIDATES = 4096;
    private static final int MAX_STORED_DEBRIS = 4096;
    private static final int MAX_CACHED_SECTIONS = 96;
    private static final int COMPUTE_PER_TICK = 2;
    private static final int REBUILD_INTERVAL = 4;
    private static final Identifier DONUT_NETHER = Identifier.of("worlds", "smp_nether");
    private static final int ANCIENT_STATE_ID = Block.getRawIdFromState(Blocks.ANCIENT_DEBRIS.getDefaultState());

    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup sectionGroup = settings.createGroup("Hidden Sections");
    private final SettingGroup blockGroup = settings.createGroup("Enclosed Blocks");
    private final SettingGroup itemGroup = settings.createGroup("Item ESP");

    // ---- general
    private final Setting<Boolean> showDebris = general.add(new BoolSetting.Builder()
        .name("show-debris")
        .description("Draw ancient debris blocks the client knows about.")
        .defaultValue(true).build());

    private final Setting<Integer> debrisRange = general.add(new IntSetting.Builder()
        .name("debris-range")
        .description("Max distance in blocks for drawn debris.")
        .defaultValue(96).min(8).sliderMax(256).build());

    private final Setting<Boolean> debrisTracers = general.add(new BoolSetting.Builder()
        .name("debris-tracers")
        .description("Line from you to every revealed ancient debris.")
        .defaultValue(true).build());

    private final Setting<SettingColor> debrisSide = general.add(new ColorSetting.Builder()
        .name("debris-side-color")
        .defaultValue(new SettingColor(255, 0, 0, 70)).build());

    private final Setting<SettingColor> debrisLine = general.add(new ColorSetting.Builder()
        .name("debris-line-color")
        .defaultValue(new SettingColor(255, 0, 0, 240)).build());

    // ---- hidden sections
    private final Setting<Boolean> outline = sectionGroup.add(new BoolSetting.Builder()
        .name("outline")
        .description("Thin outline of hidden sections. The inside is never filled.")
        .defaultValue(true).build());

    private final Setting<Integer> sectionRange = sectionGroup.add(new IntSetting.Builder()
        .name("range-chunks")
        .description("Max distance in chunks for the outline.")
        .defaultValue(12).min(1).sliderMax(48).build());

    private final Setting<Integer> maxSections = sectionGroup.add(new IntSetting.Builder()
        .name("max-outlines")
        .description("Max outlines drawn at once (nearest first).")
        .defaultValue(48).min(1).sliderMax(256).build());

    private final Setting<Integer> outlineThickness = sectionGroup.add(new IntSetting.Builder()
        .name("outline-thickness")
        .description("Lines are one pixel wide, so this draws that many nested outlines to look thicker.")
        .defaultValue(4).min(1).max(8).sliderMin(1).sliderMax(8).build());

    private final Setting<SettingColor> outlineColor = sectionGroup.add(new ColorSetting.Builder()
        .name("outline-color")
        .defaultValue(new SettingColor(30, 80, 255, 235)).build());

    // ---- enclosed blocks
    private final Setting<Boolean> enclosed = blockGroup.add(new BoolSetting.Builder()
        .name("enclosed-netherrack")
        .description("Inside hidden sections, draw only netherrack that is closed on all six sides.")
        .defaultValue(true).build());

    private final Setting<Integer> layers = blockGroup.add(new IntSetting.Builder()
        .name("layers")
        .description("How many layers deep the rock must be closed. 1 = six neighbours solid, 2 = their neighbours too.")
        .defaultValue(2).min(1).max(3).sliderMin(1).sliderMax(3).build());

    private final Setting<Integer> blockRange = blockGroup.add(new IntSetting.Builder()
        .name("block-range")
        .description("Only blocks this close to you are drawn.")
        .defaultValue(24).min(4).sliderMax(64).build());

    private final Setting<Integer> maxBlocks = blockGroup.add(new IntSetting.Builder()
        .name("max-blocks")
        .description("Max blocks drawn at once (nearest first).")
        .defaultValue(400).min(1).sliderMax(2000).build());

    private final Setting<ShapeMode> shapeMode = blockGroup.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> lineColor = blockGroup.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Edge colour of enclosed netherrack. The inside is never filled.")
        .defaultValue(new SettingColor(25, 70, 235, 230)).build());

    private final Setting<Boolean> inferred = blockGroup.add(new BoolSetting.Builder()
        .name("inferred-netherite")
        .description("Netherrack whose six neighbours are netherrack and closed on every side. Any air or lava around it cancels it. Drawn in yellow.")
        .defaultValue(true).build());

    private final Setting<Integer> maxInferred = blockGroup.add(new IntSetting.Builder()
        .name("max-inferred")
        .description("Max yellow blocks drawn at once (nearest first).")
        .defaultValue(200).min(1).sliderMax(1000).build());

    private final Setting<SettingColor> inferredSide = blockGroup.add(new ColorSetting.Builder()
        .name("inferred-side-color")
        .defaultValue(new SettingColor(255, 210, 0, 70)).build());

    private final Setting<SettingColor> inferredLine = blockGroup.add(new ColorSetting.Builder()
        .name("inferred-line-color")
        .defaultValue(new SettingColor(255, 225, 0, 235)).build());

    // ---- item esp
    private final Setting<Boolean> itemEsp = itemGroup.add(new BoolSetting.Builder()
        .name("item-esp")
        .description("Box around netherite items lying on the ground, after something dropped them. Works in every dimension.")
        .defaultValue(true).build());

    private final Setting<Boolean> itemDebris = itemGroup.add(new BoolSetting.Builder()
        .name("ancient-debris").defaultValue(true).build());

    private final Setting<Boolean> itemScrap = itemGroup.add(new BoolSetting.Builder()
        .name("netherite-scrap").defaultValue(true).build());

    private final Setting<Boolean> itemIngot = itemGroup.add(new BoolSetting.Builder()
        .name("netherite-ingot").defaultValue(true).build());

    private final Setting<Boolean> itemBlock = itemGroup.add(new BoolSetting.Builder()
        .name("netherite-block").defaultValue(false).build());

    private final Setting<Integer> itemRange = itemGroup.add(new IntSetting.Builder()
        .name("range")
        .description("Max distance in blocks.")
        .defaultValue(128).min(8).sliderMax(256).build());

    private final Setting<SettingColor> itemSide = itemGroup.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(15, 45, 190, 70)).build());

    private final Setting<SettingColor> itemLine = itemGroup.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(30, 80, 240, 235)).build());

    private record Section(int cx, int sy, int cz) {}
    private record Dist(long packed, double distSq) {}
    private record Computed(long[] enclosed, long[] yellow) {}

    // Only touched on the client thread.
    private final Set<Section> candidates = new LinkedHashSet<>();
    private final Set<Long> debris = new LinkedHashSet<>();
    private final Map<Section, Computed> cache = new HashMap<>();  // scan result per section
    private long[] visible = new long[0];        // enclosed netherrack, blue edges
    private long[] visibleYellow = new long[0];  // inferred netherite, yellow
    private ClientWorld lastWorld;
    private int lastLayers = -1;
    private int tick;
    private boolean rebuild;

    public NetheriteFinderPlus() {
        super(MazaCategory.INSTANCE, "netherite-finder-plus",
            "Draws deeply enclosed netherrack inside Nether sections that hide ancient debris.");
    }

    @Override
    public void onActivate() {
        clearAll();
        scanLoadedChunks();
    }

    @Override
    public void onDeactivate() {
        clearAll();
    }

    private void clearAll() {
        candidates.clear();
        debris.clear();
        cache.clear();
        visible = new long[0];
        visibleYellow = new long[0];
        lastWorld = null;
        lastLayers = -1;
        rebuild = true;
    }

    // ---------------------------------------------------------------- packets

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        Object packet = event.packet;

        // PacketEvent.Receive runs on the networking thread. Copy the section buffer right
        // away (the original may be released after the callback) and hand only the copy on.
        if (packet instanceof ChunkDataS2CPacket chunkPacket) {
            PacketByteBuf original = chunkPacket.getChunkData().getSectionsDataBuf();
            PacketByteBuf copy = new PacketByteBuf(original.copy());
            int cx = chunkPacket.getChunkX();
            int cz = chunkPacket.getChunkZ();
            mc.execute(() -> handleChunkData(cx, cz, copy));
        } else if (packet instanceof BlockUpdateS2CPacket update) {
            BlockPos pos = update.getPos();
            BlockState state = update.getState();
            mc.execute(() -> handleBlock(pos, state));
        } else if (packet instanceof ChunkDeltaUpdateS2CPacket delta) {
            mc.execute(() -> delta.visitUpdates(this::handleBlock));
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        // Fires on the client thread once the chunk is really in the world.
        if (mc.world == null || event.chunk() == null || !isNether(mc.world)) return;
        trackWorld(mc.world);

        ChunkPos cp = event.chunk().getPos();

        // A new chunk changes what the neighbouring sections can see across the border.
        cache.keySet().removeIf(s -> Math.abs(s.cx() - cp.x) <= 1 && Math.abs(s.cz() - cp.z) <= 1);
        rebuild = true;

        if (showDebris.get()) collectDebris(event.chunk());
    }

    private void handleChunkData(int cx, int cz, PacketByteBuf buf) {
        ClientWorld world = mc.world;
        if (!isActive() || world == null || !isNether(world)) {
            buf.release();
            return;
        }
        trackWorld(world);

        List<Integer> hidden;
        try {
            hidden = scanSections(buf, world.countVerticalSections(), world.getBottomSectionCoord(), ANCIENT_STATE_ID);
        } catch (RuntimeException ignored) {
            return;
        } finally {
            buf.release();
        }

        // A fresh chunk replaces the old analysis for that chunk.
        candidates.removeIf(s -> s.cx() == cx && s.cz() == cz);
        cache.keySet().removeIf(s -> s.cx() == cx && s.cz() == cz);

        // A hidden-palette candidate has no placed debris block, so it must not be
        // confirmed with ChunkSection.hasAny(ANCIENT_DEBRIS).
        for (int sy : hidden) addCandidate(new Section(cx, sy, cz));
        rebuild = true;
    }

    private void handleBlock(BlockPos pos, BlockState state) {
        if (!isActive() || mc.world == null || !isNether(mc.world)) return;

        invalidateAround(pos);

        long packed = pos.asLong();
        if (!state.isOf(Blocks.ANCIENT_DEBRIS)) {
            debris.remove(packed); // mined or replaced
            return;
        }

        // Real debris became visible: this section is no longer purely "hidden".
        Section section = new Section(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        candidates.remove(section);
        cache.remove(section);

        if (showDebris.get() && debris.add(packed)) trimDebris();
    }

    /** A changed block can open or close rock in this section and in the ones touching it. */
    private void invalidateAround(BlockPos pos) {
        int cx = pos.getX() >> 4;
        int cy = pos.getY() >> 4;
        int cz = pos.getZ() >> 4;
        if (cache.keySet().removeIf(s -> Math.abs(s.cx() - cx) <= 1
            && Math.abs(s.sy() - cy) <= 1 && Math.abs(s.cz() - cz) <= 1)) {
            rebuild = true;
        }
    }

    private void addCandidate(Section section) {
        if (!candidates.add(section)) return;

        while (candidates.size() > MAX_CANDIDATES) {
            Iterator<Section> it = candidates.iterator();
            Section old = it.next();
            it.remove();
            cache.remove(old);
        }
    }

    // ------------------------------------------------------------ debris blocks

    private void scanLoadedChunks() {
        if (mc.world == null || mc.player == null || !showDebris.get() || !isNether(mc.world)) return;

        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;
        int range = Math.max(2, mc.options.getViewDistance().getValue());

        for (int x = pcx - range; x <= pcx + range; x++) {
            for (int z = pcz - range; z <= pcz + range; z++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x, z);
                if (chunk != null) collectDebris(chunk);
            }
        }
    }

    /** Store every ancient debris block the client has for this chunk. */
    private void collectDebris(WorldChunk chunk) {
        ChunkPos cp = chunk.getPos();
        debris.removeIf(packed -> (BlockPos.unpackLongX(packed) >> 4) == cp.x
            && (BlockPos.unpackLongZ(packed) >> 4) == cp.z);

        ChunkSection[] sections = chunk.getSectionArray();
        if (sections == null) return;
        int bottom = chunk.getBottomY();

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            if (!section.hasAny(state -> state.isOf(Blocks.ANCIENT_DEBRIS))) continue;

            int sectionBottom = bottom + i * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!section.getBlockState(x, y, z).isOf(Blocks.ANCIENT_DEBRIS)) continue;
                        debris.add(BlockPos.asLong(cp.getStartX() + x, sectionBottom + y, cp.getStartZ() + z));
                    }
                }
            }
        }
        trimDebris();
    }

    private void trimDebris() {
        while (debris.size() > MAX_STORED_DEBRIS) {
            Iterator<Long> it = debris.iterator();
            it.next();
            it.remove();
        }
    }

    // ------------------------------------------------------------------- tick

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        trackWorld(mc.world);
        tick++;

        int signature = layers.get() * 4 + (enclosed.get() ? 2 : 0) + (inferred.get() ? 1 : 0);
        if (lastLayers != signature) {
            lastLayers = signature;
            cache.clear();
            rebuild = true;
        }

        if (!enclosed.get() && !inferred.get()) {
            visible = new long[0];
            visibleYellow = new long[0];
            return;
        }

        if (isNether(mc.world)) computeNearby();
        if (rebuild || tick % REBUILD_INTERVAL == 0) {
            rebuild = false;
            rebuildVisible();
        }
    }

    private void trackWorld(ClientWorld world) {
        if (lastWorld != world) {
            candidates.clear();
            debris.clear();
            cache.clear();
            visible = new long[0];
            visibleYellow = new long[0];
            lastWorld = world;
            rebuild = true;
        }
    }

    /** Scan the nearest hidden sections that are not cached yet, a couple per tick. */
    private void computeNearby() {
        if (candidates.isEmpty()) return;

        Vec3d eye = mc.player.getEyePos();
        double reach = blockRange.get();
        double reachSq = reach * reach;

        List<Section> need = null;
        for (Section s : candidates) {
            if (cache.containsKey(s)) continue;
            if (boxDistSq(s, eye) > reachSq) continue;
            if (need == null) need = new ArrayList<>();
            need.add(s);
        }
        if (need == null) return;

        need.sort(Comparator.comparingDouble(s -> boxDistSq(s, eye)));

        int budget = COMPUTE_PER_TICK;
        for (Section s : need) {
            if (budget-- <= 0) break;

            Computed result = computeSection(s);
            if (result == null) continue; // own chunk not loaded yet, try again later

            while (cache.size() >= MAX_CACHED_SECTIONS) {
                Iterator<Section> it = cache.keySet().iterator();
                it.next();
                it.remove();
            }
            cache.put(s, result);
            rebuild = true;
        }
    }

    /**
     * Scan one section from the chunk data the client already has.
     *
     * A cell is solid when it is neither air nor a fluid (lava counts as open space) and
     * its chunk is loaded. Unknown space is treated as open, so rock next to an unloaded
     * chunk is never claimed to be closed.
     *
     *  enclosed: netherrack that is closed on six sides, repeated N times (N erosion
     *            passes with the six-neighbour kernel).
     *  yellow:   netherrack whose six neighbours are all netherrack and whose neighbours
     *            are in turn closed on all of their sides. One bit of air or lava anywhere
     *            in that 2-block diamond cancels it.
     *
     * The grid carries a margin of max(N, 2) blocks so both checks stay inside it.
     */
    private Computed computeSection(Section section) {
        ClientWorld world = mc.world;
        int n = layers.get();
        int m = Math.max(n, 2);
        int size = 16 + 2 * m;

        WorldChunk[][] chunks = new WorldChunk[3][3];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                chunks[dx + 1][dz + 1] = world.getChunkManager().getWorldChunk(section.cx() + dx, section.cz() + dz);
            }
        }
        if (chunks[1][1] == null) return null;

        int baseX = section.cx() * 16 - m;
        int baseY = section.sy() * 16 - m;
        int baseZ = section.cz() * 16 - m;

        boolean[] solid = new boolean[size * size * size];
        boolean[] rack = new boolean[size * size * size];
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                int wz = baseZ + z;
                for (int x = 0; x < size; x++) {
                    int wx = baseX + x;
                    WorldChunk chunk = chunks[(wx >> 4) - section.cx() + 1][(wz >> 4) - section.cz() + 1];
                    if (chunk == null) continue;

                    pos.set(wx, baseY + y, wz);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) continue;

                    int index = (y * size + z) * size + x;
                    solid[index] = true;
                    rack[index] = state.isOf(Blocks.NETHERRACK);
                }
            }
        }

        int layer = size * size;
        long[] enclosedOut = new long[0];

        if (enclosed.get()) {
            boolean[] current = solid;
            for (int step = 1; step <= n; step++) {
                boolean[] next = new boolean[current.length];
                for (int y = step; y < size - step; y++) {
                    for (int z = step; z < size - step; z++) {
                        for (int x = step; x < size - step; x++) {
                            int i = (y * size + z) * size + x;
                            next[i] = current[i]
                                && current[i - 1] && current[i + 1]
                                && current[i - size] && current[i + size]
                                && current[i - layer] && current[i + layer];
                        }
                    }
                }
                current = next;
            }

            List<Long> out = new ArrayList<>();
            for (int y = m; y < m + 16; y++) {
                for (int z = m; z < m + 16; z++) {
                    for (int x = m; x < m + 16; x++) {
                        int i = (y * size + z) * size + x;
                        if (current[i] && rack[i]) out.add(BlockPos.asLong(baseX + x, baseY + y, baseZ + z));
                    }
                }
            }
            enclosedOut = toArray(out);
        }

        long[] yellowOut = new long[0];

        if (inferred.get()) {
            int[] around = {-1, 1, -size, size, -layer, layer};
            List<Long> out = new ArrayList<>();

            for (int y = m; y < m + 16; y++) {
                for (int z = m; z < m + 16; z++) {
                    for (int x = m; x < m + 16; x++) {
                        int i = (y * size + z) * size + x;
                        if (!rack[i] || !closedAround(i, around, rack, solid)) continue;
                        out.add(BlockPos.asLong(baseX + x, baseY + y, baseZ + z));
                    }
                }
            }
            yellowOut = toArray(out);
        }

        return new Computed(enclosedOut, yellowOut);
    }

    /** Six netherrack neighbours, and every neighbour of those neighbours solid (no air, no lava). */
    private static boolean closedAround(int center, int[] around, boolean[] rack, boolean[] solid) {
        for (int a : around) {
            int neighbour = center + a;
            if (!rack[neighbour]) return false;

            for (int b : around) {
                if (!solid[neighbour + b]) return false;
            }
        }
        return true;
    }

    private static long[] toArray(List<Long> list) {
        long[] result = new long[list.size()];
        for (int i = 0; i < result.length; i++) result[i] = list.get(i);
        return result;
    }

    /** Gather the cached blocks near the player, nearest first, capped per colour. */
    private void rebuildVisible() {
        Vec3d eye = mc.player.getEyePos();
        double reach = blockRange.get();
        double reachSq = reach * reach;

        List<Dist> blue = new ArrayList<>();
        List<Dist> yellow = new ArrayList<>();

        for (Map.Entry<Section, Computed> e : cache.entrySet()) {
            if (!candidates.contains(e.getKey())) continue;
            if (boxDistSq(e.getKey(), eye) > reachSq) continue;

            collectNear(e.getValue().enclosed(), eye, reachSq, blue);
            collectNear(e.getValue().yellow(), eye, reachSq, yellow);
        }

        visible = nearest(blue, maxBlocks.get());
        visibleYellow = nearest(yellow, maxInferred.get());
    }

    private static void collectNear(long[] blocks, Vec3d eye, double reachSq, List<Dist> out) {
        for (long packed : blocks) {
            double dx = BlockPos.unpackLongX(packed) + 0.5 - eye.x;
            double dy = BlockPos.unpackLongY(packed) + 0.5 - eye.y;
            double dz = BlockPos.unpackLongZ(packed) + 0.5 - eye.z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= reachSq) out.add(new Dist(packed, d));
        }
    }

    private static long[] nearest(List<Dist> list, int max) {
        list.sort(Comparator.comparingDouble(Dist::distSq));
        int limit = Math.min(list.size(), max);

        long[] result = new long[limit];
        for (int i = 0; i < limit; i++) result[i] = list.get(i).packed();
        return result;
    }

    // ----------------------------------------------------------------- render

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        // Dropped items are worth showing in any dimension, the rest is Nether only.
        if (itemEsp.get()) renderItems(event);
        if (!isNether(mc.world)) return;

        if (outline.get() && !candidates.isEmpty()) renderOutlines(event);

        // Enclosed netherrack: blue edges only, the inside stays see-through.
        if (enclosed.get() && visible.length > 0) {
            Color none = new Color(0, 0, 0, 0);
            Color line = lineColor.get();
            double e = 0.002;

            for (long packed : visible) {
                double x = BlockPos.unpackLongX(packed);
                double y = BlockPos.unpackLongY(packed);
                double z = BlockPos.unpackLongZ(packed);
                event.renderer.box(x - e, y - e, z - e, x + 1 + e, y + 1 + e, z + 1 + e, none, line, ShapeMode.Lines, 0);
            }
        }

        // Inferred netherite: yellow, drawn over the blue edges.
        if (inferred.get() && visibleYellow.length > 0) {
            Color side = inferredSide.get();
            Color line = inferredLine.get();
            double e = 0.004;

            for (long packed : visibleYellow) {
                double x = BlockPos.unpackLongX(packed);
                double y = BlockPos.unpackLongY(packed);
                double z = BlockPos.unpackLongZ(packed);
                event.renderer.box(x - e, y - e, z - e, x + 1 + e, y + 1 + e, z + 1 + e, side, line, ShapeMode.Both, 0);
            }
        }

        if (showDebris.get() && !debris.isEmpty()) renderDebris(event);
    }

    /** Box around every wanted item entity lying in the world. */
    private void renderItems(Render3DEvent event) {
        Vec3d eye = mc.player.getEyePos();
        double range = itemRange.get();
        double rangeSq = range * range;

        Color side = itemSide.get();
        Color line = itemLine.get();
        ShapeMode mode = shapeMode.get();
        double grow = 0.08; // dropped items are tiny, make them easy to spot

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity item)) continue;
            if (!wantsItem(item.getStack())) continue;

            double dx = item.getX() - eye.x;
            double dy = item.getY() - eye.y;
            double dz = item.getZ() - eye.z;
            if (dx * dx + dy * dy + dz * dz > rangeSq) continue;

            Box b = item.getBoundingBox();
            event.renderer.box(
                b.minX - grow, b.minY - grow, b.minZ - grow,
                b.maxX + grow, b.maxY + grow, b.maxZ + grow,
                side, line, mode, 0
            );
        }
    }

    private boolean wantsItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return (itemDebris.get() && stack.isOf(Items.ANCIENT_DEBRIS))
            || (itemScrap.get() && stack.isOf(Items.NETHERITE_SCRAP))
            || (itemIngot.get() && stack.isOf(Items.NETHERITE_INGOT))
            || (itemBlock.get() && stack.isOf(Items.NETHERITE_BLOCK));
    }

    /** Hidden section outline. Lines are 1px, so nested outlines make it thick. */
    private void renderOutlines(Render3DEvent event) {
        Vec3d eye = mc.player.getEyePos();
        double maxDistance = sectionRange.get() * 16.0;
        double maxSq = maxDistance * maxDistance;

        List<Section> near = new ArrayList<>();
        for (Section s : candidates) {
            if (boxDistSq(s, eye) <= maxSq) near.add(s);
        }
        if (near.isEmpty()) return;

        near.sort(Comparator.comparingDouble(s -> boxDistSq(s, eye)));
        int limit = Math.min(near.size(), maxSections.get());

        Color line = outlineColor.get();
        Color none = new Color(0, 0, 0, 0);
        int passes = outlineThickness.get();
        double step = 0.05;

        for (int i = 0; i < limit; i++) {
            Section s = near.get(i);
            double x = s.cx() * 16.0;
            double y = s.sy() * 16.0;
            double z = s.cz() * 16.0;

            for (int k = 0; k < passes; k++) {
                double g = (k - (passes - 1) / 2.0) * step;
                event.renderer.box(x - g, y - g, z - g, x + 16.0 + g, y + 16.0 + g, z + 16.0 + g,
                    none, line, ShapeMode.Lines, 0);
            }
        }
    }

    /** Revealed ancient debris: red box and a line from you to it. */
    private void renderDebris(Render3DEvent event) {
        Vec3d eye = mc.player.getEyePos();
        double range = debrisRange.get();
        double rangeSq = range * range;
        double e = 0.002;

        Color side = debrisSide.get();
        Color line = debrisLine.get();
        boolean tracers = debrisTracers.get();
        double[] start = tracers ? tracerStart() : null;
        List<Long> gone = null;

        for (long packed : debris) {
            int x = BlockPos.unpackLongX(packed);
            int y = BlockPos.unpackLongY(packed);
            int z = BlockPos.unpackLongZ(packed);

            double dx = x + 0.5 - eye.x;
            double dy = y + 0.5 - eye.y;
            double dz = z + 0.5 - eye.z;
            if (dx * dx + dy * dy + dz * dz > rangeSq) continue;

            // Self-cleaning: if the block is no longer debris, forget it.
            if (!mc.world.getBlockState(BlockPos.fromLong(packed)).isOf(Blocks.ANCIENT_DEBRIS)) {
                if (gone == null) gone = new ArrayList<>();
                gone.add(packed);
                continue;
            }

            event.renderer.box(x - e, y - e, z - e, x + 1 + e, y + 1 + e, z + 1 + e, side, line, ShapeMode.Both, 0);
            if (tracers) event.renderer.line(start[0], start[1], start[2], x + 0.5, y + 0.5, z + 0.5, line);
        }

        if (gone != null) debris.removeAll(gone);
    }

    /** Tracer origin a little in front of the camera, from yaw and pitch. */
    private double[] tracerStart() {
        double yaw = Math.toRadians(mc.player.getYaw());
        double pitch = Math.toRadians(mc.player.getPitch());
        double cosPitch = Math.cos(pitch);
        return new double[]{
            mc.player.getX() - Math.sin(yaw) * cosPitch * 0.6,
            mc.player.getEyeY() - Math.sin(pitch) * 0.6,
            mc.player.getZ() + Math.cos(yaw) * cosPitch * 0.6
        };
    }

    /** Squared distance from a point to the nearest point of the section's box. */
    private static double boxDistSq(Section s, Vec3d p) {
        double minX = s.cx() * 16.0;
        double minY = s.sy() * 16.0;
        double minZ = s.cz() * 16.0;
        double dx = Math.max(0.0, Math.max(minX - p.x, p.x - (minX + 16.0)));
        double dy = Math.max(0.0, Math.max(minY - p.y, p.y - (minY + 16.0)));
        double dz = Math.max(0.0, Math.max(minZ - p.z, p.z - (minZ + 16.0)));
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean isNether(ClientWorld world) {
        if (world.getRegistryKey() == World.NETHER) return true;
        if (world.getRegistryKey().getValue().equals(DONUT_NETHER)) return true;
        return world.getDimensionEntry().matchesKey(DimensionTypes.THE_NETHER);
    }

    // ---------------------------------------------------------- palette scan

    /** Returns the section Y coords whose block palette holds {@code target} but no block uses it. */
    private static List<Integer> scanSections(
        PacketByteBuf buf, int sectionCount, int bottomSection, int target
    ) {
        if (sectionCount < 0 || target < 0) {
            throw new IllegalArgumentException("invalid section scan arguments");
        }

        List<Integer> hidden = new ArrayList<>();

        for (int i = 0; i < sectionCount; i++) {
            int nonEmptyBlocks = buf.readUnsignedShort();

            PaletteRead blocks = readPalette(buf, 4096, 8, 4, target);
            // Biome palette: consume it exactly like the block palette.
            readPalette(buf, 64, 3, 1, -1);

            if (nonEmptyBlocks > 0 && blocks.hiddenTarget()) {
                hidden.add(bottomSection + i);
            }
        }

        // A chunk packet that wasn't consumed completely is not trusted.
        if (buf.readableBytes() != 0) {
            return List.of();
        }

        return hidden;
    }

    private record PaletteRead(boolean paletteHasTarget, boolean targetUnused) {
        boolean hiddenTarget() {
            return paletteHasTarget && targetUnused;
        }
    }

    private static PaletteRead readPalette(
        PacketByteBuf buf, int valueCount, int maxPaletteBits, int minBits, int target
    ) {
        int bits = buf.readUnsignedByte();

        int storageBits;
        int[] palette;

        if (bits == 0) {
            storageBits = 0;
            palette = new int[]{buf.readVarInt()};
        } else if (bits <= maxPaletteBits) {
            storageBits = Math.max(minBits, bits);

            int size = buf.readVarInt();
            if (size <= 0 || size > (1 << storageBits)) {
                throw new IllegalArgumentException("invalid local palette size");
            }

            palette = new int[size];
            for (int i = 0; i < size; i++) {
                palette[i] = buf.readVarInt();
            }
        } else {
            // Direct palette: there is no local palette array, packed values are global state IDs.
            storageBits = bits;
            if (storageBits > 30) {
                throw new IllegalArgumentException("direct palette is too wide");
            }
            palette = null;
        }

        int valuesPerLong = storageBits == 0 ? 0 : 64 / storageBits;
        if (storageBits > 0 && valuesPerLong == 0) {
            throw new IllegalArgumentException("invalid palette storage width");
        }

        int longs = storageBits == 0
            ? 0
            : (valueCount + valuesPerLong - 1) / valuesPerLong;

        long bytes = (long) longs * 8L;
        if (bytes > buf.readableBytes()) {
            throw new IndexOutOfBoundsException("truncated palette data");
        }

        boolean hasTarget = palette != null && contains(palette, target);

        // No local target means this section cannot be a hidden-target section.
        // The packed data is still consumed.
        if (!hasTarget && palette != null) {
            buf.skipBytes((int) bytes);
            return new PaletteRead(false, false);
        }

        int used = 0;

        if (storageBits == 0) {
            if (palette != null && palette[0] == target) {
                used = valueCount;
            }
        } else {
            int start = buf.readerIndex();
            long mask = (1L << storageBits) - 1L;

            for (int i = 0; i < valueCount; i++) {
                int longIndex = i / valuesPerLong;
                int valueIndex = i % valuesPerLong;

                long word = buf.getLong(start + longIndex * 8);
                int raw = (int) ((word >>> (valueIndex * storageBits)) & mask);

                // Indirect palette: palette index -> global state ID. Direct: raw is the ID.
                int stateId;
                if (palette == null) {
                    stateId = raw;
                } else {
                    if (raw < 0 || raw >= palette.length) {
                        throw new IllegalArgumentException("packed palette index is invalid");
                    }
                    stateId = palette[raw];
                }

                if (stateId == target) {
                    used++;
                }
            }
        }

        buf.skipBytes((int) bytes);

        // A direct palette never reports a hidden target: the packed scan above is the
        // only information, and any use of the target means it is not hidden.
        if (palette == null) {
            return new PaletteRead(used > 0, used == 0);
        }

        return new PaletteRead(hasTarget, used == 0);
    }

    private static boolean contains(int[] values, int target) {
        for (int v : values) if (v == target) return true;
        return false;
    }
}
