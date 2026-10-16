package com.maza.client.module.modules.client.notifications;

import com.maza.client.setting.SettingColor;

public enum NotificationType {
    INFO(new SettingColor(124, 77, 255, 255)),
    SUCCESS(new SettingColor(60, 255, 140, 255)),
    WARNING(new SettingColor(255, 200, 60, 255)),
    ERROR(new SettingColor(255, 60, 60, 255));
    
    private final SettingColor color;
    
    NotificationType(SettingColor color) {
        this.color = color;
    }
    
    public SettingColor getColor() {
        return color;
    }
}
