package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AntiVanish
 *
 * Looks for players whose visibility does not add up:
 *  - nearby entity: a player entity is close to you but missing from the tab list,
 *  - tab removal: a player vanished from the tab list while their entity is still in view,
 *  - gamemode watch: somebody is listed as spectator.
 * Servers with NPC plugins can trigger the first one, raise confirm-ticks if that happens.
 */
public class AntiVanish extends Module {
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup detection = settings.createGroup("Detection");
    private final SettingGroup alerts = settings.createGroup("Alerts");

    private final Setting<Integer> range = general.add(new IntSetting.Builder()
        .name("range")
        .description("Max distance in blocks for the nearby-entity check.")
        .defaultValue(64).min(8).max(256).sliderMax(256).build());

    private final Setting<Integer> confirmTicks = detection.add(new IntSetting.Builder()
        .name("confirm-ticks")
        .description("Ticks a player must stay missing from the tab list before an alert.")
        .defaultValue(12).min(1).max(60).sliderMax(60).build());

    private final Setting<Boolean> nearbyEntity = detection.add(new BoolSetting.Builder()
        .name("nearby-entity").defaultValue(true).build());

    private final Setting<Boolean> tabRemoval = detection.add(new BoolSetting.Builder()
        .name("tab-removal").defaultValue(true).build());

    private final Setting<Boolean> gamemodeWatch = detection.add(new BoolSetting.Builder()
        .name("gamemode-watch").defaultValue(true).build());

    private final Setting<Boolean> notify = alerts.add(new BoolSetting.Builder()
        .name("notify").description("Chat message on alert.").defaultValue(true).build());

    private final Setting<Boolean> esp = alerts.add(new BoolSetting.Builder()
        .name("esp").description("Box around flagged players for 10 seconds.").defaultValue(true).build());

    private final Setting<SettingColor> alertColor = alerts.add(new ColorSetting.Builder()
        .name("color").defaultValue(new SettingColor(255, 60, 60, 255)).build());

    private final Map<UUID, String> known = new HashMap<>();
    private final Map<UUID, Integer> missingTicks = new HashMap<>();
    private final Map<UUID, Long> alertedAt = new HashMap<>();
    private final Map<UUID, Long> flagged = new HashMap<>();
    private Set<UUID> listedLast = new HashSet<>();

    public AntiVanish() {
        super(MazaCategory.INSTANCE, "anti-vanish", "Detects suspicious player visibility and tab-list inconsistencies.");
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
        known.clear();
        missingTicks.clear();
        alertedAt.clear();
        flagged.clear();
        listedLast = new HashSet<>();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null) return;

        UUID self = mc.player.getUuid();
        Set<UUID> listed = new HashSet<>();

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            UUID id = entry.getProfile().id();
            String name = entry.getProfile().name();

            listed.add(id);
            if (name != null) known.put(id, name);

            if (gamemodeWatch.get() && !id.equals(self) && entry.getGameMode() == GameMode.SPECTATOR) {
                alert(id, nameOf(id), "listed as spectator");
            }
        }

        if (tabRemoval.get()) {
            for (UUID id : listedLast) {
                if (id.equals(self) || listed.contains(id)) continue;
                if (mc.world.getPlayerByUuid(id) != null) {
                    alert(id, nameOf(id), "removed from tab list while still in view");
                }
            }
        }

        double limit = range.get();
        double limitSq = limit * limit;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            UUID id = player.getUuid();
            known.putIfAbsent(id, player.getName().getString());

            boolean missing = nearbyEntity.get()
                && mc.player.squaredDistanceTo(player) <= limitSq
                && !listed.contains(id);

            if (!missing) {
                missingTicks.remove(id);
                continue;
            }

            int ticks = missingTicks.merge(id, 1, Integer::sum);
            if (ticks >= confirmTicks.get()) {
                alert(id, player.getName().getString(), "nearby entity absent from tab list");
            }
        }

        listedLast = listed;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!esp.get() || mc.world == null || flagged.isEmpty()) return;

        long now = System.currentTimeMillis();
        flagged.values().removeIf(until -> until < now);

        SettingColor line = alertColor.get();
        Color side = new Color(line.r, line.g, line.b, 45);

        for (UUID id : flagged.keySet()) {
            PlayerEntity player = mc.world.getPlayerByUuid(id);
            if (player == null) continue;

            Box b = player.getBoundingBox();
            event.renderer.box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, side, line, ShapeMode.Both, 0);
        }
    }

    private String nameOf(UUID id) {
        String name = known.get(id);
        return name != null ? name : id.toString();
    }

    private void alert(UUID id, String name, String reason) {
        long now = System.currentTimeMillis();
        Long last = alertedAt.get(id);
        if (last != null && now - last < 5000L) return;

        alertedAt.put(id, now);
        flagged.put(id, now + 10_000L);

        if (notify.get()) info("Possible vanished player: %s (%s)", name, reason);
    }
}
