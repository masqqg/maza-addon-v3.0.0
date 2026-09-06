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
    private final SettingGroup sgOutput = settings.createGroup("Output");

    private final Setting<Integer> flushInterval = sgGeneral.add(new IntSetting.Builder()
        .name("flush-interval").description("Chat'e yazma aralığı (tick, 20=1sn)")
        .defaultValue(40).min(5).max(200).sliderRange(5, 200).build());

    private final Setting<Integer> maxLines = sgGeneral.add(new IntSetting.Builder()
        .name("max-lines").description("Her seferinde max kaç satır göster")
        .defaultValue(15).min(1).max(50).sliderRange(1, 50).build());

    private final Setting<Boolean> showStats = sgOutput.add(new BoolSetting.Builder()
        .name("show-stats").description("Kapatınca toplam sayıyı yaz").defaultValue(true).build());

    private int tickCounter = 0;

    public PacketSniffer() {
        super(MazaCategory.INSTANCE, "packet-sniffer", "Tüm sunucu packetlerini loglar (toString ile).");
    }

    @Override
    public void onActivate() {
        PacketLogger.active = true;
        PacketLogger.clear();
        tickCounter = 0;
        info("Packet sniffer aktif. Tüm packetler loglanıyor.");
    }

    @Override
    public void onDeactivate() {
        PacketLogger.active = false;
        if (showStats.get()) {
            info("Toplam %d packet yakalandı.", PacketLogger.totalPackets);
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
