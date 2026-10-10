package com.maza.addon.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
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
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AntiVanish
 *
 * Reports client-observable signals that a player is hidden from the tab list. No single
 * signal proves it, so every signal carries a weight and the weights of one player add up
 * inside a time window. An alert fires when the sum reaches the minimum score.
 *
 *  spawn UUID      3  A player entity spawn packet arrives whose UUID is not in TAB. The spawn
 *                     packet carries the UUID of every player that comes into range.
 *  tab removal     3  A player leaves TAB while their entity is still in view.
 *  nearby absent   3  A player entity close to you has been missing from TAB for a while.
 *  unlisted        3  A PlayerList packet switches a profile to unlisted: hidden from TAB but still
 *                     known to the client (read from the packet, not from polling).
 *  flicker         2  A player entity spawns and is destroyed again within a second or two while
 *                     it is not in TAB: somebody toggling vanish next to you.
 *  spectator       2  Listed in TAB as a spectator (TAB poll and PlayerList packets).
 *  invisible       1  A nearby player entity is invisible (off by default).
 *  lag             1  A TAB removal followed by a client tick stall.
 *  light + data    1  A TAB removal together with a lag, a nearby light update and a nearby
 *                     entity metadata update.
 *
 * Servers with NPC plugins send player entities that are not in TAB on purpose, those give
 * false alarms on the first three methods.
 */
public class AntiVanish extends Module {
    private static final int W_SPAWN = 3;
    private static final int W_REMOVED = 3;
    private static final int W_ABSENT = 3;
    private static final int W_SPECTATOR = 2;
    private static final int W_UNLISTED = 3;
    private static final int W_FLICKER = 2;
    private static final int W_INVISIBLE = 1;
    private static final int W_LAG = 1;
    private static final int W_CORRELATED = 1;

    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup detection = settings.createGroup("Detection");
    private final SettingGroup scoring = settings.createGroup("Scoring");
    private final SettingGroup alerts = settings.createGroup("Alerts");

    private final Setting<Integer> range = general.add(new IntSetting.Builder()
        .name("range").description("Max distance for nearby-entity checks.")
        .defaultValue(64).min(8).max(256).sliderMax(256).build());

    private final Setting<Boolean> spawnUuid = detection.add(new BoolSetting.Builder()
        .name("spawn-uuid")
        .description("The UUID in a player entity spawn packet is not in TAB.")
        .defaultValue(true).build());

    private final Setting<Integer> spawnDelayTicks = detection.add(new IntSetting.Builder()
        .name("spawn-delay-ticks")
        .description("Wait this long before judging a spawn, the TAB entry may arrive a moment later.")
        .defaultValue(20).min(1).max(100).sliderMax(100).build());

    private final Setting<Integer> confirmTicks = detection.add(new IntSetting.Builder()
        .name("confirm-ticks").description("Ticks an entity must be missing from TAB before it counts.")
        .defaultValue(12).min(1).max(60).sliderMax(60).build());

    private final Setting<Boolean> listWatch = detection.add(new BoolSetting.Builder()
        .name("list-packets")
        .description("Read PlayerList and PlayerRemove packets the moment they arrive: unlisted profiles, spectator switches, TAB removals.")
        .defaultValue(true).build());

    private final Setting<Boolean> flickerWatch = detection.add(new BoolSetting.Builder()
        .name("flicker")
        .description("A player entity that spawns and is destroyed again quickly while it is not in TAB.")
        .defaultValue(true).build());

    private final Setting<Integer> flickerMs = detection.add(new IntSetting.Builder()
        .name("flicker-ms").description("Longest life of a flickering entity.")
        .defaultValue(1500).min(200).max(10000).sliderMin(200).sliderMax(10000).build());

