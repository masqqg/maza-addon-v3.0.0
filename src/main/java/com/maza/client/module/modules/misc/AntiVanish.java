package com.maza.client.module.modules.misc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.maza.client.event.EventHandler;
import com.maza.client.event.PacketEvent;
import com.maza.client.event.Render3DEvent;
import com.maza.client.event.TickEvent;
import com.maza.client.module.Category;
import com.maza.client.module.Module;
import com.maza.client.setting.*;
import com.maza.client.utils.ChatUtils;
import com.maza.client.utils.Render3DUtils;
import com.maza.client.utils.ShapeMode;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AntiVanish extends Module {
    private static final int W_SPAWN = 100;
    private static final int W_REMOVED = 100;
    private static final int W_ABSENT = 35;
    private static final int W_SPECTATOR = 100;
    private static final int W_UNLISTED = 100;
    private static final int W_FLICKER = 30;
    private static final int W_INVISIBLE = 35;
    private static final int W_LAG = 0;
    private static final int W_CORRELATED = 0;

    public final IntSetting range = addSetting(new IntSetting.Builder()
        .name("range").defaultValue(64).min(8).max(256).sliderMax(256).build());

    public final BoolSetting spawnUuid = addSetting(new BoolSetting.Builder()
        .name("spawn-uuid").defaultValue(true).build());

    public final IntSetting spawnDelayTicks = addSetting(new IntSetting.Builder()
        .name("spawn-delay-ticks").defaultValue(20).min(1).max(100).sliderMax(100).build());

    public final IntSetting confirmTicks = addSetting(new IntSetting.Builder()
        .name("confirm-ticks").defaultValue(12).min(1).max(60).sliderMax(60).build());

    public final BoolSetting listWatch = addSetting(new BoolSetting.Builder()
        .name("list-packets").defaultValue(true).build());

    public final BoolSetting flickerWatch = addSetting(new BoolSetting.Builder()
        .name("flicker").defaultValue(true).build());

    public final IntSetting flickerMs = addSetting(new IntSetting.Builder()
        .name("flicker-ms").defaultValue(1500).min(200).max(10000).sliderMin(200).sliderMax(10000).build());

    public final BoolSetting nearbyEntity = addSetting(new BoolSetting.Builder()
        .name("nearby-entity").defaultValue(true).build());

    public final BoolSetting tabRemoval = addSetting(new BoolSetting.Builder()
        .name("tab-removal").defaultValue(true).build());

    public final BoolSetting gamemodeWatch = addSetting(new BoolSetting.Builder()
        .name("gamemode-watch").defaultValue(true).build());

    public final BoolSetting invisibleWatch = addSetting(new BoolSetting.Builder()
        .name("invisible-watch").defaultValue(false).build());

    public final BoolSetting tabLagCorrelation = addSetting(new BoolSetting.Builder()
        .name("tab-lag-correlation").defaultValue(true).build());

    public final IntSetting lagThresholdMs = addSetting(new IntSetting.Builder()
        .name("lag-threshold-ms").defaultValue(500).min(100).max(5000).sliderMax(2000).build());

    public final BoolSetting entityDataWatch = addSetting(new BoolSetting.Builder()
        .name("entity-data-watch").defaultValue(true).build());

    public final BoolSetting lightDataWatch = addSetting(new BoolSetting.Builder()
        .name("light-data-watch").defaultValue(true).build());

    public final IntSetting signalWindowSeconds = addSetting(new IntSetting.Builder()
        .name("signal-window-seconds").defaultValue(20).min(5).max(30).sliderMax(30).build());

    public final IntSetting goliathCellSize = addSetting(new IntSetting.Builder()
        .name("goliath-cell-size").defaultValue(12500).min(1000).max(30000).sliderMax(30000).build());

    public final BoolSetting goliathFusion = addSetting(new BoolSetting.Builder()
        .name("goliath-fusion").defaultValue(true).build());

    public final IntSetting minScore = addSetting(new IntSetting.Builder()
        .name("min-score").defaultValue(35).min(1).max(100).sliderMin(1).sliderMax(100).build());

    public final IntSetting holdSeconds = addSetting(new IntSetting.Builder()
        .name("score-window-seconds").defaultValue(15).min(5).max(600).sliderMin(5).sliderMax(600).build());

    public final BoolSetting notify = addSetting(new BoolSetting.Builder()
        .name("notify").defaultValue(true).build());

    public final BoolSetting esp = addSetting(new BoolSetting.Builder()
        .name("esp").defaultValue(true).build());

    public final BoolSetting resolveNames = addSetting(new BoolSetting.Builder()
        .name("resolve-names").defaultValue(false).build());

    public final ColorSetting alertColor = addSetting(new ColorSetting.Builder()
        .name("color").defaultValue(new SettingColor(255, 60, 60, 255)).build());

    private record Signal(long at, int weight, String reason) {}
    private record Spawn(int entityId, double x, double y, double z, long at) {}
    private record Spawned(UUID uuid, long at) {}

    private final Map<UUID, String> known = new HashMap<>();
    private final Map<UUID, Integer> missingTicks = new HashMap<>();
    private final Map<UUID, Long> alertedAt = new HashMap<>();
    private final Map<UUID, Long> flagged = new HashMap<>();
    private final Map<UUID, Long> recentTabRemovals = new HashMap<>();
    private final Map<UUID, List<Signal>> signals = new HashMap<>();
    private final Map<UUID, Integer> pendingSpawns = new HashMap<>();
    private final Map<UUID, Spawn> spawns = new HashMap<>();
    private final Map<Integer, Spawned> spawnedById = new HashMap<>();
    private final Map<UUID, String> resolved = new ConcurrentHashMap<>();
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
    private int lastGoliathX = Integer.MIN_VALUE;
    private int lastGoliathZ = Integer.MIN_VALUE;
    private final Map<UUID, Set<String>> recentSenseTypes = new HashMap<>();

    public AntiVanish() {
        super("AntiVanish", "Detects vanished/hidden players using behavioral heuristics", Category.MISC);
    }

    @Override
    public void onEnable() {
        clearAll();
    }

    @Override
    public void onDisable() {
        clearAll();
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
        lastGoliathX = Integer.MIN_VALUE;
        lastGoliathZ = Integer.MIN_VALUE;
        recentSenseTypes.clear();
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null) return;

        tick++;
        long now = System.currentTimeMillis();

        int gx = Math.floorDiv(mc.player.getBlockX(), goliathCellSize.get());
        int gz = Math.floorDiv(mc.player.getBlockZ(), goliathCellSize.get());
        if (lastGoliathX != Integer.MIN_VALUE && (gx != lastGoliathX || gz != lastGoliathZ)) {
            signals.clear();
            recentSenseTypes.clear();
            flagged.clear();
            missingTicks.clear();
            ChatUtils.info(String.format("Goliath cell changed (%d, %d) -> (%d, %d); evidence cleared.", lastGoliathX, lastGoliathZ, gx, gz));
        }
        lastGoliathX = gx;
        lastGoliathZ = gz;

        if (lastTickAt != 0L) {
            long gap = now - lastTickAt;
            if (gap >= lagThresholdMs.get()) {
                lastLagAt = now;
                if (tabLagCorrelation.get()) {
                    for (Map.Entry<UUID, Long> removal : recentTabRemovals.entrySet()) {
                        long sinceRemoval = now - removal.getValue();
                        if (sinceRemoval >= 0L && sinceRemoval <= 20_000L) {
                            signal(removal.getKey(), W_LAG, "TAB removal followed by client tick stall (" + gap + "ms)");
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
                    signal(id, W_REMOVED, "removed from TAB while entity is still present");
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

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (spawnUuid.get() && event.packet instanceof EntitySpawnS2CPacket spawn
            && spawn.getEntityType() == EntityType.PLAYER) {
            UUID id = spawn.getUuid();
            int entityId = spawn.getEntityId();
            double x = spawn.getX(), y = spawn.getY(), z = spawn.getZ();
            mc.execute(() -> handleSpawn(id, entityId, x, y, z));
            return;
        }

        if (lightDataWatch.get() && event.packet instanceof LightUpdateS2CPacket light) {
            int cx = light.getChunkX(), cz = light.getChunkZ();
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

        Entity entity = mc.world.getEntityById(entityId);
        if (entity instanceof PlayerEntity player) known.putIfAbsent(id, player.getName().getString());

        pendingSpawns.put(id, tick + spawnDelayTicks.get());
    }

    private void handleRemoved(List<UUID> ids) {
        if (mc.world == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        for (UUID id : ids) {
            if (id.equals(mc.player.getUuid())) continue;
            recentTabRemovals.put(id, now);
            if (mc.world.getPlayerByUuid(id) != null) signal(id, W_REMOVED, "removed from TAB while entity is present");
        }
    }

    private void handleListPacket(List<UUID> unlisted, List<UUID> spectators) {
        if (mc.world == null || mc.player == null) return;
        UUID self = mc.player.getUuid();
        for (UUID id : unlisted) {
            if (!id.equals(self)) signal(id, W_UNLISTED, "profile unlisted: hidden from TAB");
        }
        for (UUID id : spectators) {
            if (!id.equals(self) && gamemodeWatch.get()) signal(id, W_SPECTATOR, "listed as spectator");
        }
    }

    private void handleDestroyed(List<Integer> entityIds) {
        if (mc.world == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        for (int entityId : entityIds) {
            Spawned spawned = spawnedById.remove(entityId);
            if (spawned == null) continue;

            long life = now - spawned.at();
            if (life > flickerMs.get() || listedLast.contains(spawned.uuid())) continue;

            maybeResolve(spawned.uuid());
            signal(spawned.uuid(), W_FLICKER, "player entity vanished within " + life + " ms, not in TAB");
        }
    }

    private void handleLight(int cx, int cz) {
        if (mc.world == null || mc.player == null) return;
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
            signal(id, W_CORRELATED, "TAB removal with recent lag and nearby light/metadata update");
        }
    }

    private void correlateRecentTabRemoval(long now, String source) {
        if (!tabLagCorrelation.get()) return;
        long window = signalWindowSeconds.get() * 1000L;

        for (Map.Entry<UUID, Long> removal : recentTabRemovals.entrySet()) {
            if (now < removal.getValue() || now - removal.getValue() > window) continue;
            boolean lightRecent = lastNearbyLightAt != 0L && now - lastNearbyLightAt <= window;
            boolean entityRecent = lastNearbyEntityDataAt != 0L && now - lastNearbyEntityDataAt <= window;
            boolean lagRecent = lastLagAt != 0L && now - lastLagAt <= 20_000L;

            if (lightRecent && entityRecent && lagRecent && now - lastSignalAlertAt > 5000L) {
                lastSignalAlertAt = now;
                signal(removal.getKey(), W_CORRELATED, "TAB removal + lag + nearby signals (" + source + ")");
            }
        }
    }

    @EventHandler
    public void onRender(Render3DEvent event) {
        if (!esp.get() || mc.world == null || flagged.isEmpty()) return;

        long now = System.currentTimeMillis();
        flagged.values().removeIf(until -> until < now);

        SettingColor line = alertColor.get();
        SettingColor side = new SettingColor(line.r, line.g, line.b, 45);

        for (UUID id : flagged.keySet()) {
            PlayerEntity player = mc.world.getPlayerByUuid(id);
            if (player != null) {
                Box b = player.getBoundingBox();
                Render3DUtils.drawBox(b, side, line, ShapeMode.Both);
                continue;
            }

            Spawn spawn = spawns.get(id);
            if (spawn != null) {
                Box b = new Box(spawn.x() - 0.3, spawn.y(), spawn.z() - 0.3, spawn.x() + 0.3, spawn.y() + 1.8, spawn.z() + 0.3);
                Render3DUtils.drawBox(b, side, line, ShapeMode.Both);
            }
        }
    }

    private String nameOf(UUID id) {
        String name = known.get(id);
        if (name == null) name = resolved.get(id);
        return name != null ? name : "unknown";
    }

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
                } catch (RuntimeException ignored) {}
            })
            .exceptionally(error -> null);
    }

    private void signal(UUID id, int weight, String reason) {
        if (weight <= 0) return;
        long now = System.currentTimeMillis();
        List<Signal> list = signals.computeIfAbsent(id, k -> new ArrayList<>());

        for (Signal s : list) {
            if (s.reason().equals(reason) && now - s.at() < 5000L) return;
        }

        list.add(new Signal(now, weight, reason));

        long cutoff = now - holdSeconds.get() * 1000L;
        list.removeIf(s -> s.at() < cutoff);

        int score = 0;
        Set<String> types = new HashSet<>();
        for (Signal s : list) {
            score += s.weight();
            types.add(senseType(s.reason()));
        }
        recentSenseTypes.put(id, types);
        boolean enoughSenses = !goliathFusion.get() || types.size() >= 2;
        if (score >= minScore.get() && enoughSenses) alert(id, score, list);
    }

    private String senseType(String reason) {
        String r = reason.toLowerCase();
        if (r.contains("spectator") || r.contains("unlisted") || r.contains("tab")) return "TAB";
        if (r.contains("invisible")) return "BODY";
        if (r.contains("particle")) return "PARTICLE";
        if (r.contains("sound")) return "SOUND";
        if (r.contains("spawn") || r.contains("flicker") || r.contains("entity")) return "ENTITY";
        return "OTHER";
    }

    private void alert(UUID id, int score, List<Signal> list) {
        long now = System.currentTimeMillis();
        Long last = alertedAt.get(id);
        if (last != null && now - last < 12_000L) return;

        alertedAt.put(id, now);
        flagged.put(id, now + 7_000L);
        maybeResolve(id);

        if (!notify.get()) return;

        StringBuilder reasons = new StringBuilder();
        for (Signal s : list) {
            if (reasons.length() > 0) reasons.append("; ");
            reasons.append(s.reason());
        }

        ChatUtils.warning(String.format("WATCHING YOU: %s | score %d | Goliath %d,%d | %s", nameOf(id), score, lastGoliathX, lastGoliathZ, reasons));
    }
}
