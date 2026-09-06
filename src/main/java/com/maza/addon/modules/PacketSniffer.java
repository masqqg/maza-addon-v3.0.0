package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import com.maza.addon.utils.PacketLogger;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import java.util.List;

public class PacketSniffer extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilters = settings.createGroup("Packet Filters");
    private final SettingGroup sgOutput = settings.createGroup("Output");

    private final Setting<Integer> flushInterval = sgGeneral.add(new IntSetting.Builder()
        .name("flush-interval").description("Chat'e yazma aralığı (tick, 20=1sn)")
        .defaultValue(40).min(5).max(200).sliderRange(5, 200).build());

    private final Setting<Integer> maxLines = sgGeneral.add(new IntSetting.Builder()
        .name("max-lines").description("Her seferinde max kaç satır göster")
        .defaultValue(15).min(1).max(50).sliderRange(1, 50).build());

    // FİLTRELER
    private final Setting<Boolean> fAll = sgFilters.add(new BoolSetting.Builder()
        .name("log-all").description("TÜM packetleri logla").defaultValue(false).build());

    private final Setting<Boolean> fChunk = sgFilters.add(new BoolSetting.Builder()
        .name("chunk").description("Chunk verileri (Data, Delta, Unload, Light)").defaultValue(true).build());
        
    private final Setting<Boolean> fBlock = sgFilters.add(new BoolSetting.Builder()
        .name("block").description("Blok değişimleri (Update, Entity)").defaultValue(true).build());
        
    private final Setting<Boolean> fEntity = sgFilters.add(new BoolSetting.Builder()
        .name("entity").description("Entity hareketleri, spawn, destroy").defaultValue(true).build());
        
    private final Setting<Boolean> fPlayer = sgFilters.add(new BoolSetting.Builder()
        .name("player").description("Oyuncu teleport ve liste").defaultValue(true).build());
        
    // İSTENEN ÖZEL PAKETLER
    private final Setting<Boolean> fActionBar = sgFilters.add(new BoolSetting.Builder()
        .name("action-bar").description("Action Bar / Overlay mesajları").defaultValue(true).build());

    private final Setting<Boolean> fHealth = sgFilters.add(new BoolSetting.Builder()
        .name("health").description("Can ve Açlık güncellemeleri").defaultValue(true).build());

    private final Setting<Boolean> fWorldTime = sgFilters.add(new BoolSetting.Builder()
        .name("world-time").description("Dünya zamanı güncellemeleri").defaultValue(true).build());

    private final Setting<Boolean> showStats = sgOutput.add(new BoolSetting.Builder()
        .name("show-stats").description("Kapatınca toplam sayıyı yaz").defaultValue(true).build());

    private int tickCounter = 0;

    public PacketSniffer() {
        super(MazaCategory.INSTANCE, "packet-sniffer", "Sunucu packetlerini dinler ve filtreler.");
    }

    @Override
    public void onActivate() {
        PacketLogger.active = true;
        PacketLogger.clear();
        updateFilters();
        tickCounter = 0;
        info("Packet sniffer aktif. Filtreler uygulandı.");
    }

    @Override
    public void onDeactivate() {
        PacketLogger.active = false;
        if (showStats.get()) {
            info("Toplam %d packet yakalandı.", PacketLogger.totalPackets);
        }
    }

    private void updateFilters() {
        PacketLogger.enabledTypes.clear();
        
        if (fAll.get()) {
            PacketLogger.enabledTypes.add("ALL");
            return;
        }

        if (fChunk.get()) {
            PacketLogger.enabledTypes.add("ChunkData");
            PacketLogger.enabledTypes.add("ChunkDeltaUpdate");
            PacketLogger.enabledTypes.add("UnloadChunk");
            PacketLogger.enabledTypes.add("LightData");
        }
        if (fBlock.get()) {
            PacketLogger.enabledTypes.add("BlockUpdate");
            PacketLogger.enabledTypes.add("BlockEntityUpdate");
        }
        if (fEntity.get()) {
            PacketLogger.enabledTypes.add("EntitySpawn");
            PacketLogger.enabledTypes.add("EntityPosition");
            PacketLogger.enabledTypes.add("EntityLook");
            PacketLogger.enabledTypes.add("EntityPositionLook");
            PacketLogger.enabledTypes.add("EntityVelocityUpdate");
            PacketLogger.enabledTypes.add("EntityAnimation");
            PacketLogger.enabledTypes.add("EntityStatus");
            PacketLogger.enabledTypes.add("EntityEquipmentUpdate");
            PacketLogger.enabledTypes.add("EntityTrackerUpdate");
            PacketLogger.enabledTypes.add("EntitiesDestroy");
        }
        if (fPlayer.get()) {
            PacketLogger.enabledTypes.add("PlayerPositionLook");
            PacketLogger.enabledTypes.add("PlayerList");
        }
        if (fActionBar.get()) {
            PacketLogger.enabledTypes.add("OverlayMessage"); // 1.21.x'te ActionBar yerine bu gelir
            PacketLogger.enabledTypes.add("ActionBar");       // Eski isim uyumluluğu
        }
        if (fHealth.get()) {
            PacketLogger.enabledTypes.add("HealthUpdate");
        }
        if (fWorldTime.get()) {
            PacketLogger.enabledTypes.add("WorldTimeUpdate");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        tickCounter++;
        if (tickCounter < flushInterval.get()) return;
        tickCounter = 0;

        List<String> batch = PacketLogger.flushQueue();
        if (batch.isEmpty()) return;

        int show = Math.min(maxLines.get(), batch.size());
        int skip = batch.size() - show;

        info("--- Son %d packet (kuyrukta: %d) ---", show, batch.size());
        for (int i = skip; i < batch.size(); i++) {
            info(batch.get(i));
        }
    }
}
