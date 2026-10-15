package com.maza.client.event;

public class KeyEvent extends Event {
    public final int key;
    public final int scanCode;
    public final int action;
    public final int modifiers;

    public KeyEvent(int key, int scanCode, int action, int modifiers) {
        this.key = key;
        this.scanCode = scanCode;
        this.action = action;
        this.modifiers = modifiers;
    }
}
