package com.maza.client.setting;

import java.util.function.BooleanSupplier;

public class ColorSetting extends Setting<SettingColor> {
    public ColorSetting(String name, String description, SettingColor defaultValue, BooleanSupplier visible) {
        super(name, description, defaultValue, visible);
    }

    public static class Builder {
        private String name = "";
        private String description = "";
        private SettingColor defaultValue = new SettingColor(255, 255, 255, 255);
        private BooleanSupplier visible = () -> true;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder defaultValue(SettingColor defaultValue) { this.defaultValue = defaultValue; return this; }
        public Builder visible(BooleanSupplier visible) { this.visible = visible; return this; }

        public ColorSetting build() {
            return new ColorSetting(name, description, defaultValue, visible);
        }
    }
}