    private final Setting<Boolean> nearbyEntity = detection.add(new BoolSetting.Builder()
        .name("nearby-entity").defaultValue(true).build());
    private final Setting<Boolean> tabRemoval = detection.add(new BoolSetting.Builder()
        .name("tab-removal").defaultValue(true).build());
    private final Setting<Boolean> gamemodeWatch = detection.add(new BoolSetting.Builder()
        .name("gamemode-watch").defaultValue(true).build());
    private final Setting<Boolean> invisibleWatch = detection.add(new BoolSetting.Builder()
        .name("invisible-watch").description("Nearby invisible player entities. Potions make these too.")
        .defaultValue(false).build());
    private final Setting<Boolean> tabLagCorrelation = detection.add(new BoolSetting.Builder()
        .name("tab-lag-correlation").description("Correlates a TAB removal with a client tick stall within 10/20 seconds.")
        .defaultValue(true).build());
    private final Setting<Integer> lagThresholdMs = detection.add(new IntSetting.Builder()
        .name("lag-threshold-ms").description("Tick gap treated as a possible lag spike; this is not a server-lag measurement.")
        .defaultValue(500).min(100).max(5000).sliderMax(2000).build());
    private final Setting<Boolean> entityDataWatch = detection.add(new BoolSetting.Builder()
        .name("entity-data-watch").description("Tracks metadata updates for entities within 6 blocks.")
        .defaultValue(true).build());
    private final Setting<Boolean> lightDataWatch = detection.add(new BoolSetting.Builder()
        .name("light-data-watch").description("Tracks light-update packets for nearby chunks; packet data does not identify an exact block volume.")
        .defaultValue(true).build());
    private final Setting<Integer> signalWindowSeconds = detection.add(new IntSetting.Builder()
        .name("signal-window-seconds").description("How long light/entity signals remain relevant after a TAB removal.")
        .defaultValue(20).min(5).max(30).sliderMax(30).build());

    private final Setting<Integer> minScore = scoring.add(new IntSetting.Builder()
        .name("min-score")
        .description("Weights of one player that have to add up before an alert. 3 = one strong signal or three weak ones.")
        .defaultValue(3).min(1).max(12).sliderMin(1).sliderMax(12).build());

    private final Setting<Integer> holdSeconds = scoring.add(new IntSetting.Builder()
        .name("score-window-seconds").description("Signals older than this no longer count.")
        .defaultValue(60).min(10).max(600).sliderMin(10).sliderMax(600).build());

    private final Setting<Boolean> notify = alerts.add(new BoolSetting.Builder()
        .name("notify").description("Chat message on alert.").defaultValue(true).build());
    private final Setting<Boolean> esp = alerts.add(new BoolSetting.Builder()
        .name("esp")
        .description("Box around flagged players for 10 seconds. A player who is not in view is boxed where the spawn packet put them.")
        .defaultValue(true).build());
    private final Setting<Boolean> resolveNames = alerts.add(new BoolSetting.Builder()
        .name("resolve-names")
        .description("Look up the name of an unknown UUID at Mojang's session server, one request every 2 seconds. This sends the UUID to Mojang.")
        .defaultValue(false).build());

    private final Setting<SettingColor> alertColor = alerts.add(new ColorSetting.Builder()
        .name("color").defaultValue(new SettingColor(255, 60, 60, 255)).build());

    private record Signal(long at, int weight, String reason) {}
    private record Spawn(int entityId, double x, double y, double z, long at) {}
    private record Spawned(UUID uuid, long at) {}

    // Only touched on the client thread.
    private final Map<UUID, String> known = new HashMap<>();
    private final Map<UUID, Integer> missingTicks = new HashMap<>();
    private final Map<UUID, Long> alertedAt = new HashMap<>();
    private final Map<UUID, Long> flagged = new HashMap<>();
    private final Map<UUID, Long> recentTabRemovals = new HashMap<>();
    private final Map<UUID, List<Signal>> signals = new HashMap<>();
    private final Map<UUID, Integer> pendingSpawns = new HashMap<>();   // uuid -> tick it is judged
    private final Map<UUID, Spawn> spawns = new HashMap<>();            // last spawn packet per uuid
    private final Map<Integer, Spawned> spawnedById = new HashMap<>();  // entity id -> who spawned, for flicker
    private final Map<UUID, String> resolved = new ConcurrentHashMap<>(); // names found at Mojang
    private final Set<UUID> lookedUp = new HashSet<>();
    private HttpClient http;
    private long lastLookupAt;
    private Set<UUID> listedLast = new HashSet<>();
    private int tick;
    private long lastTickAt;
    private long lastLagAt;
    private long lastNearbyLightAt;
    private long lastNearbyEntityDataAt;
    private long lastSignalAlertAt;

