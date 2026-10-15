package com.maza.client.gui.component;

import com.maza.client.setting.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class SettingComponent {
    private final Setting<?> setting;
    private final ModuleButton parent;
    private boolean dragging = false;

    public SettingComponent(Setting<?> setting, ModuleButton parent) {
        this.setting = setting;
        this.parent = parent;
    }

    public Setting<?> getSetting() {
        return setting;
    }

    public void render(DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY) {
        if (!setting.isVisible()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;

        // Background
        int bg = hovered ? 0xFF22222D : 0xFF181820;
        context.fill(x, y, x + width, y + height, bg);

        if (setting instanceof BoolSetting boolSetting) {
            boolean val = boolSetting.get();
            int toggleColor = val ? 0xFF7C4DFF : 0xFF353545;
            context.fill(x + width - 16, y + 2, x + width - 4, y + height - 2, toggleColor);
            context.drawTextWithShadow(mc.textRenderer, setting.getName(), x + 4, y + 3, val ? 0xFFFFFFFF : 0xFFAAAAAA);
        } else if (setting instanceof IntSetting intSetting) {
            double range = intSetting.sliderMax - intSetting.sliderMin;
            double pct = range > 0 ? (double) (intSetting.get() - intSetting.sliderMin) / range : 0;
            pct = Math.max(0, Math.min(1, pct));
            int sliderW = (int) ((width - 8) * pct);
            context.fill(x + 4, y + height - 4, x + 4 + sliderW, y + height - 2, 0xFF7C4DFF);
            String text = setting.getName() + ": §7" + intSetting.get();
            context.drawTextWithShadow(mc.textRenderer, text, x + 4, y + 2, 0xFFE0E0E0);
        } else if (setting instanceof DoubleSetting doubleSetting) {
            double range = doubleSetting.sliderMax - doubleSetting.sliderMin;
            double pct = range > 0 ? (double) (doubleSetting.get() - doubleSetting.sliderMin) / range : 0;
            pct = Math.max(0, Math.min(1, pct));
            int sliderW = (int) ((width - 8) * pct);
            context.fill(x + 4, y + height - 4, x + 4 + sliderW, y + height - 2, 0xFF7C4DFF);
            String text = setting.getName() + ": §7" + String.format("%.1f", doubleSetting.get());
            context.drawTextWithShadow(mc.textRenderer, text, x + 4, y + 2, 0xFFE0E0E0);
        } else if (setting instanceof EnumSetting<?> enumSetting) {
            String text = setting.getName() + ": §b" + enumSetting.get().name();
            context.drawTextWithShadow(mc.textRenderer, text, x + 4, y + 3, 0xFFFFFFFF);
        } else if (setting instanceof ColorSetting colorSetting) {
            SettingColor c = colorSetting.get();
            int preview = c.getPacked();
            context.fill(x + width - 18, y + 2, x + width - 4, y + height - 2, preview);
            context.drawTextWithShadow(mc.textRenderer, setting.getName(), x + 4, y + 3, 0xFFFFFFFF);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean mouseClicked(double mouseX, double mouseY, int button, int x, int y, int width, int height) {
        if (!setting.isVisible()) return false;
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        if (!hovered) return false;

        if (button == 0) { // Left click
            if (setting instanceof BoolSetting boolSetting) {
                boolSetting.set(!boolSetting.get());
                return true;
            } else if (setting instanceof IntSetting intSetting) {
                dragging = true;
                updateIntSlider(mouseX, x, width, intSetting);
                return true;
            } else if (setting instanceof DoubleSetting doubleSetting) {
                dragging = true;
                updateDoubleSlider(mouseX, x, width, doubleSetting);
                return true;
            } else if (setting instanceof EnumSetting enumSetting) {
                Enum<?> current = (Enum<?>) enumSetting.get();
                Enum<?>[] values = current.getClass().getEnumConstants();
                int nextIndex = (current.ordinal() + 1) % values.length;
                enumSetting.set(values[nextIndex]);
                return true;
            } else if (setting instanceof ColorSetting colorSetting) {
                // Cycle simple colors on click
                SettingColor cur = colorSetting.get();
                if (cur.r == 255 && cur.g == 255 && cur.b == 255) {
                    colorSetting.set(new SettingColor(124, 77, 255, cur.a)); // Purple
                } else if (cur.r == 124) {
                    colorSetting.set(new SettingColor(255, 60, 60, cur.a)); // Red
                } else if (cur.r == 255 && cur.g == 60) {
                    colorSetting.set(new SettingColor(60, 255, 60, cur.a)); // Green
                } else if (cur.g == 255) {
                    colorSetting.set(new SettingColor(60, 180, 255, cur.a)); // Blue
                } else {
                    colorSetting.set(new SettingColor(255, 255, 255, cur.a)); // White
                }
                return true;
            }
        }
        return false;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
    }

    public void mouseDragged(double mouseX, double mouseY, int button, int x, int width) {
        if (dragging) {
            if (setting instanceof IntSetting intSetting) {
                updateIntSlider(mouseX, x, width, intSetting);
            } else if (setting instanceof DoubleSetting doubleSetting) {
                updateDoubleSlider(mouseX, x, width, doubleSetting);
            }
        }
    }

    private void updateIntSlider(double mouseX, int x, int width, IntSetting intSetting) {
        double pct = (mouseX - (x + 4)) / (width - 8);
        pct = Math.max(0, Math.min(1, pct));
        int val = (int) Math.round(intSetting.sliderMin + pct * (intSetting.sliderMax - intSetting.sliderMin));
        intSetting.set(val);
    }

    private void updateDoubleSlider(double mouseX, int x, int width, DoubleSetting doubleSetting) {
        double pct = (mouseX - (x + 4)) / (width - 8);
        pct = Math.max(0, Math.min(1, pct));
        double val = doubleSetting.sliderMin + pct * (doubleSetting.sliderMax - doubleSetting.sliderMin);
        doubleSetting.set(val);
    }
}
