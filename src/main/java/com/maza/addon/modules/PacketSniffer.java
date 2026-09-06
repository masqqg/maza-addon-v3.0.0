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
    private final SettingGroup sgFilters = settings.createGroup("Kategoriler");
    private final SettingGroup sgOutput = settings.createGroup("Çıktı");

    private final Setting<Integer> flushInterval = sgGeneral.add(new IntSetting.Builder()
        .name("flush-interval").description("Chat'e yazma aralığı (tick)")
        .defaultValue(40).min(5).max(200).sliderRange(5, 200).build());

    private final Setting<Integer> maxLines = sgGeneral.add(new IntSetting.Builder()
        .name("max-lines").description("Max satır sayısı")
        .defaultValue(15).min(1).max(50).sliderRange(1, 50).build());

    // KATEGORİ FİLTRELERİ
    private final Setting<Boolean> fEnvanter = sgFilters.add(new BoolSetting.Builder()
        .name("envanter").description("Envanter/Menü packetleri").defaultValue(true).build());
    private final Setting<Boolean> fPlayerInfo = sgFilters.add(new BoolSetting.Builder()
        .name("player-info").description("PlayerList/PlayerInfo").defaultValue(true).build());
    private final Setting<Boolean> fPlayerPos = sgFilters.add(new BoolSetting.Builder()
        .name("player-pos").description("PlayerPosition/Teleport").defaultValue(true).build());
    private final Setting<Boolean> fEntity = sgFilters.add(new BoolSetting.Builder()
        .name("entity").description("Tüm Entity işlemleri").defaultValue(true).build());
    private final Setting<Boolean> fBlock = sgFilters.add(new BoolSetting.Builder()
        .name("block").description("Blok işlemleri").defaultValue(true).build());
    private final Setting<Boolean> fChunk = sgFilters.add(new BoolSetting.Builder()
        .name("chunk").description("Chunk işlemleri").defaultValue(true).build());

    private final Setting<Boolean> showStats = sgOutput.add(new BoolSetting.Builder()
        .name("show-stats").description("Kapatınca istatistik göster").defaultValue(true).build());

    private int tickCounter = 0;

    public PacketSniffer() {
        super(MazaCategory.INSTANCE, "packet-sniffer", "Kategori bazlı packet sniffer");
    }

    @Override
    public void onActivate() {
        PacketLogger.active = true;
        PacketLogger.clear();
        updateFilters();
        tickCounter = 0;
        info("Packet sniffer aktif.");
    }

    @Override
    public void onDeactivate() {
        PacketLogger.active = false;
        if (showStats.get()) info("Toplam %d packet.", PacketLogger.totalPackets);
    }

    private void updateFilters() {
        PacketLogger.enabledCategories.clear();
        if (fEnvanter.get()) PacketLogger.enabledCategories.add("ENVANTER");
        if (fPlayerInfo.get()) PacketLogger.enabledCategories.add("PLAYER_INFO");
        if (fPlayerPos.get()) PacketLogger.enabledCategories.add("PLAYER_POS");
        if (fEntity.get()) PacketLogger.enabledCategories.add("ENTITY");
        if (fBlock.get()) PacketLogger.enabledCategories.add("BLOCK");
        if (fChunk.get()) PacketLogger.enabledCategories.add("CHUNK");
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

        info("--- Son %d packet ---", show);
        for (int i = skip; i < batch.size(); i++) info(batch.get(i));
    }
}