    public AntiVanish() {
        super(MazaCategory.INSTANCE, "anti-vanish", "Flags possible vanish-related signals; signals are not proof of staff presence.");
    }

    @Override
    public void onActivate() {
        clearAll();
    }

    @Override
    public void onDeactivate() {
        clearAll();
    }

    @Override
    public String getInfoString() {
        return flagged.isEmpty() ? null : String.valueOf(flagged.size());
    }

    private void clearAll() {
        known.clear();
        missingTicks.clear();
        alertedAt.clear();
        flagged.clear();
        recentTabRemovals.clear();
        signals.clear();
        pendingSpawns.clear();
        spawns.clear();
        spawnedById.clear();
        resolved.clear();
        lookedUp.clear();
        lastLookupAt = 0L;
        listedLast = new HashSet<>();
        lastTickAt = 0L;
        lastLagAt = 0L;
        lastNearbyLightAt = 0L;
        lastNearbyEntityDataAt = 0L;
        lastSignalAlertAt = 0L;
    }

    // ------------------------------------------------------------------- tick

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null) return;

        tick++;
        long now = System.currentTimeMillis();

        if (lastTickAt != 0L) {
            long gap = now - lastTickAt;
            if (gap >= lagThresholdMs.get()) {
                lastLagAt = now;
                if (tabLagCorrelation.get()) {
                    for (Map.Entry<UUID, Long> removal : recentTabRemovals.entrySet()) {
                        long sinceRemoval = now - removal.getValue();
                        if (sinceRemoval >= 0L && sinceRemoval <= 10_000L) {
                            signal(removal.getKey(), W_LAG, "TAB removal followed by a client tick stall within 10s (" + gap + "ms)");
                        } else if (sinceRemoval > 10_000L && sinceRemoval <= 20_000L) {
                            signal(removal.getKey(), W_LAG, "TAB removal followed by a client tick stall within 20s (" + gap + "ms)");
                        }
                    }
                }
            }
        }
        lastTickAt = now;

        UUID self = mc.player.getUuid();
        Set<UUID> listed = new HashSet<>();
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            UUID id = entry.getProfile().id();
            String name = entry.getProfile().name();
            listed.add(id);
            if (name != null) known.put(id, name);
            if (gamemodeWatch.get() && !id.equals(self) && entry.getGameMode() == GameMode.SPECTATOR) {
                signal(id, W_SPECTATOR, "listed as spectator");
            }
        }

        if (tabRemoval.get()) {
            for (UUID id : listedLast) {
                if (id.equals(self) || listed.contains(id)) continue;
                recentTabRemovals.put(id, now);
                if (mc.world.getPlayerByUuid(id) != null) {
                    signal(id, W_REMOVED, "removed from TAB while the entity is still present");
                } else {
                    maybeCorrelatedSignal(id, now);
                }
            }
        }

        recentTabRemovals.entrySet().removeIf(entry -> now - entry.getValue() > 30_000L);
        if (tick % 200 == 0) spawnedById.values().removeIf(spawned -> now - spawned.at() > 60_000L);

        judgeSpawns(listed);

        double limitSq = (double) range.get() * range.get();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            UUID id = player.getUuid();
            known.putIfAbsent(id, player.getName().getString());

            if (invisibleWatch.get() && player.isInvisible() && mc.player.squaredDistanceTo(player) <= limitSq) {
                signal(id, W_INVISIBLE, "nearby player entity is invisible");
            }

            boolean missing = nearbyEntity.get()
                && mc.player.squaredDistanceTo(player) <= limitSq
                && !listed.contains(id);
            if (!missing) {
                missingTicks.remove(id);
                continue;
            }

            int ticks = missingTicks.merge(id, 1, Integer::sum);
            if (ticks >= confirmTicks.get()) {
                signal(id, W_ABSENT, "nearby player entity absent from TAB");
            }
        }
        listedLast = listed;
    }

    /** Spawns whose waiting time is over: is the UUID of the spawn packet in TAB by now? */
    private void judgeSpawns(Set<UUID> listed) {
        if (pendingSpawns.isEmpty()) return;

        List<UUID> due = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : pendingSpawns.entrySet()) {
            if (entry.getValue() <= tick) due.add(entry.getKey());
        }

        for (UUID id : due) {
            pendingSpawns.remove(id);
            if (listed.contains(id) || id.equals(mc.player.getUuid())) continue;

            maybeResolve(id);

            Spawn spawn = spawns.get(id);
            String where = spawn == null ? "" : String.format(" at %.0f, %.0f, %.0f", spawn.x(), spawn.y(), spawn.z());
            signal(id, W_SPAWN, "spawn packet UUID is not in TAB" + where);
        }
    }

    // ---------------------------------------------------------------- packets

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        // Network thread: pull the plain values out here, every map is only used on the client thread.
        if (spawnUuid.get() && event.packet instanceof EntitySpawnS2CPacket spawn
            && spawn.getEntityType() == EntityType.PLAYER) {
            UUID id = spawn.getUuid();
            int entityId = spawn.getEntityId();
            double x = spawn.getX();
            double y = spawn.getY();
            double z = spawn.getZ();
            mc.execute(() -> handleSpawn(id, entityId, x, y, z));
            return;
        }

        if (lightDataWatch.get() && event.packet instanceof LightUpdateS2CPacket light) {
            int cx = light.getChunkX();
            int cz = light.getChunkZ();
            mc.execute(() -> handleLight(cx, cz));
            return;
        }

        if (listWatch.get() && event.packet instanceof PlayerRemoveS2CPacket remove) {
            List<UUID> ids = new ArrayList<>(remove.profileIds());
            mc.execute(() -> handleRemoved(ids));
            return;
        }

        if (listWatch.get() && event.packet instanceof PlayerListS2CPacket list) {
            boolean unlistAction = list.getActions().contains(PlayerListS2CPacket.Action.UPDATE_LISTED);
            boolean modeAction = list.getActions().contains(PlayerListS2CPacket.Action.UPDATE_GAME_MODE);

            List<UUID> unlisted = new ArrayList<>();
            List<UUID> spectators = new ArrayList<>();
            for (PlayerListS2CPacket.Entry entry : list.getEntries()) {
                if (unlistAction && !entry.listed()) unlisted.add(entry.profileId());
                if (modeAction && entry.gameMode() == GameMode.SPECTATOR) spectators.add(entry.profileId());
            }

            if (!unlisted.isEmpty() || !spectators.isEmpty()) mc.execute(() -> handleListPacket(unlisted, spectators));
            return;
        }

        if (flickerWatch.get() && event.packet instanceof EntitiesDestroyS2CPacket destroy) {
            List<Integer> ids = new ArrayList<>();
            for (int entityId : destroy.getEntityIds()) ids.add(entityId);
            mc.execute(() -> handleDestroyed(ids));
            return;
        }

        if (entityDataWatch.get() && event.packet instanceof EntityTrackerUpdateS2CPacket tracker) {
            int id = tracker.id();
            mc.execute(() -> handleTracker(id));
        }
    }

    private void handleSpawn(UUID id, int entityId, double x, double y, double z) {
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        spawns.put(id, new Spawn(entityId, x, y, z, now));
        spawnedById.put(entityId, new Spawned(id, now));

        // The name may be known from TAB or from the entity itself.
        Entity entity = mc.world.getEntityById(entityId);
        if (entity instanceof PlayerEntity player) known.putIfAbsent(id, player.getName().getString());

        pendingSpawns.put(id, tick + spawnDelayTicks.get());
    }

    /** A PlayerRemove packet: the profile left TAB at this exact moment. */
    private void handleRemoved(List<UUID> ids) {
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        for (UUID id : ids) {
            if (id.equals(mc.player.getUuid())) continue;

            recentTabRemovals.put(id, now);
            // Same wording as the TAB poll, so both ways of seeing it count once.
            if (mc.world.getPlayerByUuid(id) != null) signal(id, W_REMOVED, "removed from TAB while the entity is still present");
        }
    }

    /** A PlayerList packet that unlists a profile or puts it in spectator mode. */
    private void handleListPacket(List<UUID> unlisted, List<UUID> spectators) {
        if (mc.world == null || mc.player == null) return;

        UUID self = mc.player.getUuid();
        for (UUID id : unlisted) {
            if (!id.equals(self)) signal(id, W_UNLISTED, "profile unlisted: hidden from TAB but still known");
        }
        for (UUID id : spectators) {
            if (!id.equals(self) && gamemodeWatch.get()) signal(id, W_SPECTATOR, "listed as spectator");
        }
    }

    /** An entity destroyed soon after it spawned, and its player was never in TAB. */
    private void handleDestroyed(List<Integer> entityIds) {
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        for (int entityId : entityIds) {
            Spawned spawned = spawnedById.remove(entityId);
            if (spawned == null) continue;

            long life = now - spawned.at();
            if (life > flickerMs.get() || listedLast.contains(spawned.uuid())) continue;

            maybeResolve(spawned.uuid());
            signal(spawned.uuid(), W_FLICKER, "player entity appeared and vanished within " + life + " ms, not in TAB");
        }
    }

    private void handleLight(int cx, int cz) {
        if (mc.world == null || mc.player == null) return;

        // Light packets are chunk/section updates, not precise changed-block coordinates.
        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        if (Math.abs(cx - playerChunkX) <= 1 && Math.abs(cz - playerChunkZ) <= 1) {
            long now = System.currentTimeMillis();
            lastNearbyLightAt = now;
            correlateRecentTabRemoval(now, "nearby chunk light-data update");
        }
    }

    private void handleTracker(int entityId) {
        if (mc.world == null || mc.player == null) return;

        Entity entity = mc.world.getEntityById(entityId);
        if (entity != null && entity != mc.player && entity.squaredDistanceTo(mc.player) <= 36.0) {
            long now = System.currentTimeMillis();
            lastNearbyEntityDataAt = now;
            correlateRecentTabRemoval(now, "entity metadata update within 6 blocks");
        }
    }

    private void maybeCorrelatedSignal(UUID id, long now) {
        long window = signalWindowSeconds.get() * 1000L;
        boolean lightRecent = lastNearbyLightAt != 0L && now - lastNearbyLightAt <= window;
        boolean entityRecent = lastNearbyEntityDataAt != 0L && now - lastNearbyEntityDataAt <= window;
        boolean lagRecent = lastLagAt != 0L && now - lastLagAt <= 20_000L;
        if (lightRecent && entityRecent && lagRecent) {
            signal(id, W_CORRELATED, "TAB removal with recent lag, nearby light update and entity metadata");
        }
    }

    private void correlateRecentTabRemoval(long now, String source) {
        if (!tabLagCorrelation.get()) return;
        long window = signalWindowSeconds.get() * 1000L;

        for (Map.Entry<UUID, Long> removal : recentTabRemovals.entrySet()) {
            if (now < removal.getValue() || now - removal.getValue() > window) continue;

            // Ordinary packet traffic alone is nothing; the other signals have to exist as well.
            boolean lightRecent = lastNearbyLightAt != 0L && now - lastNearbyLightAt <= window;
            boolean entityRecent = lastNearbyEntityDataAt != 0L && now - lastNearbyEntityDataAt <= window;
            boolean lagRecent = lastLagAt != 0L && now - lastLagAt <= 20_000L;

            if (lightRecent && entityRecent && lagRecent && now - lastSignalAlertAt > 5000L) {
                lastSignalAlertAt = now;
                signal(removal.getKey(), W_CORRELATED, "TAB removal + lag + nearby light/entity signals (" + source + ")");
            }
        }
    }

    // ----------------------------------------------------------------- render

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!esp.get() || mc.world == null || flagged.isEmpty()) return;

        long now = System.currentTimeMillis();
        flagged.values().removeIf(until -> until < now);

        SettingColor line = alertColor.get();
        Color side = new Color(line.r, line.g, line.b, 45);

        for (UUID id : flagged.keySet()) {
            PlayerEntity player = mc.world.getPlayerByUuid(id);

            if (player != null) {
                Box b = player.getBoundingBox();
                event.renderer.box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, side, line, ShapeMode.Both, 0);
                continue;
            }

            // Not in view: the place the spawn packet put them.
            Spawn spawn = spawns.get(id);
            if (spawn != null) {
                event.renderer.box(spawn.x() - 0.3, spawn.y(), spawn.z() - 0.3, spawn.x() + 0.3, spawn.y() + 1.8, spawn.z() + 0.3,
                    side, line, ShapeMode.Both, 0);
            }
        }
    }

    // ---------------------------------------------------------------- scoring

    private String nameOf(UUID id) {
        String name = known.get(id);
        if (name == null) name = resolved.get(id);
        return name != null ? name : "unknown";
    }

    /** Ask Mojang who a UUID is. One request every two seconds, never twice for the same UUID. */
    private void maybeResolve(UUID id) {
        if (!resolveNames.get() || known.containsKey(id) || resolved.containsKey(id)) return;

        long now = System.currentTimeMillis();
        if (now - lastLookupAt < 2000L || !lookedUp.add(id)) return;
        lastLookupAt = now;

        if (http == null) http = HttpClient.newHttpClient();

        String url = "https://sessionserver.mojang.com/session/minecraft/profile/" + id.toString().replace("-", "");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(response -> {
                if (response.statusCode() != 200) return;

                try {
                    JsonObject object = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (object.has("name")) resolved.put(id, object.get("name").getAsString());
                } catch (RuntimeException ignored) {
                    // an answer that is not a profile
                }
            })
            .exceptionally(error -> null);
    }

    /** Add one signal to a player. The alert fires when the weights inside the window reach the minimum. */
    private void signal(UUID id, int weight, String reason) {
        long now = System.currentTimeMillis();
        List<Signal> list = signals.computeIfAbsent(id, k -> new ArrayList<>());

        // The same signal repeating every tick counts once per few seconds.
        for (Signal s : list) {
            if (s.reason().equals(reason) && now - s.at() < 5000L) return;
        }

        list.add(new Signal(now, weight, reason));

        long cutoff = now - holdSeconds.get() * 1000L;
        list.removeIf(s -> s.at() < cutoff);

        int score = 0;
        for (Signal s : list) score += s.weight();
        if (score >= minScore.get()) alert(id, score, list);
    }

    private void alert(UUID id, int score, List<Signal> list) {
        long now = System.currentTimeMillis();
        Long last = alertedAt.get(id);
        if (last != null && now - last < 5000L) return;

        alertedAt.put(id, now);
        flagged.put(id, now + 10_000L);
        maybeResolve(id);

        if (!notify.get()) return;

        StringBuilder reasons = new StringBuilder();
        for (Signal s : list) {
            if (reasons.length() > 0) reasons.append("; ");
            reasons.append(s.reason());
        }

        info("Possible vanish/staff signal: %s, UUID %s, score %d (%s)", nameOf(id), id, score, reasons);
    }
}
