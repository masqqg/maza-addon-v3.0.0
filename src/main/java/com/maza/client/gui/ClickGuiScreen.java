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
        // Draw slight background darkening
        context.fill(0, 0, width, height, 0x66000000);

        // Header info bar
        String header = "§dMaza Client §7v3.0.0";
        int totalEnabled = 0;
        int totalModules = 0;
        for (CategoryFrame frame : FRAMES) {
            // Count via module manager
        }
        
        // Calculate stats
        com.maza.client.module.ModuleManager mm = com.maza.client.module.ModuleManager.INSTANCE;
        totalModules = mm.getModules().size();
        for (com.maza.client.module.Module m : mm.getModules()) {
            if (m.isEnabled()) totalEnabled++;
        }
        
        context.fill(0, 0, width, 16, 0xDD101015);
        context.fill(0, 15, width, 16, 0xFF7C4DFF);
        context.drawTextWithShadow(textRenderer, header, 8, 4, 0xFFFFFFFF);
        int statsX = width - textRenderer.getWidth("§f" + totalEnabled + "/" + totalModules + " modüller") - 8;
        context.drawTextWithShadow(textRenderer, "§f" + totalEnabled + "/" + totalModules + " modüller", statsX, 4, 0xFFFFFFFF);

        for (CategoryFrame frame : FRAMES) {
            frame.render(context, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (CategoryFrame frame : FRAMES) {
            if (frame.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (CategoryFrame frame : FRAMES) {
            frame.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        for (CategoryFrame frame : FRAMES) {
            frame.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (CategoryFrame frame : FRAMES) {
            if (frame.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
