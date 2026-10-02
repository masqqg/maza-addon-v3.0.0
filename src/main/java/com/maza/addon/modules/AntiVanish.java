package com.maza.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import com.maza.addon.MazaCategory;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class AntiVanish extends Module {
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup detection = settings.createGroup("Detection");
    private final SettingGroup alerts = settings.createGroup("Alerts");

    private final Setting<Integer> range = general.add(new IntSetting.Builder().name("range").defaultValue(64).min(8).max(256).sliderMax(256).build());
    private final Setting<Integer> confirmTicks = detection.add(new IntSetting.Builder().name("confirm-ticks").defaultValue(12).min(1).max(60).sliderMax(60).build());
    private final Setting<Boolean> nearbyEntity = detection.add(new BoolSetting.Builder().name("nearby-entity").defaultValue(true).build());
    private final Setting<Boolean> tabRemoval = detection.add(new BoolSetting.Builder().name("tab-removal").defaultValue(true).build());
    private final Setting<Boolean> gamemodeWatch = detection.add(new BoolSetting.Builder().name("gamemode-watch").defaultValue(true).build());
    private final Setting<Boolean> notify = alerts.add(new BoolSetting.Builder().name("notify").defaultValue(true).build());
    private final Setting<SettingColor> color = alerts.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(255, 70, 70, 255)).build());

    private final Map<UUID, String> known = new HashMap<>();
    private final Map<UUID, Integer> missingTicks = new HashMap<>();
    private final Map<UUID, Long> alertsSent = new HashMap<>();

    public AntiVanish() { super(MazaCategory.INSTANCE, "anti-vanish", "Detects suspicious player visibility/tab-list inconsistencies."); }

    @Override public void onActivate() { known.clear(); missingTicks.clear(); alertsSent.clear(); }
    @Override public void onDeactivate() { known.clear(); missingTicks.clear(); alertsSent.clear(); }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null) return;
        Set<UUID> listed = new HashSet<>();
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            listed.add(e.getProfile().getId());
            if (e.getProfile().getName() != null) known.put(e.getProfile().getId(), e.getProfile().getName());
        }

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.getUuid() == null) continue;
            known.putIfAbsent(p.getUuid(), p.getName().getString());
            if (nearbyEntity.get() && mc.player.squaredDistanceTo(p) <= range.get() * range.get() && !listed.contains(p.getUuid())) {
                int n = missingTicks.merge(p.getUuid(), 1, Integer::sum);
                if (n >= confirmTicks.get()) alert(p.getUuid(), p.getName().getString(), "nearby entity absent from tab list");
            } else {
                missingTicks.remove(p.getUuid());
            }
        }
    }

    private void alert(UUID id, String name, String reason) {
        long now = System.currentTimeMillis();
        if (now - alertsSent.getOrDefault(id, 0L) < 5000) return;
        alertsSent.put(id, now);
        if (notify.get()) info("Possible vanished player: " + name + " (" + reason + ")");
    }
}
