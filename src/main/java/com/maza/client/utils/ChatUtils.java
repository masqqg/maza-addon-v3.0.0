package com.maza.client.utils;

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

    public static void send(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null && mc.inGameHud.getChatHud() != null) {
            mc.inGameHud.getChatHud().addMessage(Text.literal(message));
        }
    }
}
