package com.maza.client.module.modules.world;

import com.maza.client.event.EventHandler;
import com.maza.client.event.PacketEvent;
import com.maza.client.event.Render3DEvent;
import com.maza.client.event.TickEvent;
import com.maza.client.module.Category;
import com.maza.client.module.Module;
import com.maza.client.setting.*;
import com.maza.client.utils.Render3DUtils;
import com.maza.client.utils.ShapeMode;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.DimensionTypes;

import java.util.*;

/**
 * NetheriteFinderPlus - Standalone Fabric
 */
public class NetheriteFinderPlus extends Module {
    private static final int MAX_CANDIDATES = 4096;
    private static final int MAX_STORED_DEBRIS = 4096;
    private static final int MAX_CACHED_SECTIONS = 96;
    private static final int COMPUTE_PER_TICK = 2;
    private static final int REBUILD_INTERVAL = 4;
    private static final Identifier DONUT_NETHER = Identifier.of("worlds", "smp_nether");
    private static final int ANCIENT_STATE_ID = Block.getRawIdFromState(Blocks.ANCIENT_DEBRIS.getDefaultState());

    // Settings
    public final BoolSetting showDebris = addSetting(new BoolSetting.Builder()
        .name("show-debris").defaultValue(true).build());

    public final IntSetting debrisRange = addSetting(new IntSetting.Builder()
        .name("debris-range").defaultValue(96).min(8).sliderMax(256).build());

    public final BoolSetting debrisTracers = addSetting(new BoolSetting.Builder()
        .name("debris-tracers").defaultValue(true).build());

    public final ColorSetting debrisSide = addSetting(new ColorSetting.Builder()
        .name("debris-side-color").defaultValue(new SettingColor(255, 0, 0, 70)).build());

    public final ColorSetting debrisLine = addSetting(new ColorSetting.Builder()
        .name("debris-line-color").defaultValue(new SettingColor(255, 0, 0, 240)).build());

    public final BoolSetting outline = addSetting(new BoolSetting.Builder()
        .name("outline").defaultValue(true).build());

    public final IntSetting sectionRange = addSetting(new IntSetting.Builder()
        .name("range-chunks").defaultValue(12).min(1).sliderMax(48).build());

    public final IntSetting maxSections = addSetting(new IntSetting.Builder()
        .name("max-outlines").defaultValue(48).min(1).sliderMax(256).build());

    public final IntSetting outlineThickness = addSetting(new IntSetting.Builder()
        .name("outline-thickness").defaultValue(4).min(1).max(8).sliderMin(1).sliderMax(8).build());

    public final ColorSetting outlineColor = addSetting(new ColorSetting.Builder()
        .name("outline-color").defaultValue(new SettingColor(30, 80, 255, 235)).build());

    public final BoolSetting enclosed = addSetting(new BoolSetting.Builder()
        .name("enclosed-netherrack").defaultValue(true).build());

    public final IntSetting layers = addSetting(new IntSetting.Builder()
        .name("layers").defaultValue(2).min(1).max(3).sliderMin(1).sliderMax(3).build());

    public final IntSetting blockRange = addSetting(new IntSetting.Builder()
        .name("block-range").defaultValue(40).min(4).sliderMax(64).build());

    public final IntSetting maxBlocks = addSetting(new IntSetting.Builder()
        .name("max-blocks").defaultValue(600).min(1).sliderMax(2000).build());

