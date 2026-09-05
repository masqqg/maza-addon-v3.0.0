package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashSet;
import java.util.Set;

public class EntityTracker extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> trackPlayers = sgGeneral.add(new BoolSetting.Builder()
        .name("track-players")
        .description("Track player entities")
        .defaultValue(true)
        .build()
    );

    private final Set<Integer> trackedEntities = new HashSet<>();

    public EntityTracker() {
        super(MazaCategory.INSTANCE, "entity-tracker", "Tracks entity spawns in world.");
    }

    @Override
    public void onActivate() {
        trackedEntities.clear();
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        Entity entity = event.entity;
        
        if (trackPlayers.get() && entity instanceof PlayerEntity) {
            if (!trackedEntities.contains(entity.getId())) {
                trackedEntities.add(entity.getId());
                info("Player spawned: %s at %.0f, %.0f, %.0f", 
                    entity.getName().getString(),
                    entity.getX(), entity.getY(), entity.getZ());
            }
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        trackedEntities.remove(event.entity.getId());
    }
}
