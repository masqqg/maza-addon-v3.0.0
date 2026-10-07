package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.DimensionTypes;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * NetheriteFinder+
 *
 * Passive finder, nothing is ever sent to the server.
 *
 *  1. Hidden sections: the chunk packet is parsed and every 16x16x16 section whose
 *     block palette lists ancient debris while no block uses that entry is
 *     remembered. These are drawn as a thin outline with corner brackets and a
 *     distance label, never as a filled cube.
 *  2. Real debris blocks: every ancient debris block the client actually knows
 *     about (exposed ones in chunk data, and ones the server reveals through block
 *     updates) is painted block by block in translucent blue. Air is never painted.
 *
 * Palette-scan idea follows codexNetheritechunk (MIT). Targets the 1.21.5+
 * chunk format (no length prefix on paletted-container data arrays).
 */
public class NetheriteFinderPlus extends Module {
    private static final Direction[] DIRS = Direction.values();
    private static final int MAX_CANDIDATES = 4096;
    private static final int MAX_STORED_DEBRIS = 4096;
    private static final double BRACKET = 3.0;
    private static final double PULSE_MS = 1400.0;
    private static final Identifier DONUT_NETHER = Identifier.of("worlds", "smp_nether");
    private static final Direction[] DIRS = Direction.values();
    private static final int ANCIENT_STATE_ID = Block.getRawIdFromState(Blocks.ANCIENT_DEBRIS.getDefaultState());

    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup sectionGroup = settings.createGroup("Hidden Sections");
    private final SettingGroup debrisGroup = settings.createGroup("Debris Blocks");

    // ---- general
    private final Setting<Integer> renderRange = general.add(new IntSetting.Builder()
        .name("render-range")
        .description("Max distance in chunks for hidden sections.")
        .defaultValue(24).min(1).sliderMax(64).build());

    private final Setting<Integer> maxBoxes = general.add(new IntSetting.Builder()
        .name("max-boxes")
        .description("Max number of hidden sections drawn at once (nearest first).")
        .defaultValue(64).min(1).sliderMax(512).build());

    private final Setting<Boolean> notify = general.add(new BoolSetting.Builder()
        .name("notify")
        .description("Chat message when new hidden-debris sections are found.")
        .defaultValue(true).build());

    private final Setting<Boolean> notifyReveals = general.add(new BoolSetting.Builder()
        .name("notify-reveals")
        .description("Chat message when the server reveals a real ancient debris block.")
        .defaultValue(true).build());

    // ---- hidden sections
    private final Setting<Boolean> sectionOutline = sectionGroup.add(new BoolSetting.Builder()
        .name("outline")
        .description("Draw the edges of hidden sections.")
        .defaultValue(true).build());

    private final Setting<Boolean> sectionFill = sectionGroup.add(new BoolSetting.Builder()
        .name("fill")
        .description("Also tint the whole 16x16x16 cube. Off by default, the cube stays see-through.")
        .defaultValue(false).build());

    private final Setting<Boolean> brackets = sectionGroup.add(new BoolSetting.Builder()
        .name("corner-brackets")
        .description("Bright L-shaped marks on the 8 corners.")
        .defaultValue(true).build());

    private final Setting<Boolean> pulse = sectionGroup.add(new BoolSetting.Builder()
        .name("pulse")
        .description("Slowly pulse the outline.")
        .defaultValue(true).build());

    private final Setting<Boolean> distanceLabels = sectionGroup.add(new BoolSetting.Builder()
        .name("distance-labels")
        .description("Show the distance above the nearest sections.")
        .defaultValue(true).build());

    private final Setting<Integer> labelLimit = sectionGroup.add(new IntSetting.Builder()
        .name("label-limit")
        .defaultValue(8).min(1).sliderMax(32).build());

