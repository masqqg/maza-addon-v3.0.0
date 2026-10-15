package com.maza.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.maza.client.MazaClient;
import com.maza.client.module.Module;
import com.maza.client.module.ModuleManager;
import com.maza.client.setting.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;

public class ConfigManager {
    public static final ConfigManager INSTANCE = new ConfigManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configDir;
    private final File configFile;

    private ConfigManager() {
        this.configDir = FabricLoader.getInstance().getConfigDir().resolve("maza");
        this.configFile = configDir.resolve("config.json").toFile();
    }

    public void save() {
        try {
            if (!configDir.toFile().exists()) {
                configDir.toFile().mkdirs();
            }

            JsonObject root = new JsonObject();

            for (Module module : ModuleManager.INSTANCE.getModules()) {
                JsonObject moduleObj = new JsonObject();
                moduleObj.addProperty("enabled", module.isEnabled());
                moduleObj.addProperty("key", module.getKey());

                JsonObject settingsObj = new JsonObject();
                for (Setting<?> setting : module.getSettings()) {
                    if (setting instanceof BoolSetting boolSetting) {
                        settingsObj.addProperty(setting.getName(), boolSetting.get());
                    } else if (setting instanceof IntSetting intSetting) {
                        settingsObj.addProperty(setting.getName(), intSetting.get());
                    } else if (setting instanceof DoubleSetting doubleSetting) {
                        settingsObj.addProperty(setting.getName(), doubleSetting.get());
                    } else if (setting instanceof ColorSetting colorSetting) {
                        SettingColor color = colorSetting.get();
                        JsonObject colorObj = new JsonObject();
                        colorObj.addProperty("r", color.r);
                        colorObj.addProperty("g", color.g);
                        colorObj.addProperty("b", color.b);
                        colorObj.addProperty("a", color.a);
                        settingsObj.add(setting.getName(), colorObj);
                    } else if (setting instanceof EnumSetting<?> enumSetting) {
                        settingsObj.addProperty(setting.getName(), ((Enum<?>) enumSetting.get()).name());
                    }
                }
                moduleObj.add("settings", settingsObj);
                root.add(module.getName(), moduleObj);
            }

            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            MazaClient.LOG.error("Failed to save Maza configuration: {}", e.getMessage());
        }
    }

    public void load() {
        if (!configFile.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            for (Module module : ModuleManager.INSTANCE.getModules()) {
                if (root.has(module.getName())) {
                    JsonObject moduleObj = root.getAsJsonObject(module.getName());

                    if (moduleObj.has("key")) {
                        module.setKey(moduleObj.get("key").getAsInt());
                    }

                    if (moduleObj.has("settings")) {
                        JsonObject settingsObj = moduleObj.getAsJsonObject("settings");
                        for (Setting<?> setting : module.getSettings()) {
                            if (settingsObj.has(setting.getName())) {
                                JsonElement el = settingsObj.get(setting.getName());
                                try {
                                    applySettingValue(setting, el);
                                } catch (Exception ignored) {}
                            }
                        }
                    }

                    if (moduleObj.has("enabled")) {
                        boolean enabled = moduleObj.get("enabled").getAsBoolean();
                        if (enabled != module.isEnabled()) {
                            module.setEnabled(enabled);
                        }
                    }
                }
            }
        } catch (Exception e) {
            MazaClient.LOG.error("Failed to load Maza configuration: {}", e.getMessage());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applySettingValue(Setting<?> setting, JsonElement el) {
        if (setting instanceof BoolSetting boolSetting && el.isJsonPrimitive()) {
            boolSetting.set(el.getAsBoolean());
        } else if (setting instanceof IntSetting intSetting && el.isJsonPrimitive()) {
            intSetting.set(el.getAsInt());
        } else if (setting instanceof DoubleSetting doubleSetting && el.isJsonPrimitive()) {
            doubleSetting.set(el.getAsDouble());
        } else if (setting instanceof ColorSetting colorSetting && el.isJsonObject()) {
            JsonObject c = el.getAsJsonObject();
            int r = c.has("r") ? c.get("r").getAsInt() : 255;
            int g = c.has("g") ? c.get("g").getAsInt() : 255;
            int b = c.has("b") ? c.get("b").getAsInt() : 255;
            int a = c.has("a") ? c.get("a").getAsInt() : 255;
            colorSetting.set(new SettingColor(r, g, b, a));
        } else if (setting instanceof EnumSetting enumSetting && el.isJsonPrimitive()) {
            Enum<?> current = (Enum<?>) enumSetting.get();
            Class enumClass = current.getClass();
            try {
                Enum<?> parsed = Enum.valueOf(enumClass, el.getAsString());
                enumSetting.set(parsed);
            } catch (Exception ignored) {}
        }
    }
}
