package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.ChunkPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MovementDebug extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> scoreThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("score-threshold")
        .description("Score to trigger alert")
        .defaultValue(50)
        .min(1)
        .max(200)
        .build()
    );

    private final Map<ChunkPos, Integer> chunkScores = new ConcurrentHashMap<>();

    public MovementDebug() {
        super(MazaCategory.INSTANCE, "movement-debug", "Tracks chunk activity with scoring.");
    }

    @Override
    public void onActivate() {
        chunkScores.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        ChunkPos pos = event.chunk().getPos();
        chunkScores.put(pos, 0);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        ChunkPos playerChunk = mc.player.getChunkPos();
        
        for (Map.Entry<ChunkPos, Integer> entry : chunkScores.entrySet()) {
            ChunkPos pos = entry.getKey();
            int distance = Math.abs(pos.x - playerChunk.x) + Math.abs(pos.z - playerChunk.z);
            
            if (distance <= 3) {
                int score = entry.getValue() + 1;
                chunkScores.put(pos, score);
                
                if (score >= scoreThreshold.get()) {
                    info("Suspicious activity in chunk %s (score: %d)", pos, score);
                    chunkScores.put(pos, 0);
                }
            }
        }
    }
}
