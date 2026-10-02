package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionTypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * NetheriteFinder+
 *
 * Passive finder: reads the chunk packets the server already sends and flags
 * 16x16x16 sections whose block palette lists ancient debris while no block in
 * the section actually uses that palette entry (i.e. the debris is hidden).
 * Flagged sections are drawn as green boxes. Nothing is sent to the server.
 *
 * Palette-scan idea follows codexNetheritechunk (MIT). Targets the 1.21.5+
 * chunk format (no length prefix on paletted-container data arrays).
 */
public class NetheriteFinderPlus extends Module {
    private static final int MAX_CANDIDATES = 4096;
    private static final Identifier DONUT_NETHER = Identifier.of("worlds", "smp_nether");
    private static final int ANCIENT_STATE_ID = Block.getRawIdFromState(Blocks.ANCIENT_DEBRIS.getDefaultState());

    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup render = settings.createGroup("Render");

    private final Setting<Integer> renderRange = general.add(new IntSetting.Builder()
        .name("render-range")
        .description("Max horizontal distance in chunks to draw boxes.")
        .defaultValue(12).min(1).sliderMax(32).build());

    private final Setting<Integer> maxBoxes = general.add(new IntSetting.Builder()
        .name("max-boxes")
        .description("Max number of boxes drawn at once (nearest first).")
        .defaultValue(64).min(1).sliderMax(512).build());

    private final Setting<Boolean> notify = general.add(new BoolSetting.Builder()
        .name("notify")
        .description("Send a chat message when new hidden-debris sections are found.")
        .defaultValue(true).build());

