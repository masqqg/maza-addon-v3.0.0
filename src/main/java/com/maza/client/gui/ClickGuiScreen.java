package com.maza.client.gui;

import com.maza.client.config.ConfigManager;
import com.maza.client.gui.component.CategoryFrame;
import com.maza.client.module.Category;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ClickGuiScreen extends Screen {
    private static final List<CategoryFrame> FRAMES = new ArrayList<>();

    static {
        int startX = 20;
        int frameWidth = 120;
        for (Category category : Category.values()) {
            FRAMES.add(new CategoryFrame(category, startX, 25, frameWidth));
            startX += frameWidth + 12;
        }
    }

    public ClickGuiScreen() {
        super(Text.literal("Maza ClickGUI"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        for (int i = 0; i < height; i += 2) {
            int alpha = 100 + (i * 2);
            if (alpha > 180) alpha = 180;
            context.fill(0, i, width, i + 2, 0x000000 | (alpha << 24));
        }

        com.maza.client.module.ModuleManager mm = com.maza.client.module.ModuleManager.INSTANCE;
        int totalModules = mm.getModules().size();
        int totalEnabled = 0;
        for (com.maza.client.module.Module m : mm.getModules()) {
            if (m.isEnabled()) totalEnabled++;
        }

        context.fill(0, 0, width, 24, 0xDD0A0A0F);
        for (int i = 0; i < 3; i++) {
            int alpha = 255 - (i * 85);
            context.fill(0, 23 - i, width, 24 - i, 0x2E86AB | (alpha << 24));
        }

        String header = "§bMaza";
        context.drawTextWithShadow(textRenderer, header, 12, 6, 0xFF2E86AB);
        context.drawTextWithShadow(textRenderer, " Client v3.0.0", 12 + textRenderer.getWidth(header), 6, 0xFFFFFFFF);

        int statsX = width - textRenderer.getWidth("§a" + totalEnabled + "§7/§f" + totalModules) - 12;
        context.drawTextWithShadow(textRenderer, "§a" + totalEnabled + "§7/§f" + totalModules, statsX, 6, 0xFFFFFFFF);

        for (CategoryFrame frame : FRAMES) {
            frame.render(context, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        ConfigManager.INSTANCE.save();
        super.close();
    }
}
