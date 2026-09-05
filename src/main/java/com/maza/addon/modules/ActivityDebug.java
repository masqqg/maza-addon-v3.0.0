package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.ChunkPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ActivityDebug extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("threshold")
        .description("Block updates to trigger alert")
        .defaultValue(10)
        .min(1)
        .max(100)
        .build()
    );

    private final Setting<Boolean> playSound = sgGeneral.add(new BoolSetting.Builder()
        .name("play-sound")
        .description("Play sound on detection")
        .defaultValue(true)
        .build()
    );

    private final Map<ChunkPos, Integer> chunkActivity = new ConcurrentHashMap<>();

    public ActivityDebug() {
        super(MazaCategory.INSTANCE, "activity-debug", "Detects block activity in chunks.");
    }

    @Override
    public void onActivate() {
        chunkActivity.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        ChunkPos pos = event.chunk().getPos();
        chunkActivity.put(pos, 0);
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null) return;

        ChunkPos pos = new ChunkPos(event.pos);
        int count = chunkActivity.getOrDefault(pos, 0) + 1;
        chunkActivity.put(pos, count);

        if (count >= threshold.get()) {
            info("High activity in chunk %s (%d updates)", pos, count);
            
            if (playSound.get() && mc.player != null) {
                mc.world.playSound(
                    mc.player,
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING,
                    net.minecraft.sound.SoundCategory.PLAYERS,
                    1.0f, 1.0f
                );
            }
            
            chunkActivity.put(pos, 0);
        }
    }
}