    private final Setting<ShapeMode> shapeMode = render.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> sideColor = render.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(0, 255, 0, 45)).build());

    private final Setting<SettingColor> lineColor = render.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(0, 255, 0, 255)).build());

    private record Section(int cx, int sy, int cz) {}

    // Only touched on the client thread.
    private final Set<Section> candidates = new LinkedHashSet<>();
    private ClientWorld lastWorld;
    private int pendingNew;
    private Section nearestNew;
    private double nearestNewDistSq;
    private long lastNotifyAt;

    public NetheriteFinderPlus() {
        super(MazaCategory.INSTANCE, "netherite-finder-plus",
            "Boxes Nether chunk sections that hide ancient debris (green).");
    }

    @Override
    public void onActivate() {
        clearAll();
    }

    @Override
    public void onDeactivate() {
        clearAll();
    }

    private void clearAll() {
        candidates.clear();
        lastWorld = null;
        pendingNew = 0;
        nearestNew = null;
    }

    // ---------------------------------------------------------------- packets

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        Object packet = event.packet;

        if (packet instanceof ChunkDataS2CPacket chunkPacket) {
            // Hop to the client thread so the world/state we read is current.
            mc.execute(() -> handleChunk(chunkPacket));
        } else if (packet instanceof BlockUpdateS2CPacket update) {
            BlockPos pos = update.getPos();
            BlockState state = update.getState();
            mc.execute(() -> handleBlock(pos, state));
        } else if (packet instanceof ChunkDeltaUpdateS2CPacket delta) {
            mc.execute(() -> delta.visitUpdates(this::handleBlock));
        }
    }

    private void handleChunk(ChunkDataS2CPacket packet) {
        ClientWorld world = mc.world;
        if (!isActive() || world == null || !isNether(world)) return;
        trackWorld(world);

        int cx = packet.getChunkX();
        int cz = packet.getChunkZ();
        List<Integer> hidden;

        PacketByteBuf buf = packet.getChunkData().getSectionsDataBuf();
        try {
            hidden = scanSections(buf, world.countVerticalSections(), world.getBottomSectionCoord(), ANCIENT_STATE_ID);
        } catch (RuntimeException ignored) {
            return;
        } finally {
            buf.release();
        }

        candidates.removeIf(s -> s.cx() == cx && s.cz() == cz);

        for (int sy : hidden) {
            Section candidate = new Section(cx, sy, cz);
            if (confirmSection(world, candidate)) add(candidate);
        }
    }

    /** Second-stage client-world confirmation. Packet candidates remain valid
     * when the client section is unavailable during chunk loading/replacement. */
    private static boolean confirmSection(ClientWorld world, Section section) {
        try {
            var chunk = world.getChunk(section.cx(), section.cz());
            int sectionIndex = section.sy() - world.getBottomSectionCoord();
            if (sectionIndex < 0 || sectionIndex >= chunk.getSectionArray().length) return true;

            var chunkSection = chunk.getSection(sectionIndex);
            if (chunkSection == null) return true;

            // Do not require an exposed debris block: the packet/palette stage
            // is specifically looking for hidden debris candidates.
            return chunkSection.hasAny(state -> state.isOf(Blocks.ANCIENT_DEBRIS));
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void handleBlock(BlockPos pos, BlockState state) {
        if (!isActive() || candidates.isEmpty()) return;
        if (!state.isOf(Blocks.ANCIENT_DEBRIS)) return;
        // Real debris became visible here: this section is no longer "hidden".
        candidates.remove(new Section(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
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
            pendingNew = 0;
            nearestNew = null;
            lastWorld = world;
        }
    }

    // ----------------------------------------------------------------- render

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null || candidates.isEmpty() || !isNether(mc.world)) return;

        int pcx = mc.player.getBlockX() >> 4;
        int pcz = mc.player.getBlockZ() >> 4;
        int range = renderRange.get();

        List<Section> visible = new ArrayList<>();
        for (Section s : candidates) {
            if (Math.max(Math.abs(s.cx() - pcx), Math.abs(s.cz() - pcz)) <= range) visible.add(s);
        }
        if (visible.isEmpty()) return;

        visible.sort(Comparator.comparingDouble(this::distSq));
        int limit = Math.min(visible.size(), maxBoxes.get());

        for (int i = 0; i < limit; i++) {
            Section s = visible.get(i);
            double x = s.cx() * 16.0;
            double y = s.sy() * 16.0;
            double z = s.cz() * 16.0;
            event.renderer.box(x, y, z, x + 16.0, y + 16.0, z + 16.0,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    private double distSq(Section s) {
        double dx = s.cx() * 16.0 + 8.0 - mc.player.getX();
        double dy = s.sy() * 16.0 + 8.0 - mc.player.getY();
        double dz = s.cz() * 16.0 + 8.0 - mc.player.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean isNether(ClientWorld world) {
        if (world.getRegistryKey() == World.NETHER) return true;
        if (world.getRegistryKey().getValue().equals(DONUT_NETHER)) return true;
        return world.getDimensionEntry().matchesKey(DimensionTypes.THE_NETHER);
    }

    // ---------------------------------------------------------- palette scan

    /** Returns the section Y coords whose block palette holds {@code target} but no block uses it. */
    private static List<Integer> scanSections(PacketByteBuf buf, int sectionCount, int bottomSection, int target) {
        if (sectionCount <= 0) throw new IllegalArgumentException("bad section count");
        List<Integer> out = new ArrayList<>();

        for (int i = 0; i < sectionCount; i++) {
            int nonEmpty = buf.readUnsignedShort();                 // non-air block count
            boolean hidden = readPalette(buf, 4096, 8, 4, target);  // block states 16^3
            readPalette(buf, 64, 3, 1, -1);                         // biomes 4^3 (skipped)
            if (nonEmpty > 0 && hidden) out.add(bottomSection + i);
        }
        return out;
    }

    /**
     * Reads one paletted container and consumes its bytes. Returns true when the
     * palette contains {@code target} but no entry in the data array points to it.
     */
    private static boolean readPalette(PacketByteBuf buf, int valueCount, int maxLocalBits, int minLocalBits, int target) {
        int bits = buf.readUnsignedByte();
        int[] palette = null;

        if (bits == 0) {
            palette = new int[]{buf.readVarInt()};                  // single-valued
        } else if (bits <= maxLocalBits) {
            bits = Math.max(bits, minLocalBits);                    // indirect
            int size = buf.readVarInt();
            if (size <= 0 || size > (1 << bits)) throw new IllegalArgumentException("bad palette size");
            palette = new int[size];
            for (int i = 0; i < size; i++) palette[i] = buf.readVarInt();
        } else if (bits > 30) {
            throw new IllegalArgumentException("direct palette too wide"); // direct
        }

        int entriesPerLong = bits == 0 ? 0 : 64 / bits;
        long bytes = bits == 0 ? 0 : (long) ((valueCount + entriesPerLong - 1) / entriesPerLong) * 8L;
        if (buf.readableBytes() < bytes) throw new IndexOutOfBoundsException("truncated palette data");

        boolean hasTarget = palette != null && contains(palette, target);
        int usage = 0;

        if (hasTarget) {
            if (bits == 0) {
                usage = valueCount; // single value == target: every block is debris
            } else {
                int start = buf.readerIndex();
                long mask = (1L << bits) - 1L;
                for (int i = 0; i < valueCount; i++) {
                    long word = buf.getLong(start + (i / entriesPerLong) * 8);
                    int index = (int) ((word >>> ((i % entriesPerLong) * bits)) & mask);
                    if (index >= palette.length) throw new IllegalArgumentException("bad palette index");
                    if (palette[index] == target) usage++;
                }
            }
        }

        buf.skipBytes((int) bytes);
        return hasTarget && usage == 0;
    }

    private static boolean contains(int[] values, int target) {
        for (int v : values) if (v == target) return true;
        return false;
    }
}
