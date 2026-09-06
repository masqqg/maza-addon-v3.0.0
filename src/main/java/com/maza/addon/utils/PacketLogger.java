package com.maza.addon.utils;

import net.minecraft.network.packet.Packet;
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

        // Dinamik olarak class ismini al (1.21.11'deki isim değişikliklerinden etkilenmez)
        String type = packet.getClass().getSimpleName();
        type = type.replace("S2CPacket", "").replace("C2SPacket", "").replace("Packet", "");

        // Filtre kontrolü
        if (!enabledTypes.contains("ALL") && !enabledTypes.contains(type)) {
            return;
        }

        totalPackets++;
        queue.add(type);
        recentLogs.add(type);
        
        if (recentLogs.size() > MAX_RECENT) {
            recentLogs.remove(0);
        }
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
