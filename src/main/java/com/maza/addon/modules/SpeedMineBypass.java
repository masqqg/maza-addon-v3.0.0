package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class SpeedMineBypass extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoTool = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-tool").description("Silently switch to fastest tool (SAFE, no flag)")
        .defaultValue(true).build());

    private final Setting<Boolean> riskyFastBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("risky-fast-break").description("Send extra break packets (CAN FLAG!)")
        .defaultValue(false).build());

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay").description("Ticks between extra packets (risky mode)")
        .defaultValue(6).min(2).max(20).sliderRange(2, 20).build());

    private BlockPos lastBlock;
    private Direction lastDirection;
    private int tickCounter = 0;

    public SpeedMineBypass() {
        super(MazaCategory.INSTANCE, "speed-mine-bypass", "Auto tool (safe) + fast break (risky)");
    }

    @Override
    public void onActivate() {
        lastBlock = null;
        lastDirection = null;
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        lastBlock = null;
        lastDirection = null;
        tickCounter = 0;
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (mc == null || mc.world == null || mc.player == null || event == null) return;
        BlockPos pos = event.blockPos;
        Direction dir = event.direction;
        if (pos == null || dir == null) return;

        lastBlock = pos;
        lastDirection = dir;

        if (autoTool.get()) {
            try {
                BlockState state = mc.world.getBlockState(pos);
                int bestSlot = -1;
                float bestSpeed = 1.0f;

                for (int i = 0; i < 9; i++) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (stack == null || stack.isEmpty()) continue;
                    float speed = stack.getMiningSpeedMultiplier(state);
                    if (speed > bestSpeed) {
                        bestSpeed = speed;
                        bestSlot = i;
                    }
                }

                if (bestSlot >= 0) {
                    InvUtils.swap(bestSlot, true);
                }
            } catch (Exception ignored) {}
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!riskyFastBreak.get()) return;
        if (mc == null || mc.world == null || mc.player == null) return;
        if (mc.getNetworkHandler() == null) return;
        if (lastBlock == null) return;

        tickCounter++;

        if (tickCounter >= delay.get()) {
            tickCounter = 0;
            try {
                BlockState state = mc.world.getBlockState(lastBlock);
                if (state.isAir()) {
                    lastBlock = null;
                    lastDirection = null;
                    return;
                }
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                    lastBlock,
                    lastDirection
                ));
            } catch (Exception ignored) {}
        }
    }
}
