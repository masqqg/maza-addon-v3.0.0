package com.maza.client.setting;

import java.util.function.BooleanSupplier;

public class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String name, String description, Boolean defaultValue, BooleanSupplier visible) {
        super(name, description, defaultValue, visible);
    }

    public static class Builder {
        private String name = "";
        private String description = "";
        private boolean defaultValue = false;
        private BooleanSupplier visible = () -> true;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder defaultValue(boolean defaultValue) { this.defaultValue = defaultValue; return this; }
        public Builder visible(BooleanSupplier visible) { this.visible = visible; return this; }

        public BoolSetting build() {
            return new BoolSetting(name, description, defaultValue, visible);
        }
    }
}
