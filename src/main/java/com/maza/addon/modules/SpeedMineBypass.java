package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.item.ToolItem;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class SpeedMineBypass extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay").description("Delay between break packets (try 3-8)")
        .defaultValue(5).min(1).max(20).sliderRange(1, 20).build());

    private final Setting<Boolean> onlyTools = sgGeneral.add(new BoolSetting.Builder()
        .name("only-tools").description("Only speed mine when holding a tool")
        .defaultValue(true).build());

    private final Setting<Boolean> silentSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-switch").description("Silently switch to best tool")
        .defaultValue(false).build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug").description("Print debug info")
        .defaultValue(false).build());

    private BlockPos lastBlock;
    private Direction lastDirection;
    private int tickCounter = 0;

    public SpeedMineBypass() {
        super(MazaCategory.INSTANCE, "speed-mine-bypass", "Speed mine with anti-cheat bypass");
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

        if (onlyTools.get() && !(mc.player.getMainHandStack().getItem() instanceof ToolItem)) {
            return;
        }

        if (silentSwitch.get()) {
            try {
                FindItemResult tool = InvUtils.findInHotbar(item -> item.getItem() instanceof ToolItem);
                if (tool.found()) {
                    InvUtils.swap(tool.slot(), true);
                }
            } catch (Exception ignored) {}
        }

        lastBlock = pos;
        lastDirection = dir;

        if (debug.get()) info("Started breaking %s", pos.toShortString());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
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

                if (debug.get()) info("Sent break packet for %s", lastBlock.toShortString());
            } catch (Exception ignored) {}
        }
    }
}
