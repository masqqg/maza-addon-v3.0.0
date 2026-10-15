package com.maza.client.setting;

import java.util.function.BooleanSupplier;

public class IntSetting extends Setting<Integer> {
    public final int min;
    public final int max;
    public final int sliderMin;
    public final int sliderMax;

    public IntSetting(String name, String description, int defaultValue, int min, int max, int sliderMin, int sliderMax, BooleanSupplier visible) {
        super(name, description, defaultValue, visible);
        this.min = min;
        this.max = max;
        this.sliderMin = sliderMin;
        this.sliderMax = sliderMax;
    }

    public static class Builder {
        private String name = "";
        private String description = "";
        private int defaultValue = 0;
        private int min = Integer.MIN_VALUE;
        private int max = Integer.MAX_VALUE;
        private int sliderMin = 0;
        private int sliderMax = 100;
        private BooleanSupplier visible = () -> true;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder defaultValue(int defaultValue) { this.defaultValue = defaultValue; return this; }
        public Builder min(int min) { this.min = min; return this; }
        public Builder max(int max) { this.max = max; return this; }
        public Builder sliderMin(int sliderMin) { this.sliderMin = sliderMin; return this; }
        public Builder sliderMax(int sliderMax) { this.sliderMax = sliderMax; return this; }
        public Builder visible(BooleanSupplier visible) { this.visible = visible; return this; }

        public IntSetting build() {
            if (min != Integer.MIN_VALUE && sliderMin == 0) sliderMin = min;
            if (max != Integer.MAX_VALUE && sliderMax == 100) sliderMax = max;
            return new IntSetting(name, description, defaultValue, min, max, sliderMin, sliderMax, visible);
        }
    }
}
