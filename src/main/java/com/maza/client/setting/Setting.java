package com.maza.client.setting;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class Setting<T> {
    private final String name;
    private final String description;
    private T value;
    private final T defaultValue;
    private final BooleanSupplier visible;
    private Consumer<T> onChanged;

    public Setting(String name, String description, T defaultValue, BooleanSupplier visible) {
        this.name = name;
        this.description = description;
        this.value = defaultValue;
        this.defaultValue = defaultValue;
        this.visible = visible != null ? visible : () -> true;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public T get() { return value; }
    public T getDefaultValue() { return defaultValue; }
    public boolean isVisible() { return visible.getAsBoolean(); }

    public void set(T value) {
        this.value = value;
        if (onChanged != null) onChanged.accept(value);
    }

    public void setOnChanged(Consumer<T> onChanged) {
        this.onChanged = onChanged;
    }

    public void reset() {
        set(defaultValue);
    }
}
