package com.maza.addon.utils;

import net.minecraft.network.packet.Packet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PacketLogger {

    public static boolean active = false;
    public static int totalPackets = 0;
    public static final List<String> enabledCategories = new ArrayList<>();

    private static final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    public static final List<String> recentLogs = new ArrayList<>();
    private static final int MAX_RECENT = 500;

    public static void onPacket(Packet<?> packet) {
        if (!active) return;

        String className = packet.getClass().getName();
        String category = getCategory(className);
        
        if (!enabledCategories.contains(category)) return;

        String info = packet.toString();
        if (info.length() > 80) info = info.substring(0, 80) + "...";

        totalPackets++;
        queue.add("[" + category + "] " + info);
        recentLogs.add("[" + category + "] " + info);
        
        if (recentLogs.size() > MAX_RECENT) recentLogs.remove(0);
    }

    private static String getCategory(String className) {
        // Envanter/Menü
        if (className.contains("ScreenHandler") || className.contains("OpenScreen") || 
            className.contains("CloseScreen") || className.contains("Trade")) {
            return "ENVANTER";
        }
        // PlayerInfo
        if (className.contains("PlayerList") || className.contains("PlayerInfo")) {
            return "PLAYER_INFO";
        }
        // PlayerPosition
        if (className.contains("PlayerPosition")) {
            return "PLAYER_POS";
        }
        // Entity
        if (className.contains("Entity")) {
            return "ENTITY";
        }
        // Block
        if (className.contains("Block")) {
            return "BLOCK";
        }
        // Chunk
        if (className.contains("Chunk") || className.contains("LightData")) {
            return "CHUNK";
        }
        return "OTHER";
    }

    public static List<String> flushQueue() {
        List<String> batch = new ArrayList<>();
        String line;
        while ((line = queue.poll()) != null) batch.add(line);
        return batch;
    }

    public static void clear() {
        queue.clear();
        recentLogs.clear();
        totalPackets = 0;
    }
}
