package com.maza.client.module.modules.client;

import com.maza.client.event.EventHandler;
import com.maza.client.event.Render2DEvent;
import com.maza.client.module.Category;
import com.maza.client.module.Module;
import com.maza.client.module.modules.client.notifications.Notification;
import com.maza.client.module.modules.client.notifications.NotificationType;
import com.maza.client.setting.BoolSetting;
import com.maza.client.setting.ColorSetting;
import com.maza.client.setting.IntSetting;
import com.maza.client.setting.SettingColor;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NotificationManager extends Module {
    public static final NotificationManager INSTANCE = new NotificationManager();
    
    private final List<Notification> notifications = new ArrayList<>();
    
    public final BoolSetting enabled = addSetting(new BoolSetting.Builder()
        .name("Enabled")
        .description("Show notifications")
        .defaultValue(true)
        .build());
    
    public final IntSetting animationTime = addSetting(new IntSetting.Builder()
        .name("Animation Time")
        .description("Time for notifications to slide in/out")
        .defaultValue(200)
        .min(50)
        .max(1000)
        .sliderMin(50)
        .sliderMax(1000)
        .build());
    
    public final IntSetting fadeTime = addSetting(new IntSetting.Builder()
        .name("Fade Time")
        .description("Time to fade notifications out")
        .defaultValue(500)
        .min(100)
        .max(2000)
        .sliderMin(100)
        .sliderMax(2000)
        .build());
    
    public final IntSetting maxNotifications = addSetting(new IntSetting.Builder()
        .name("Max Notifications")
        .description("Maximum number of active notifications")
        .defaultValue(5)
        .min(1)
        .max(20)
        .sliderMin(1)
        .sliderMax(20)
        .build());
    
    public final ColorSetting bgColor = addSetting(new ColorSetting.Builder()
        .name("Background Color")
        .description("Background color for notifications")
        .defaultValue(new SettingColor(25, 25, 35, 220))
        .build());
    
    public final ColorSetting borderColor = addSetting(new ColorSetting.Builder()
        .name("Border Color")
        .description("Border color for notifications")
        .defaultValue(new SettingColor(124, 77, 255, 255))
        .build());
    
    public NotificationManager() {
        super("Notifications", "Shows in-game notifications", Category.CLIENT);
        setEnabled(true);
    }
    
    public static void send(String title, String message, NotificationType type) {
        send(title, message, type, 5000);
    }
    
    public static void send(String title, String message, NotificationType type, long duration) {
        INSTANCE.addNotification(new Notification(title, message, type, duration));
    }
    
    public static void send(String message, NotificationType type) {
        send("", message, type);
    }
    
    public void addNotification(Notification notification) {
        notifications.add(0, notification);
        while (notifications.size() > INSTANCE.maxNotifications.get()) {
            notifications.remove(notifications.size() - 1);
        }
    }
    
    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (mc.options.hudHidden || !enabled.get()) return;
        
        DrawContext context = event.context;
        int x = event.width - 260;
        int y = 10;
        
        Iterator<Notification> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            Notification notification = iterator.next();
            if (notification.isExpired()) {
                notification.startFadeOut();
            }
            
            if (notification.isFadingOut() && notification.getAlpha() <= 0) {
                iterator.remove();
                continue;
            }
            
            notification.update(event.tickDelta);
            
            int height = notification.getHeight(mc.textRenderer);
            int width = 250;
            
            int renderX = x + (int)(20 * notification.getAnimationProgress());
            int renderY = y;
            
            notification.render(context, renderX, renderY, width, height);
            
            y += height + 5;
        }
    }
}
