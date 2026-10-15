package com.maza.client.utils;

import com.maza.client.MazaClient;
import com.maza.client.module.modules.client.NotificationManager;
import com.maza.client.module.modules.client.notifications.NotificationType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ChatUtils {
    private static final String PREFIX = "§7[§dMaza§7] §r";

    public static void info(String message) {
        send(PREFIX + "§f" + message);
    }

    public static void warning(String message) {
        send(PREFIX + "§e" + message);
    }

    public static void error(String message) {
        send(PREFIX + "§c" + message);
    }

    public static void success(String message) {
        send(PREFIX + "§a" + message);
    }

    public static void send(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null && mc.inGameHud.getChatHud() != null) {
            mc.inGameHud.getChatHud().addMessage(Text.literal(message));
        }
    }

    public static void notice(String message) {
        NotificationManager.send("Maza", message, NotificationType.INFO, 4000);
    }

    public static void notice(String title, String message, NotificationType type) {
        NotificationManager.send(title, message, type, 4000);
    }

    public static void infoNotice(String message) {
        NotificationManager.send("Maza", message, NotificationType.INFO, 4000);
    }

    public static void successNotice(String message) {
        NotificationManager.send("Maza", message, NotificationType.SUCCESS, 4000);
    }

    public static void warningNotice(String message) {
        NotificationManager.send("Maza", message, NotificationType.WARNING, 4000);
    }

    public static void errorNotice(String message) {
        NotificationManager.send("Maza", message, NotificationType.ERROR, 4000);
    }
}