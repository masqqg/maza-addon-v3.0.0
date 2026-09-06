package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.ChunkPos;

public class ChunkMarker extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> chunkX = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-x").description("Chunk X koordinatı")
        .defaultValue(0).min(-1000000).max(1000000).build());

    private final Setting<Integer> chunkZ = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-z").description("Chunk Z koordinatı")
        .defaultValue(0).min(-1000000).max(1000000).build());

    private final Setting<Integer> yLevel = sgGeneral.add(new IntSetting.Builder()
        .name("y-level").description("Y seviyesi")
        .defaultValue(65).min(-64).max(320).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").description("Çizgi rengi")
        .defaultValue(new SettingColor(0, 255, 255, 255)).build());

    public ChunkMarker() {
        super(MazaCategory.INSTANCE, "chunk-marker", "Belirli chunk'ı Y seviyesinde işaretle");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc == null || mc.player == null || event == null || event.renderer == null) return;

        int cx = chunkX.get();
        int cz = chunkZ.get();
        int y = yLevel.get();

        // 16x16 alan, tek blok yüksekliğinde ince kutu
        event.renderer.box(
            cx * 16, y, cz * 16,
            cx * 16 + 16, y + 1, cz * 16 + 16,
            new SettingColor(0, 255, 255, 30),
            lineColor.get(),
            ShapeMode.Both,
            0
        );
    }
}
