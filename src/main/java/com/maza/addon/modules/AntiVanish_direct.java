package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingColor;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AntiVanish extends Module {
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup detection = settings.createGroup("Detection");
    private final SettingGroup alerts = settings.createGroup("Alerts");
    private final SettingGroup staff = settings.createGroup("Staff List");

    private final Setting<Boolean> enabled = general.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Enable staff vanish detection.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> range = detection.add(new IntSetting.Builder()
        .name("range")
        .defaultValue(128)
        .min(8)
        .max(256)
        .sliderMax(256)
        .build());

    private final Setting<Integer> confirmTicks = detection.add(new IntSetting.Builder()
        .name("confirm-ticks")
        .defaultValue(12)
        .min(1)
        .max(60)
        .sliderMax(60)
        .build());

    private final Setting<Boolean> tabCheck = detection.add(new BoolSetting.Builder()
        .name("tab-check")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> spectatorCheck = detection.add(new BoolSetting.Builder()
        .name("spectator-check")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> chatAlert = alerts.add(new BoolSetting.Builder()
        .name("chat-alert")
        .description("Send a large uppercase alert to chat.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> screenAlert = alerts.add(new BoolSetting.Builder()
        .name("screen-alert")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> alertColor = alerts.add(new ColorSetting.Builder()
        .name("alert-color")
        .defaultValue(new SettingColor(255, 60, 60, 255))
        .build());

    /*
     * Editable staff list.
     * Add/remove names directly from the module setting.
     */
    private final Setting<java.util.List<String>> staffList = staff.add(
        new StringListSetting.Builder()
            .name("staff-list")
            .description("Names that should be treated as staff.")
            .defaultValue(
                "fluffymaster07",
                "archivepedro",
                "munkerlich",
                "frenk_btw",
                "napooo_",
                "auzzitech",
                "cryptodaveyt",
                "w1zox_",
                "zeef69",
                "showered",
                "captainmoose35",
                "bobisfound",
                "noahvdaa",
                "0gsummer",
                "lzouzmp5",
                "pastagamer08",
                "u_vv",
                "owen1212055",
                "splaterd",
                "fallerfly"
            )
            .build()
    );

    private final Map<UUID, String> knownNames = new HashMap<>();
    private final Map<UUID, Integer> missingTicks = new HashMap<>();
    private final Map<UUID, Long> lastAlert = new HashMap<>();
    private final Map<UUID, Boolean> spectatorState = new HashMap<>();

    public AntiVanish() {
        super(
            MazaCategory.INSTANCE,
            "anti-vanish",
            "Detects configured staff disappearing from TAB or entering spectator."
        );
    }

    @Override
    public void onActivate() {
        clearState();
    }

    @Override
    public void onDeactivate() {
        clearState();
    }

    private void clearState() {
        knownNames.clear();
        missingTicks.clear();
        lastAlert.clear();
        spectatorState.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!enabled.get()) return;

        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null) {
            clearState();
            return;
        }

        Map<UUID, PlayerListEntry> tab = new HashMap<>();

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry == null || entry.getProfile() == null) continue;

            UUID id = entry.getProfile().getId();
            String name = entry.getProfile().getName();

            if (id == null || name == null || name.isBlank()) continue;

            knownNames.put(id, name);
            tab.put(id, entry);

            if (spectatorCheck.get() && isStaff(name)) {
                boolean spectator = entry.getGameMode() != null
                    && entry.getGameMode().isSpectator();

                Boolean old = spectatorState.put(id, spectator);

                if (spectator && (old == null || !old)) {
                    alert(name, "SPECTATOR");
                }
            }
        }

        if (tabCheck.get()) {
            checkNearbyStaff(tab);
        }
    }

    private void checkNearbyStaff(Map<UUID, PlayerListEntry> tab) {
        double maxDistanceSq = range.get() * (double) range.get();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null || player == mc.player) continue;

            UUID id = player.getUuid();
            String name = player.getName().getString();

            if (id == null || name.isBlank()) continue;
            if (!isStaff(name)) {
                missingTicks.remove(id);
                continue;
            }

            boolean nearby = mc.player.squaredDistanceTo(player) <= maxDistanceSq;
            boolean missingFromTab = !tab.containsKey(id);

            if (nearby && missingFromTab) {
                int ticks = missingTicks.merge(id, 1, Integer::sum);

                if (ticks >= confirmTicks.get()) {
                    alert(name, "VANISHED FROM TAB");
                }
            } else {
                missingTicks.remove(id);
            }
        }
    }

    private boolean isStaff(String name) {
        String target = name.toLowerCase(Locale.ROOT).trim();

        for (String staffName : staffList.get()) {
            if (staffName != null
                && target.equals(staffName.toLowerCase(Locale.ROOT).trim())) {
                return true;
            }
        }

        return false;
    }

    private void alert(String name, String reason) {
        long now = System.currentTimeMillis();

        UUID id = mc.player != null ? mc.player.getUuid() : null;

        /*
         * Per-name cooldown prevents one staff member from spamming chat.
         */
        UUID alertId = UUID.nameUUIDFromBytes(
            name.toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        if (now - lastAlert.getOrDefault(alertId, 0L) < 5000L) return;
        lastAlert.put(alertId, now);

        String message =
            "§c§l⚠ STAFF ALERT ⚠ §r§c"
            + name.toUpperCase(Locale.ROOT)
            + " §7| §c"
            + reason;

        if (chatAlert.get() && mc.player != null) {
            mc.player.sendMessage(
                net.minecraft.text.Text.literal(message),
                false
            );
        }

        if (screenAlert.get()) {
            info("§c§lSTAFF ALERT: §f" + name + " §c" + reason);
        }
    }
}
