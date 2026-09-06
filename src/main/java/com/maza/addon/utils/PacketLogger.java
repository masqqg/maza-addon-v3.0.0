package com.maza.addon.utils;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;

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
        String line = packet.getClass().getSimpleName();

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
        return "OTHER";
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
