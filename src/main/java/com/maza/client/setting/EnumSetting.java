package com.maza.client.setting;

import java.util.function.BooleanSupplier;

public class EnumSetting<T extends Enum<T>> extends Setting<T> {
    public EnumSetting(String name, String description, T defaultValue, BooleanSupplier visible) {
        super(name, description, defaultValue, visible);
    }

    public static class Builder<T extends Enum<T>> {
        private String name = "";
        private String description = "";
        private T defaultValue;
        private BooleanSupplier visible = () -> true;

        public Builder<T> name(String name) { this.name = name; return this; }
        public Builder<T> description(String description) { this.description = description; return this; }
        public Builder<T> defaultValue(T defaultValue) { this.defaultValue = defaultValue; return this; }
        public Builder<T> visible(BooleanSupplier visible) { this.visible = visible; return this; }

        public EnumSetting<T> build() {
            return new EnumSetting<>(name, description, defaultValue, visible);
        }
    }
}
