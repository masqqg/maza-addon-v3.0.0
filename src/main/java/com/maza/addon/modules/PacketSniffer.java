package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import com.maza.addon.utils.PacketLogger;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.util.Arrays;
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

    // PACKET FİLTRELERİ
    private final Setting<Boolean> fChunkData = sgFilters.add(new BoolSetting.Builder()
        .name("chunk-data").description("Chunk veri packetleri").defaultValue(true).build());
    private final Setting<Boolean> fChunkDelta = sgFilters.add(new BoolSetting.Builder()
        .name("chunk-delta").description("Toplu blok güncellemeleri").defaultValue(true).build());
    private final Setting<Boolean> fBlockUpdate = sgFilters.add(new BoolSetting.Builder()
        .name("block-update").description("Tek blok değişimi").defaultValue(true).build());
    private final Setting<Boolean> fEntitySpawn = sgFilters.add(new BoolSetting.Builder()
        .name("entity-spawn").description("Yeni entity spawn").defaultValue(true).build());
    private final Setting<Boolean> fEntityPos = sgFilters.add(new BoolSetting.Builder()
        .name("entity-pos").description("Entity hareket").defaultValue(true).build());
    private final Setting<Boolean> fEntityLook = sgFilters.add(new BoolSetting.Builder()
        .name("entity-look").description("Entity bakış yönü").defaultValue(true).build());
    private final Setting<Boolean> fEntityLookPos = sgFilters.add(new BoolSetting.Builder()
        .name("entity-lookpos").description("Entity hem hareket hem bakış").defaultValue(true).build());
    private final Setting<Boolean> fEntityVel = sgFilters.add(new BoolSetting.Builder()
        .name("entity-vel").description("Entity hızı").defaultValue(false).build());
    private final Setting<Boolean> fEntityAnim = sgFilters.add(new BoolSetting.Builder()
        .name("entity-anim").description("Entity animasyonu").defaultValue(false).build());
    private final Setting<Boolean> fEntityStatus = sgFilters.add(new BoolSetting.Builder()
        .name("entity-status").description("Entity durumu (hasar/ölüm)").defaultValue(false).build());
    private final Setting<Boolean> fEntityEquip = sgFilters.add(new BoolSetting.Builder()
        .name("entity-equip").description("Entity ekipman değişimi").defaultValue(false).build());
    private final Setting<Boolean> fEntityTracker = sgFilters.add(new BoolSetting.Builder()
        .name("entity-tracker").description("Entity metadata").defaultValue(false).build());
    private final Setting<Boolean> fEntityDestroy = sgFilters.add(new BoolSetting.Builder()
        .name("entity-destroy").description("Entity yok edildi").defaultValue(true).build());
    private final Setting<Boolean> fPlayerTeleport = sgFilters.add(new BoolSetting.Builder()
        .name("player-teleport").description("Sen teleport edildin").defaultValue(true).build());
    private final Setting<Boolean> fDisconnect = sgFilters.add(new BoolSetting.Builder()
        .name("disconnect").description("Bağlantı kesildi").defaultValue(true).build());
    private final Setting<Boolean> fTitle = sgFilters.add(new BoolSetting.Builder()
        .name("title").description("Ekran başlığı").defaultValue(false).build());
    private final Setting<Boolean> fSubtitle = sgFilters.add(new BoolSetting.Builder()
        .name("subtitle").description("Alt başlık").defaultValue(false).build());
    private final Setting<Boolean> fActionbar = sgFilters.add(new BoolSetting.Builder()
        .name("actionbar").description("Action bar mesajı").defaultValue(false).build());
    private final Setting<Boolean> fWorldTime = sgFilters.add(new BoolSetting.Builder()
        .name("world-time").description("Dünya zamanı").defaultValue(false).build());
    private final Setting<Boolean> fHealth = sgFilters.add(new BoolSetting.Builder()
        .name("health").description("Can/açlık güncellemesi").defaultValue(false).build());
    private final Setting<Boolean> fXP = sgFilters.add(new BoolSetting.Builder()
        .name("xp").description("Deneyim güncellemesi").defaultValue(false).build());
    private final Setting<Boolean> fExplosion = sgFilters.add(new BoolSetting.Builder()
        .name("explosion").description("Patlama").defaultValue(true).build());
    private final Setting<Boolean> fSound = sgFilters.add(new BoolSetting.Builder()
        .name("sound").description("Ses efekti").defaultValue(false).build());
    private final Setting<Boolean> fParticle = sgFilters.add(new BoolSetting.Builder()
        .name("particle").description("Parçacık efekti").defaultValue(false).build());
    private final Setting<Boolean> fKeepAlive = sgFilters.add(new BoolSetting.Builder()
        .name("keep-alive").description("Bağlantı kontrolü").defaultValue(false).build());
    private final Setting<Boolean> fUnloadChunk = sgFilters.add(new BoolSetting.Builder()
        .name("unload-chunk").description("Chunk boşaltıldı").defaultValue(true).build());
    private final Setting<Boolean> fLightData = sgFilters.add(new BoolSetting.Builder()
        .name("light-data").description("Işık verisi").defaultValue(false).build());
    private final Setting<Boolean> fBlockEntity = sgFilters.add(new BoolSetting.Builder()
        .name("block-entity").description("Chest/spawner vb.").defaultValue(true).build());
    private final Setting<Boolean> fContainerSlot = sgFilters.add(new BoolSetting.Builder()
        .name("container-slot").description("Envanter slot değişimi").defaultValue(false).build());
    private final Setting<Boolean> fOpenScreen = sgFilters.add(new BoolSetting.Builder()
        .name("open-screen").description("Menü açıldı").defaultValue(false).build());
    private final Setting<Boolean> fCloseScreen = sgFilters.add(new BoolSetting.Builder()
        .name("close-screen").description("Menü kapandı").defaultValue(false).build());
    private final Setting<Boolean> fContainerContent = sgFilters.add(new BoolSetting.Builder()
        .name("container-content").description("Envanter içeriği").defaultValue(false).build());

    private final Setting<Boolean> showStats = sgOutput.add(new BoolSetting.Builder()
        .name("show-stats").description("Kapatınca toplam sayıyı yaz").defaultValue(true).build());

    private int tickCounter = 0;

    public PacketSniffer() {
        super(MazaCategory.INSTANCE, "packet-sniffer", "Ayarlanabilir server packet sniffer");
    }

    @Override
    public void onActivate() {
        PacketLogger.active = true;
        PacketLogger.clear();
        updateFilters();
        tickCounter = 0;
        info("Packet sniffer aktif - seçili packet türleri loglanıyor");
    }

    @Override
    public void onDeactivate() {
        PacketLogger.active = false;
        if (showStats.get()) {
            info("Toplam %d packet yakalandı", PacketLogger.totalPackets);
        }
    }

    // Ayarlar değişince filtreleri güncelle
    @Override
    public void onSettingChanged(Setting<?> setting) {
        if (isActive()) updateFilters();
    }

    private void updateFilters() {
        PacketLogger.enabledTypes.clear();
        if (fChunkData.get()) PacketLogger.enabledTypes.add("CHUNK_DATA");
        if (fChunkDelta.get()) PacketLogger.enabledTypes.add("CHUNK_DELTA");
        if (fBlockUpdate.get()) PacketLogger.enabledTypes.add("BLOCK_UPDATE");
        if (fEntitySpawn.get()) PacketLogger.enabledTypes.add("ENTITY_SPAWN");
        if (fEntityPos.get()) PacketLogger.enabledTypes.add("ENTITY_POS");
        if (fEntityLook.get()) PacketLogger.enabledTypes.add("ENTITY_LOOK");
        if (fEntityLookPos.get()) PacketLogger.enabledTypes.add("ENTITY_LOOKPOS");
        if (fEntityVel.get()) PacketLogger.enabledTypes.add("ENTITY_VEL");
        if (fEntityAnim.get()) PacketLogger.enabledTypes.add("ENTITY_ANIM");
        if (fEntityStatus.get()) PacketLogger.enabledTypes.add("ENTITY_STATUS");
        if (fEntityEquip.get()) PacketLogger.enabledTypes.add("ENTITY_EQUIP");
        if (fEntityTracker.get()) PacketLogger.enabledTypes.add("ENTITY_TRACKER");
        if (fEntityDestroy.get()) PacketLogger.enabledTypes.add("ENTITY_DESTROY");
        if (fPlayerTeleport.get()) PacketLogger.enabledTypes.add("PLAYER_TELEPORT");
        if (fDisconnect.get()) PacketLogger.enabledTypes.add("DISCONNECT");
        if (fTitle.get()) PacketLogger.enabledTypes.add("TITLE");
        if (fSubtitle.get()) PacketLogger.enabledTypes.add("SUBTITLE");
        if (fActionbar.get()) PacketLogger.enabledTypes.add("ACTIONBAR");
        if (fWorldTime.get()) PacketLogger.enabledTypes.add("WORLD_TIME");
        if (fHealth.get()) PacketLogger.enabledTypes.add("HEALTH");
        if (fXP.get()) PacketLogger.enabledTypes.add("XP");
        if (fExplosion.get()) PacketLogger.enabledTypes.add("EXPLOSION");
        if (fSound.get()) PacketLogger.enabledTypes.add("SOUND");
        if (fParticle.get()) PacketLogger.enabledTypes.add("PARTICLE");
        if (fKeepAlive.get()) PacketLogger.enabledTypes.add("KEEP_ALIVE");
        if (fUnloadChunk.get()) PacketLogger.enabledTypes.add("UNLOAD_CHUNK");
        if (fLightData.get()) PacketLogger.enabledTypes.add("LIGHT_DATA");
        if (fBlockEntity.get()) PacketLogger.enabledTypes.add("BLOCK_ENTITY");
        if (fContainerSlot.get()) PacketLogger.enabledTypes.add("CONTAINER_SLOT");
        if (fOpenScreen.get()) PacketLogger.enabledTypes.add("OPEN_SCREEN");
        if (fCloseScreen.get()) PacketLogger.enabledTypes.add("CLOSE_SCREEN");
        if (fContainerContent.get()) PacketLogger.enabledTypes.add("CONTAINER_CONTENT");
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
