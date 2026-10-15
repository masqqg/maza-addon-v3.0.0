package com.maza.client.setting;

import java.util.function.BooleanSupplier;

public class DoubleSetting extends Setting<Double> {
    public final double min;
    public final double max;
    public final double sliderMin;
    public final double sliderMax;

    public DoubleSetting(String name, String description, double defaultValue, double min, double max, double sliderMin, double sliderMax, BooleanSupplier visible) {
        super(name, description, defaultValue, visible);
        this.min = min;
        this.max = max;
        this.sliderMin = sliderMin;
        this.sliderMax = sliderMax;
    }

    public static class Builder {
        private String name = "";
        private String description = "";
        private double defaultValue = 0.0;
        private double min = 0.0;
        private double max = Double.MAX_VALUE;
        private double sliderMin = 0.0;
        private double sliderMax = 1.0;
        private BooleanSupplier visible = () -> true;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder defaultValue(double defaultValue) { this.defaultValue = defaultValue; return this; }
        public Builder min(double min) { this.min = min; return this; }
        public Builder max(double max) { this.max = max; return this; }
        public Builder sliderMin(double sliderMin) { this.sliderMin = sliderMin; return this; }
        public Builder sliderMax(double sliderMax) { this.sliderMax = sliderMax; return this; }
        public Builder visible(BooleanSupplier visible) { this.visible = visible; return this; }

        public DoubleSetting build() {
            return new DoubleSetting(name, description, defaultValue, min, max, sliderMin, sliderMax, visible);
        }
    }
}
