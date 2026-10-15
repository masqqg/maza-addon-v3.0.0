package com.maza.client.module.modules.client;

import com.maza.client.MazaClient;
import com.maza.client.event.EventHandler;
import com.maza.client.event.Render2DEvent;
import com.maza.client.module.Category;
import com.maza.client.module.Module;
import com.maza.client.module.ModuleManager;
import com.maza.client.setting.BoolSetting;
import com.maza.client.setting.ColorSetting;
import com.maza.client.setting.SettingColor;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HUD extends Module {
    public final BoolSetting watermark = addSetting(new BoolSetting.Builder()
            .name("Watermark")
            .description("Displays client name and version")
            .defaultValue(true)
            .build());

    public final BoolSetting arrayList = addSetting(new BoolSetting.Builder()
            .name("ArrayList")
            .description("Displays enabled modules")
            .defaultValue(true)
            .build());

    public final ColorSetting color = addSetting(new ColorSetting.Builder()
            .name("Color")
            .description("Color for HUD elements")
            .defaultValue(new SettingColor(124, 77, 255, 255))
            .build());

    public HUD() {
        super("HUD", "In-game heads-up display", Category.RENDER);
        setEnabled(true);
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (mc.options.hudHidden) return;
        DrawContext context = event.drawContext;

        int hudColor = color.get().getPacked();

        // Watermark
        if (watermark.get()) {
            String wm = MazaClient.MOD_NAME + " §7v" + MazaClient.MOD_VERSION;
            context.fill(3, 3, 3 + mc.textRenderer.getWidth(wm) + 4, 3 + 12, 0x88000000);
            context.drawTextWithShadow(mc.textRenderer, wm, 5, 5, hudColor);
        }

        // ArrayList
        if (arrayList.get()) {
            List<Module> active = new ArrayList<>();
            for (Module m : ModuleManager.INSTANCE.getModules()) {
                if (m.isEnabled() && !(m instanceof HUD) && !(m instanceof ClickGUI)) {
                    active.add(m);
                }
            }

            active.sort(Comparator.comparingInt(m -> -mc.textRenderer.getWidth(m.getName())));

            int y = 4;
            for (Module m : active) {
                String name = m.getName();
                int textW = mc.textRenderer.getWidth(name);
                int x = event.width - textW - 6;

                context.fill(x - 2, y - 1, event.width - 2, y + 10, 0x77000000);
                context.fill(event.width - 2, y - 1, event.width, y + 10, hudColor);
                context.drawTextWithShadow(mc.textRenderer, name, x, y, 0xFFFFFFFF);

                y += 11;
            }
        }
    }
}
