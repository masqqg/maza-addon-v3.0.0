package com.maza.addon.utils;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PacketLogger {

    public static boolean active = false;
    public static int totalPackets = 0;
    public static final List<String> enabledTypes = new ArrayList<>();

    private static final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    public static final List<String> recentLogs = new ArrayList<>();
    private static final int MAX_RECENT = 500;

    public static void onPacket(Packet<?> packet) {
        if (!active) return;

        String type = getPacketType(packet);
        if (!enabledTypes.contains(type)) return;

        totalPackets++;
        String line = formatPacket(packet, type);

        queue.add(line);
        recentLogs.add(line);
        if (recentLogs.size() > MAX_RECENT) {
            recentLogs.remove(0);
        }
    }

    private static String getPacketType(Packet<?> packet) {
        if (packet instanceof ChunkDataS2CPacket) return "CHUNK_DATA";
        if (packet instanceof ChunkDeltaUpdateS2CPacket) return "CHUNK_DELTA";
        if (packet instanceof BlockUpdateS2CPacket) return "BLOCK_UPDATE";
        if (packet instanceof EntitySpawnS2CPacket) return "ENTITY_SPAWN";
        if (packet instanceof EntityPositionS2CPacket) return "ENTITY_POS";
        if (packet instanceof EntityLookS2CPacket) return "ENTITY_LOOK";
        if (packet instanceof EntityPositionLookS2CPacket) return "ENTITY_LOOKPOS";
        if (packet instanceof EntityVelocityUpdateS2CPacket) return "ENTITY_VEL";
        if (packet instanceof EntityAnimationS2CPacket) return "ENTITY_ANIM";
        if (packet instanceof EntityStatusS2CPacket) return "ENTITY_STATUS";
        if (packet instanceof EntityEquipmentUpdateS2CPacket) return "ENTITY_EQUIP";
        if (packet instanceof EntityTrackerUpdateS2CPacket) return "ENTITY_TRACKER";
        if (packet instanceof EntitiesDestroyS2CPacket) return "ENTITY_DESTROY";
        if (packet instanceof PlayerPositionLookS2CPacket) return "PLAYER_TELEPORT";
        if (packet instanceof DisconnectS2CPacket) return "DISCONNECT";
        if (packet instanceof TitleS2CPacket) return "TITLE";
        if (packet instanceof SubtitleS2CPacket) return "SUBTITLE";
        if (packet instanceof ActionBarS2CPacket) return "ACTIONBAR";
        if (packet instanceof WorldTimeUpdateS2CPacket) return "WORLD_TIME";
        if (packet instanceof HealthUpdateS2CPacket) return "HEALTH";
        if (packet instanceof ExperienceBarUpdateS2CPacket) return "XP";
        if (packet instanceof ExplosionS2CPacket) return "EXPLOSION";
        if (packet instanceof PlaySoundS2CPacket) return "SOUND";
        if (packet instanceof ParticleS2CPacket) return "PARTICLE";
        if (packet instanceof KeepAliveS2CPacket) return "KEEP_ALIVE";
        if (packet instanceof UnloadChunkS2CPacket) return "UNLOAD_CHUNK";
        if (packet instanceof LightDataS2CPacket) return "LIGHT_DATA";
        if (packet instanceof BlockEntityUpdateS2CPacket) return "BLOCK_ENTITY";
        if (packet instanceof ScreenHandlerSlotUpdateS2CPacket) return "CONTAINER_SLOT";
        if (packet instanceof OpenScreenS2CPacket) return "OPEN_SCREEN";
        if (packet instanceof CloseScreenS2CPacket) return "CLOSE_SCREEN";
        if (packet instanceof ScreenHandlerContentS2CPacket) return "CONTAINER_CONTENT";
        return "OTHER_" + packet.getClass().getSimpleName();
    }

    private static String formatPacket(Packet<?> packet, String type) {
        switch (type) {
            case "CHUNK_DATA":
                ChunkDataS2CPacket cdp = (ChunkDataS2CPacket) packet;
                return String.format("CHUNK_DATA | chunk=[%d,%d]", cdp.getChunkX(), cdp.getChunkZ());

            case "BLOCK_UPDATE":
                BlockUpdateS2CPacket bup = (BlockUpdateS2CPacket) packet;
                BlockPos bp = bup.getPos();
                return String.format("BLOCK_UPDATE | pos=[%d,%d,%d] block=%s",
                    bp.getX(), bp.getY(), bp.getZ(), bup.getBlockState().getBlock().getName().getString());

            case "ENTITY_SPAWN":
                EntitySpawnS2CPacket esp = (EntitySpawnS2CPacket) packet;
                return String.format("ENTITY_SPAWN | id=%d type=%s pos=[%.1f,%.1f,%.1f]",
                    esp.getId(), esp.getEntityType().toString(), esp.getX(), esp.getY(), esp.getZ());

            case "ENTITY_POS":
                EntityPositionS2CPacket epp = (EntityPositionS2CPacket) packet;
                return String.format("ENTITY_POS | id=%d pos=[%.1f,%.1f,%.1f]",
                    epp.getId(), epp.getX(), epp.getY(), epp.getZ());

            case "ENTITY_LOOK":
                EntityLookS2CPacket elp = (EntityLookS2CPacket) packet;
                return String.format("ENTITY_LOOK | id=%d yaw=%.1f pitch=%.1f",
                    elp.getId(), elp.getYaw(), elp.getPitch());

            case "ENTITY_LOOKPOS":
                EntityPositionLookS2CPacket elpp = (EntityPositionLookS2CPacket) packet;
                return String.format("ENTITY_LOOKPOS | id=%d pos=[%.1f,%.1f,%.1f] yaw=%.1f pitch=%.1f",
                    elpp.getId(), elpp.getX(), elpp.getY(), elpp.getZ(), elpp.getYaw(), elpp.getPitch());

            // 1.21.11 uyumlu: detaylı info yerine sadece ID/type
            case "ENTITY_VEL":
            case "ENTITY_ANIM":
            case "ENTITY_STATUS":
            case "ENTITY_EQUIP":
            case "ENTITY_TRACKER":
                return type + " | entity=" + getEntityIdSafe(packet);

            case "ENTITY_DESTROY":
                EntitiesDestroyS2CPacket edp = (EntitiesDestroyS2CPacket) packet;
                return String.format("ENTITY_DESTROY | ids=%s", edp.getEntityIds());

            case "PLAYER_TELEPORT":
                PlayerPositionLookS2CPacket ptlp = (PlayerPositionLookS2CPacket) packet;
                return String.format("PLAYER_TELEPORT | pos=[%.1f,%.1f,%.1f] yaw=%.1f pitch=%.1f",
                    ptlp.getX(), ptlp.getY(), ptlp.getZ(), ptlp.getYaw(), ptlp.getPitch());

            case "DISCONNECT":
                DisconnectS2CPacket dp = (DisconnectS2CPacket) packet;
                return String.format("DISCONNECT | reason=%s", dp.getReason().getString());

            case "TITLE":
                return "TITLE | başlık gösterildi";
            case "SUBTITLE":
                return "SUBTITLE | alt başlık";

            case "ACTIONBAR":
                ActionBarS2CPacket abp = (ActionBarS2CPacket) packet;
                return String.format("ACTIONBAR | %s", abp.getContent().getString());

            case "WORLD_TIME":
                WorldTimeUpdateS2CPacket wtp = (WorldTimeUpdateS2CPacket) packet;
                return String.format("WORLD_TIME | age=%d tod=%d", wtp.getTime(), wtp.getTimeOfDay());

            case "HEALTH":
                HealthUpdateS2CPacket hup = (HealthUpdateS2CPacket) packet;
                return String.format("HEALTH | hp=%.1f food=%d sat=%.1f",
                    hup.getHealth(), hup.getFood(), hup.getSaturation());

            // 1.21.11 uyumlu: XP detayları kaldırıldı
            case "XP":
                return "XP | deneyim güncellendi";

            // 1.21.11 uyumlu: patlama detayları kaldırıldı
            case "EXPLOSION":
                return "EXPLOSION | patlama gerçekleşti";

            case "SOUND":
                PlaySoundS2CPacket sdp = (PlaySoundS2CPacket) packet;
                return String.format("SOUND | %s pos=[%.1f,%.1f,%.1f]",
                    sdp.getSound().value().getId().toString(), sdp.getX(), sdp.getY(), sdp.getZ());

            case "PARTICLE":
                ParticleS2CPacket pp = (ParticleS2CPacket) packet;
                return String.format("PARTICLE | %s count=%d pos=[%.1f,%.1f,%.1f]",
                    pp.getParameters().getType().toString(), pp.getCount(), pp.getX(), pp.getY(), pp.getZ());

            case "KEEP_ALIVE":
                KeepAliveS2CPacket kap = (KeepAliveS2CPacket) packet;
                return String.format("KEEP_ALIVE | id=%d", kap.getId());

            case "UNLOAD_CHUNK":
                UnloadChunkS2CPacket ucp = (UnloadChunkS2CPacket) packet;
                return String.format("UNLOAD_CHUNK | chunk=[%d,%d]", ucp.pos().x, ucp.pos().z);

            case "LIGHT_DATA":
                LightDataS2CPacket ldp = (LightDataS2CPacket) packet;
                return String.format("LIGHT_DATA | chunk=[%d,%d]", ldp.getPos().x, ldp.getPos().z);

            case "BLOCK_ENTITY":
                BlockEntityUpdateS2CPacket beup = (BlockEntityUpdateS2CPacket) packet;
                return String.format("BLOCK_ENTITY | pos=[%d,%d,%d] type=%d",
                    beup.getPos().getX(), beup.getPos().getY(), beup.getPos().getZ(), beup.getBlockEntityType());

            case "CONTAINER_SLOT":
                ScreenHandlerSlotUpdateS2CPacket shsup = (ScreenHandlerSlotUpdateS2CPacket) packet;
                return String.format("CONTAINER_SLOT | handler=%d slot=%d", shsup.getSyncId(), shsup.getSlot());

            case "OPEN_SCREEN":
                OpenScreenS2CPacket osp = (OpenScreenS2CPacket) packet;
                return String.format("OPEN_SCREEN | name=%s handler=%d", osp.getName().getString(), osp.getSyncId());

            case "CLOSE_SCREEN":
                return "CLOSE_SCREEN";

            case "CONTAINER_CONTENT":
                ScreenHandlerContentS2CPacket shcp = (ScreenHandlerContentS2CPacket) packet;
                return String.format("CONTAINER_CONTENT | handler=%d slots=%d", shcp.getSyncId(), shcp.getItems().size());

            default:
                return type + " | bilinmeyen packet";
        }
    }

    // Güvenli entity ID alma (1.21.11 uyumlu)
    private static String getEntityIdSafe(Packet<?> packet) {
        try {
            if (packet instanceof EntityVelocityUpdateS2CPacket p) return String.valueOf(p.getId());
            if (packet instanceof EntityAnimationS2CPacket p) return String.valueOf(p.getId());
            if (packet instanceof EntityStatusS2CPacket p) return String.valueOf(p.getEntityId());
            if (packet instanceof EntityEquipmentUpdateS2CPacket p) return String.valueOf(p.getId());
            if (packet instanceof EntityTrackerUpdateS2CPacket p) return String.valueOf(p.getId());
        } catch (Exception ignored) {}
        return "?";
    }

    public static List<String> flushQueue() {
        List<String> batch = new ArrayList<>();
        String line;
        while ((line = queue.poll()) != null) {
            batch.add(line);
        }
        return batch;
    }

    public static void clear() {
        queue.clear();
        recentLogs.clear();
        totalPackets = 0;
    }
}