    public final ColorSetting lineColor = addSetting(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(25, 70, 235, 230)).build());

    public final BoolSetting inferred = addSetting(new BoolSetting.Builder()
        .name("inferred-netherite").defaultValue(true).build());

    public final IntSetting maxInferred = addSetting(new IntSetting.Builder()
        .name("max-inferred").defaultValue(150).min(1).sliderMax(500).build());

    public final IntSetting inferredMinY = addSetting(new IntSetting.Builder()
        .name("inferred-min-y").defaultValue(8).min(0).max(127).sliderMin(0).sliderMax(127).build());

    public final IntSetting inferredMaxY = addSetting(new IntSetting.Builder()
        .name("inferred-max-y").defaultValue(119).min(0).max(127).sliderMin(0).sliderMax(127).build());

    public final IntSetting inferredSpacing = addSetting(new IntSetting.Builder()
        .name("inferred-spacing").defaultValue(2).min(1).max(8).sliderMin(1).sliderMax(8).build());

    public final ColorSetting inferredSide = addSetting(new ColorSetting.Builder()
        .name("inferred-side-color").defaultValue(new SettingColor(255, 210, 0, 70)).build());

    public final ColorSetting inferredLine = addSetting(new ColorSetting.Builder()
        .name("inferred-line-color").defaultValue(new SettingColor(255, 225, 0, 235)).build());

    public final BoolSetting itemEsp = addSetting(new BoolSetting.Builder()
        .name("item-esp").defaultValue(true).build());

    public final BoolSetting itemDebris = addSetting(new BoolSetting.Builder()
        .name("ancient-debris").defaultValue(true).build());

    public final BoolSetting itemScrap = addSetting(new BoolSetting.Builder()
        .name("netherite-scrap").defaultValue(true).build());

    public final BoolSetting itemIngot = addSetting(new BoolSetting.Builder()
        .name("netherite-ingot").defaultValue(true).build());

    public final BoolSetting itemBlock = addSetting(new BoolSetting.Builder()
        .name("netherite-block").defaultValue(false).build());

    public final IntSetting itemRange = addSetting(new IntSetting.Builder()
        .name("range").defaultValue(128).min(8).sliderMax(256).build());

    public final ColorSetting itemLine = addSetting(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(30, 80, 240, 235)).build());

    private record Section(int cx, int sy, int cz) {}
    private record Dist(long packed, double distSq) {}
    private record Computed(long[] enclosed, long[] yellow) {}

    private final Set<Section> candidates = new LinkedHashSet<>();
    private final Set<Long> debris = new LinkedHashSet<>();
    private final Map<Section, Computed> cache = new HashMap<>();
    private long[] visible = new long[0];
    private long[] visibleYellow = new long[0];
    private ClientWorld lastWorld;
    private int lastLayers = -1;
    private int tick;
    private boolean rebuild;

    public NetheriteFinderPlus() {
        super("NetheriteFinderPlus", "Draws deeply enclosed netherrack inside Nether sections that hide ancient debris", Category.WORLD);
    }

    @Override
    public void onEnable() {
        clearAll();
        scanLoadedChunks();
    }

    @Override
    public void onDisable() {
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

    @EventHandler
    public void onPacket(PacketEvent.Receive event) {
        Object packet = event.packet;

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

    private void handleChunkData(int cx, int cz, PacketByteBuf buf) {
        ClientWorld world = mc.world;
        if (!isEnabled() || world == null || !isNether(world)) {
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

        candidates.removeIf(s -> s.cx() == cx && s.cz() == cz);
        cache.keySet().removeIf(s -> s.cx() == cx && s.cz() == cz);

        for (int sy : hidden) addCandidate(new Section(cx, sy, cz));
        rebuild = true;

        WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz);
        if (chunk != null && showDebris.get()) collectDebris(chunk);
    }

    private void handleBlock(BlockPos pos, BlockState state) {
        if (!isEnabled() || mc.world == null || !isNether(mc.world)) return;

        invalidateAround(pos);

        long packed = pos.asLong();
        if (!state.isOf(Blocks.ANCIENT_DEBRIS)) {
            debris.remove(packed);
            return;
        }

        Section section = new Section(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        candidates.remove(section);
        cache.remove(section);

        if (showDebris.get() && debris.add(packed)) trimDebris();
    }

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

    private void scanLoadedChunks() {
        if (mc.world == null || mc.player == null || !showDebris.get() || !isNether(mc.world)) return;

        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;
        int r = Math.max(2, mc.options.getViewDistance().getValue());

        for (int x = pcx - r; x <= pcx + r; x++) {
            for (int z = pcz - r; z <= pcz + r; z++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x, z);
                if (chunk != null) collectDebris(chunk);
            }
        }
    }

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

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        trackWorld(mc.world);
        tick++;

        int signature = Objects.hash(layers.get(), enclosed.get(), inferred.get(),
            inferredMinY.get(), inferredMaxY.get(), inferredSpacing.get());
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
            if (result == null) continue;

            while (cache.size() >= MAX_CACHED_SECTIONS) {
                Iterator<Section> it = cache.keySet().iterator();
                it.next();
                it.remove();
            }
            cache.put(s, result);
            rebuild = true;
        }
    }

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
            int minWorldY = inferredMinY.get();
            int maxWorldY = inferredMaxY.get();
            int spacing = inferredSpacing.get();
            List<int[]> kept = new ArrayList<>();
            List<Long> out = new ArrayList<>();

            for (int y = m; y < m + 16; y++) {
                int worldY = baseY + y;
                if (worldY < minWorldY || worldY > maxWorldY) continue;

                for (int z = m; z < m + 16; z++) {
                    for (int x = m; x < m + 16; x++) {
                        int i = (y * size + z) * size + x;
                        if (!rack[i] || !closedAround(i, around, rack, solid)) continue;
                        if (spacing > 1 && tooClose(kept, x, y, z, spacing)) continue;

                        kept.add(new int[]{x, y, z});
                        out.add(BlockPos.asLong(baseX + x, baseY + y, baseZ + z));
                    }
                }
            }
            yellowOut = toArray(out);
        }

        return new Computed(enclosedOut, yellowOut);
    }

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

    private static boolean tooClose(List<int[]> kept, int x, int y, int z, int spacing) {
        for (int[] k : kept) {
            if (Math.abs(k[0] - x) < spacing && Math.abs(k[1] - y) < spacing && Math.abs(k[2] - z) < spacing) return true;
        }
        return false;
    }

    private static long[] toArray(List<Long> list) {
        long[] result = new long[list.size()];
        for (int i = 0; i < result.length; i++) result[i] = list.get(i);
        return result;
    }

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

    @EventHandler
    public void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        if (itemEsp.get()) renderItems();
        if (!isNether(mc.world)) return;

        if (outline.get() && !candidates.isEmpty()) renderOutlines();

        if (enclosed.get() && visible.length > 0) {
            SettingColor line = lineColor.get();
            for (long packed : visible) {
                double x = BlockPos.unpackLongX(packed);
                double y = BlockPos.unpackLongY(packed);
                double z = BlockPos.unpackLongZ(packed);
                Render3DUtils.drawBox(new Box(x, y, z, x + 1, y + 1, z + 1), null, line, ShapeMode.Lines);
            }
        }

        if (inferred.get() && visibleYellow.length > 0) {
            SettingColor side = inferredSide.get();
            SettingColor line = inferredLine.get();
            for (long packed : visibleYellow) {
                double x = BlockPos.unpackLongX(packed);
                double y = BlockPos.unpackLongY(packed);
                double z = BlockPos.unpackLongZ(packed);
                Render3DUtils.drawBox(new Box(x, y, z, x + 1, y + 1, z + 1), side, line, ShapeMode.Both);
            }
        }

        if (showDebris.get() && !debris.isEmpty()) renderDebris();
    }

    private void renderItems() {
        Vec3d eye = mc.player.getEyePos();
        double r = itemRange.get();
        double rSq = r * r;
        SettingColor line = itemLine.get();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity item)) continue;
            if (!wantsItem(item.getStack())) continue;

            double dx = item.getX() - eye.x;
            double dy = item.getY() - eye.y;
            double dz = item.getZ() - eye.z;
            if (dx * dx + dy * dy + dz * dz > rSq) continue;

            int bx = (int) Math.floor(item.getX());
            int by = (int) Math.floor(item.getY() + 0.01);
            int bz = (int) Math.floor(item.getZ());

            Render3DUtils.drawBox(new Box(bx, by, bz, bx + 1, by + 1, bz + 1), null, line, ShapeMode.Lines);
        }
    }

    private boolean wantsItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return (itemDebris.get() && stack.isOf(Items.ANCIENT_DEBRIS))
            || (itemScrap.get() && stack.isOf(Items.NETHERITE_SCRAP))
            || (itemIngot.get() && stack.isOf(Items.NETHERITE_INGOT))
            || (itemBlock.get() && stack.isOf(Items.NETHERITE_BLOCK));
    }

    private void renderOutlines() {
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
        SettingColor line = outlineColor.get();

        for (int i = 0; i < limit; i++) {
            Section s = near.get(i);
            double x = s.cx() * 16.0;
            double y = s.sy() * 16.0;
            double z = s.cz() * 16.0;
            Render3DUtils.drawBox(new Box(x, y, z, x + 16.0, y + 16.0, z + 16.0), null, line, ShapeMode.Lines);
        }
    }

    private void renderDebris() {
        Vec3d eye = mc.player.getEyePos();
        double r = debrisRange.get();
        double rSq = r * r;

        SettingColor side = debrisSide.get();
        SettingColor line = debrisLine.get();
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
            if (dx * dx + dy * dy + dz * dz > rSq) continue;

            if (!mc.world.getBlockState(BlockPos.fromLong(packed)).isOf(Blocks.ANCIENT_DEBRIS)) {
                if (gone == null) gone = new ArrayList<>();
                gone.add(packed);
                continue;
            }

            Render3DUtils.drawBox(new Box(x, y, z, x + 1, y + 1, z + 1), side, line, ShapeMode.Both);
            if (tracers && start != null) {
                Render3DUtils.drawLine(new Vec3d(start[0], start[1], start[2]), new Vec3d(x + 0.5, y + 0.5, z + 0.5), line, 1.5f);
            }
        }

        if (gone != null) debris.removeAll(gone);
    }

    private double[] tracerStart() {
        if (mc.player == null) return new double[]{0, 0, 0};
        double yaw = Math.toRadians(mc.player.getYaw());
        double pitch = Math.toRadians(mc.player.getPitch());
        double cosPitch = Math.cos(pitch);
        return new double[]{
            mc.player.getX() - Math.sin(yaw) * cosPitch * 0.6,
            mc.player.getEyeY() - Math.sin(pitch) * 0.6,
            mc.player.getZ() + Math.cos(yaw) * cosPitch * 0.6
        };
    }

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
            readPalette(buf, 64, 3, 1, -1);

            if (nonEmptyBlocks > 0 && blocks.hiddenTarget()) {
                hidden.add(bottomSection + i);
            }
        }

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
