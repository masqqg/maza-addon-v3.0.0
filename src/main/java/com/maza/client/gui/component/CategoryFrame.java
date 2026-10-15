package com.maza.client.gui.component;

import com.maza.client.module.Category;
import com.maza.client.module.Module;
import com.maza.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class CategoryFrame {
    private final Category category;
    private int x, y, width, height;
    private final List<ModuleButton> buttons = new ArrayList<>();
    private boolean dragging = false;
    private int dragX, dragY;
    private boolean open = true;

    public CategoryFrame(Category category, int x, int y, int width) {
        this.category = category;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = 18;

        for (Module module : ModuleManager.INSTANCE.getByCategory(category)) {
            buttons.add(new ModuleButton(module, this));
        }
    }

    public void render(DrawContext context, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();

        // Header
        context.fill(x, y, x + width, y + height, 0xFF14141A);
        context.fill(x, y + height - 2, x + width, y + height, 0xFF7C4DFF); // Accent line under header

        String title = category.getName() + (open ? " §7[-]" : " §7[+]");
        context.drawTextWithShadow(mc.textRenderer, title, x + 5, y + 5, 0xFFFFFFFF);

        if (open) {
            int curY = y + height;
            for (ModuleButton button : buttons) {
                button.render(context, x, curY, width, mouseX, mouseY);
                curY += button.getHeight();
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean headerHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;

        if (headerHovered) {
            if (button == 0) { // Dragging
                dragging = true;
                dragX = (int) (mouseX - x);
                dragY = (int) (mouseY - y);
                return true;
            } else if (button == 1) { // Toggle collapse
                open = !open;
                return true;
            }
        }

        if (open) {
            int curY = y + height;
            for (ModuleButton btn : buttons) {
                if (btn.mouseClicked(mouseX, mouseY, button, x, curY, width)) {
                    return true;
                }
                curY += btn.getHeight();
            }
        }

        return false;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        if (open) {
            for (ModuleButton btn : buttons) {
                btn.mouseReleased(mouseX, mouseY, button);
            }
        }
    }

    public void mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            x = (int) (mouseX - dragX);
            y = (int) (mouseY - dragY);
        }
        if (open) {
            for (ModuleButton btn : buttons) {
                btn.mouseDragged(mouseX, mouseY, button, x, width);
            }
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (open) {
            for (ModuleButton btn : buttons) {
                if (btn.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
        }
        return false;
    }
}
