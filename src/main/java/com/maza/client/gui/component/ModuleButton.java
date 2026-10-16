package com.maza.client.gui.component;

import com.maza.client.module.Module;
import com.maza.client.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ModuleButton {
    private final Module module;
    private final CategoryFrame parent;
    private final List<SettingComponent> settingComponents = new ArrayList<>();
    private boolean expanded = false;
    private boolean binding = false;

    public ModuleButton(Module module, CategoryFrame parent) {
        this.module = module;
        this.parent = parent;
        for (Setting<?> setting : module.getSettings()) {
            settingComponents.add(new SettingComponent(setting, this));
        }
    }

    public Module getModule() {
        return module;
    }

    public int getHeight() {
        int h = 18;
        if (expanded) {
            for (SettingComponent comp : settingComponents) {
                if (comp.getSetting().isVisible()) {
                    h += 14;
                }
            }
        }
        return h;
    }

    public void render(DrawContext context, int x, int y, int width, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 18;

        // Smooth background with hover effect
        int baseColor = module.isEnabled() ? 0xFF7C4DFF : 0xFF20202A;
        int hoverColor = module.isEnabled() ? 0xFF8354FF : 0xFF2A2A38;
        int bgColor = hovered ? hoverColor : baseColor;
        
        // Subtle gradient background
        context.fill(x + 1, y + 1, x + width - 1, y + 17, bgColor);
        
        // Border glow when enabled
        if (module.isEnabled()) {
            context.fill(x, y, x + width, y + 1, 0xFF7C4DFF);
            context.fill(x, y + 17, x + width, y + 18, 0xFF7C4DFF);
        }

        String title = binding ? "§bPress key..." : (module.isEnabled() ? "§f" : "§7") + module.getName();
        context.drawTextWithShadow(mc.textRenderer, title, x + 5, y + 5, 0xFFFFFFFF);

        if (!settingComponents.isEmpty()) {
            String arrow = expanded ? "▲" : "▼";
            context.drawTextWithShadow(mc.textRenderer, arrow, x + width - 12, y + 5, 0xFFAAAAAA);
        }

        if (expanded) {
            int curY = y + 18;
            for (SettingComponent comp : settingComponents) {
                if (comp.getSetting().isVisible()) {
                    comp.render(context, x, curY, width, 14, mouseX, mouseY);
                    curY += 14;
                }
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int x, int y, int width) {
        boolean headerHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 18;

        if (headerHovered) {
            if (binding) {
                // Only actual keyboard keys should set the keybind (handled in
                // {@link #keyPressed}). Mouse clicks during binding just cancel it.
                binding = false;
                return true;
            }

            if (button == 0) { // Left click
                module.toggle();
                return true;
            } else if (button == 1) { // Right click - open settings
                expanded = !expanded;
                return true;
            } else if (button == 2) { // Middle click: bind key
                binding = true;
                return true;
            }
        }

        if (expanded) {
            int curY = y + 18;
            for (SettingComponent comp : settingComponents) {
                if (comp.getSetting().isVisible()) {
                    if (comp.mouseClicked(mouseX, mouseY, button, x, curY, width, 14)) {
                        return true;
                    }
                    curY += 14;
                }
            }
        }
        return false;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        for (SettingComponent comp : settingComponents) {
            comp.mouseReleased(mouseX, mouseY, button);
        }
    }

    public void mouseDragged(double mouseX, double mouseY, int button, int x, int width) {
        if (expanded) {
            for (SettingComponent comp : settingComponents) {
                comp.mouseDragged(mouseX, mouseY, button, x, width);
            }
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                module.setKey(GLFW.GLFW_KEY_UNKNOWN);
            } else {
                module.setKey(keyCode);
            }
            binding = false;
            return true;
        }
        return false;
    }
}
