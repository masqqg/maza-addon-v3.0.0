package com.maza.client.setting;

public class SettingColor {
    public int r, g, b, a;

    public SettingColor(int r, int g, int b, int a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public SettingColor(int r, int g, int b) {
        this(r, g, b, 255);
    }

    public int getR() { return r; }
    public int getG() { return g; }
    public int getB() { return b; }
    public int getA() { return a; }

    public int getPacked() {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