    private final Setting<Boolean> sectionTracers = sectionGroup.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Lines to the nearest hidden sections.")
        .defaultValue(false).build());

    private final Setting<Integer> tracerLimit = sectionGroup.add(new IntSetting.Builder()
        .name("tracer-limit")
        .defaultValue(3).min(1).sliderMax(16).build());

    private final Setting<SettingColor> sectionLine = sectionGroup.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(70, 160, 255, 200)).build());

    private final Setting<SettingColor> sectionSide = sectionGroup.add(new ColorSetting.Builder()
        .name("fill-color")
        .defaultValue(new SettingColor(70, 160, 255, 28)).build());

    private final Setting<SettingColor> bracketColor = sectionGroup.add(new ColorSetting.Builder()
        .name("bracket-color")
        .defaultValue(new SettingColor(130, 200, 255, 255)).build());

    // ---- debris blocks
    private final Setting<Boolean> showDebris = debrisGroup.add(new BoolSetting.Builder()
        .name("show-debris")
        .description("Paint real ancient debris blocks the client knows about.")
        .defaultValue(true).build());

    private final Setting<Integer> debrisRange = debrisGroup.add(new IntSetting.Builder()
        .name("debris-range")
        .description("Max distance in blocks for painted debris.")
        .defaultValue(96).min(8).sliderMax(256).build());

    private final Setting<Integer> maxDebris = debrisGroup.add(new IntSetting.Builder()
        .name("max-debris")
        .description("Max number of debris blocks painted at once (nearest first).")
        .defaultValue(256).min(1).sliderMax(1024).build());

    private final Setting<ShapeMode> debrisShape = debrisGroup.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> debrisSide = debrisGroup.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(70, 150, 255, 85)).build());

    private final Setting<SettingColor> debrisLine = debrisGroup.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(110, 190, 255, 230)).build());

    private record Section(int cx, int sy, int cz) {}
    private record Visible(Section section, double distance) {}
    private record Label(Vector3d pos, String text) {}

    // Only touched on the client thread.
    private final Set<Section> candidates = new LinkedHashSet<>();
    private final Set<Long> debris = new LinkedHashSet<>();
    private final Set<Long> potentialNetherite = new LinkedHashSet<>();
    private final List<Label> labels = new ArrayList<>();
    private ClientWorld lastWorld;
    private int pendingNew;
    private Section nearestNew;
    private double nearestNewDistSq;
    private long lastNotifyAt;

    public NetheriteFinderPlus() {
        super(MazaCategory.INSTANCE, "netherite-finder-plus",
            "Outlines Nether chunk sections that hide ancient debris and paints real debris blocks (blue).");
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
        potentialNetherite.clear();
        labels.clear();
        lastWorld = null;
        pendingNew = 0;
        nearestNew = null;
    }

    // ---------------------------------------------------------------- packets

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        Object packet = event.packet;

        // PacketEvent.Receive runs on the client networking thread. Copy the section
        // buffer immediately, because the original may be released after the callback,
        // and never defer the original ByteBuf itself to mc.execute().
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
        if (!showDebris.get() || mc.world == null || event.chunk() == null || !isNether(mc.world)) return;
        trackWorld(mc.world);
        collectDebris(event.chunk());
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

        // A hidden-palette candidate has NO currently placed debris block, so it must not
        // be confirmed with ChunkSection.hasAny(ANCIENT_DEBRIS).
        for (int sy : hidden) add(new Section(cx, sy, cz));
    }

    private void handleBlock(BlockPos pos, BlockState state) {
        if (!isActive() || mc.world == null || !isNether(mc.world)) return;
        long packed = pos.asLong();

        if (!state.isOf(Blocks.ANCIENT_DEBRIS)) {
            debris.remove(packed); // mined or replaced
            return;
        }

        // Real debris became visible here: the section is no longer purely "hidden".
        candidates.remove(new Section(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));

        if (showDebris.get() && debris.add(packed)) {
            trimDebris();
            if (notifyReveals.get()) {
                info("Ancient debris at %d, %d, %d", pos.getX(), pos.getY(), pos.getZ());
            }
        }
    }

    private void add(Section section) {
        if (!candidates.add(section)) return;

        while (candidates.size() > MAX_CANDIDATES) {
            Iterator<Section> it = candidates.iterator();
            it.next();
            it.remove();
        }

        pendingNew++;
        if (mc.player != null) {
            double d = distSq(section);
            if (nearestNew == null || d < nearestNewDistSq) {
                nearestNew = section;
                nearestNewDistSq = d;
            }
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
        if (mc.world == null) return;
        trackWorld(mc.world);

        if (pendingNew > 0 && notify.get()) {
            long now = System.currentTimeMillis();
            if (now - lastNotifyAt >= 3000) {
                lastNotifyAt = now;
                String where = nearestNew == null ? ""
                    : String.format(", nearest at %d, %d, %d",
                        nearestNew.cx() * 16 + 8, nearestNew.sy() * 16 + 8, nearestNew.cz() * 16 + 8);
                info("%d new hidden-debris section(s)%s", pendingNew, where);
                pendingNew = 0;
                nearestNew = null;
            }
        } else if (!notify.get()) {
            pendingNew = 0;
            nearestNew = null;
        }
    }

    private void trackWorld(ClientWorld world) {
        if (lastWorld != world) {
            candidates.clear();
            debris.clear();
        potentialNetherite.clear();
            labels.clear();
            pendingNew = 0;
            nearestNew = null;
            lastWorld = world;
        }
    }

    // ----------------------------------------------------------------- render

    @EventHandler
    private void onRender(Render3DEvent event) {
        labels.clear();
        if (mc.world == null || mc.player == null || !isNether(mc.world)) return;

        renderSections(event);
        if (showDebris.get()) renderDebris(event);
    }

    private void renderSections(Render3DEvent event) {
        if (candidates.isEmpty()) return;

        Vec3d eye = mc.player.getEyePos();
        double maxDistance = renderRange.get() * 16.0;

        List<Visible> visible = new ArrayList<>();
        for (Section s : candidates) {
            double d = Math.sqrt(boxDistSq(s, eye));
            if (d <= maxDistance) visible.add(new Visible(s, d));
        }
        if (visible.isEmpty()) return;

        visible.sort(Comparator.comparingDouble(Visible::distance));
        int limit = Math.min(visible.size(), maxBoxes.get());

        double wave = pulse.get()
            ? 0.5 + 0.5 * Math.sin((System.currentTimeMillis() % (long) PULSE_MS) / PULSE_MS * Math.PI * 2.0)
            : 0.5;

        double yaw = Math.toRadians(mc.player.getYaw());
        double pitch = Math.toRadians(mc.player.getPitch());
        double cosPitch = Math.cos(pitch);
        double sx = eye.x + -Math.sin(yaw) * cosPitch * 0.6;
        double sy = eye.y + -Math.sin(pitch) * 0.6;
        double sz = eye.z + Math.cos(yaw) * cosPitch * 0.6;

        for (int i = 0; i < limit; i++) {
            Visible v = visible.get(i);
            Section s = v.section();

            double x1 = s.cx() * 16.0;
            double y1 = s.sy() * 16.0;
            double z1 = s.cz() * 16.0;
            double x2 = x1 + 16.0;
            double y2 = y1 + 16.0;
            double z2 = z1 + 16.0;

            // Far sections are faint, near ones strong.
            double near = 0.35 + 0.65 * Math.max(0.0, 1.0 - v.distance() / maxDistance);
            double glow = near * (0.75 + 0.25 * wave);

            Color line = scale(sectionLine.get(), glow);
            Color side = scale(sectionSide.get(), near);

            if (sectionOutline.get() || sectionFill.get()) {
                ShapeMode mode = sectionFill.get()
                    ? (sectionOutline.get() ? ShapeMode.Both : ShapeMode.Sides)
                    : ShapeMode.Lines;
                event.renderer.box(x1, y1, z1, x2, y2, z2, side, line, mode, 0);
            }

            if (brackets.get()) drawBrackets(event, x1, y1, z1, x2, y2, z2, scale(bracketColor.get(), near));

            double cx = (x1 + x2) / 2.0;
            double cy = (y1 + y2) / 2.0;
            double cz = (z1 + z2) / 2.0;

            if (sectionTracers.get() && i < tracerLimit.get()) {
                event.renderer.line(sx, sy, sz, cx, cy, cz, scale(sectionLine.get(), 0.9));
            }

            if (distanceLabels.get() && i < labelLimit.get()) {
                labels.add(new Label(new Vector3d(cx, cy + 1.0, cz),
                    String.format(Locale.ROOT, "%.0fm", v.distance())));
            }
        }
    }

    private void drawBrackets(Render3DEvent event, double x1, double y1, double z1,
                              double x2, double y2, double z2, Color color) {
        for (int ix = 0; ix < 2; ix++) {
            for (int iy = 0; iy < 2; iy++) {
                for (int iz = 0; iz < 2; iz++) {
                    double x = ix == 0 ? x1 : x2;
                    double y = iy == 0 ? y1 : y2;
                    double z = iz == 0 ? z1 : z2;
                    double dx = ix == 0 ? BRACKET : -BRACKET;
                    double dy = iy == 0 ? BRACKET : -BRACKET;
                    double dz = iz == 0 ? BRACKET : -BRACKET;

                    event.renderer.line(x, y, z, x + dx, y, z, color);
                    event.renderer.line(x, y, z, x, y + dy, z, color);
                    event.renderer.line(x, y, z, x, y, z + dz, color);
                }
            }
        }
    }

    private void renderDebris(Render3DEvent event) {
        if (debris.isEmpty()) return;

        Vec3d eye = mc.player.getEyePos();
        double range = debrisRange.get();
        double rangeSq = range * range;

        List<BlockPos> shown = new ArrayList<>();
        List<Long> gone = null;

        for (long packed : debris) {
            BlockPos pos = BlockPos.fromLong(packed);
            double dx = pos.getX() + 0.5 - eye.x;
            double dy = pos.getY() + 0.5 - eye.y;
            double dz = pos.getZ() + 0.5 - eye.z;
            if (dx * dx + dy * dy + dz * dz > rangeSq) continue;

            // Self-cleaning: if the block is no longer debris, forget it.
            if (!mc.world.getBlockState(pos).isOf(Blocks.ANCIENT_DEBRIS)) {
                if (gone == null) gone = new ArrayList<>();
                gone.add(packed);
                continue;
            }
            shown.add(pos);
        }

        if (gone != null) debris.removeAll(gone);
        if (shown.isEmpty()) return;

        shown.sort(Comparator.comparingDouble(p -> {
            double dx = p.getX() + 0.5 - eye.x;
            double dy = p.getY() + 0.5 - eye.y;
            double dz = p.getZ() + 0.5 - eye.z;
            return dx * dx + dy * dy + dz * dz;
        }));

        int limit = Math.min(shown.size(), maxDebris.get());
        double e = 0.002; // avoid z-fighting with the block faces

        // Only the block itself is painted, never the air around it.
        for (int i = 0; i < limit; i++) {
            BlockPos p = shown.get(i);
            event.renderer.box(
                p.getX() - e, p.getY() - e, p.getZ() - e,
                p.getX() + 1 + e, p.getY() + 1 + e, p.getZ() + 1 + e,
                debrisSide.get(), debrisLine.get(), debrisShape.get(), 0
            );
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (labels.isEmpty() || mc.world == null || mc.player == null) return;

        TextRenderer text = TextRenderer.get();
        Color white = new Color(255, 255, 255, 255);

        for (Label label : labels) {
            if (!NametagUtils.to2D(label.pos(), 1.0, true)) continue;

            NametagUtils.begin(label.pos());
            text.beginBig();
            double half = text.getWidth(label.text(), true) / 2.0;
            text.render(label.text(), -half, -text.getHeight(true), white, true);
            text.end();
            NametagUtils.end();
        }
    }

    private static Color scale(Color base, double factor) {
        int a = (int) Math.max(0, Math.min(255, base.a * factor));
        return new Color(base.r, base.g, base.b, a);
    }

    private double distSq(Section s) {
        double dx = s.cx() * 16.0 + 8.0 - mc.player.getX();
        double dy = s.sy() * 16.0 + 8.0 - mc.player.getY();
        double dz = s.cz() * 16.0 + 8.0 - mc.player.getZ();
        return dx * dx + dy * dy + dz * dz;
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


    private void scanForPotentialNetherite(WorldChunk chunk) {
        if (mc.world == null || !showDebris.get()) return;
        potentialNetherite.clear();
        
        ChunkPos cp = chunk.getPos();
        ChunkSection[] sections = chunk.getSectionArray();
        if (sections == null) return;
        int bottom = chunk.getBottomY();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            int sectionBottom = bottom + i * 16;

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!state.isOf(Blocks.NETHERRACK)) continue;

                        int wx = cp.getStartX() + x;
                        int wy = sectionBottom + y;
                        int wz = cp.getStartZ() + z;

                        if (isPotentialNetherite(wx, wy, wz)) {
                            potentialNetherite.add(BlockPos.asLong(wx, wy, wz));
                        }
                    }
                }
            }
        }
    }

    private boolean isPotentialNetherite(int x, int y, int z) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (Direction d : DIRS) {
            int nx = x + d.getOffsetX();
            int ny = y + d.getOffsetY();
            int nz = z + d.getOffsetZ();
            pos.set(nx, ny, nz);
            BlockState neighborState = mc.world.getBlockState(pos);
            if (neighborState.isAir() || !neighborState.getFluidState().isEmpty()) return false;
            if (!isFullyEnclosed(nx, ny, nz)) return false;
        }
        return true;
    }

    private boolean isFullyEnclosed(int x, int y, int z) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (Direction d : DIRS) {
            pos.set(x + d.getOffsetX(), y + d.getOffsetY(), z + d.getOffsetZ());
            BlockState state = mc.world.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    private void renderPotentialNetherite(Render3DEvent event) {
        if (potentialNetherite.isEmpty()) return;
        Color yellowSide = new Color(255, 200, 0, 70);
        Color yellowLine = new Color(255, 220, 0, 220);
        double e = 0.002;
        for (long packed : potentialNetherite) {
            int x = BlockPos.unpackLongX(packed);
            int y = BlockPos.unpackLongY(packed);
            int z = BlockPos.unpackLongZ(packed);
            event.renderer.box(x - e, y - e, z - e, x + 1 + e, y + 1 + e, z + 1 + e,
                yellowSide, yellowLine, ShapeMode.Both, 0);
        }
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
