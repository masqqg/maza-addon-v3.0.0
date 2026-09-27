package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;

public class AutoDisconnect extends Module {
    private final SettingGroup general = settings.getDefaultGroup();

    private final Setting<Boolean> enabled = general.add(new BoolSetting.Builder()
        .name("auto-disconnect")
        .defaultValue(true)
        .build());

    private final Setting<Integer> triggerY = general.add(new IntSetting.Builder()
        .name("trigger-y")
        .description("Disconnect when the player's Y reaches this value or lower.")
        .defaultValue(-3)
        .min(-64)
        .max(320)
        .sliderMin(-64)
        .sliderMax(320)
        .build());

    private boolean triggered;

    public AutoDisconnect() {
        super(MazaCategory.INSTANCE, "auto-disconnect", "Disconnects from the server when the player reaches the configured Y level.");
    }

    @Override
    public void onActivate() {
        triggered = false;
    }

    @Override
    public void onDeactivate() {
        triggered = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!enabled.get() || triggered || mc.player == null || mc.getNetworkHandler() == null) return;

        if (mc.player.getY() <= triggerY.get()) {
            triggered = true;
            if (mc.getNetworkHandler().getConnection() != null) {
                mc.getNetworkHandler().getConnection().disconnect(Text.literal("Auto-disconnect: Y <= " + triggerY.get()));
            }
        }
    }
}
