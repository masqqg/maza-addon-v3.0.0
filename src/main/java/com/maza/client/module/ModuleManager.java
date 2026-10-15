package com.maza.client.module;

import com.maza.client.event.EventHandler;
import com.maza.client.event.EventManager;
import com.maza.client.event.KeyEvent;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ModuleManager {
    public static final ModuleManager INSTANCE = new ModuleManager();

    private final List<Module> modules = new CopyOnWriteArrayList<>();

    private ModuleManager() {
        EventManager.INSTANCE.subscribe(this);
    }

    public void register(Module module) {
        if (module != null && !modules.contains(module)) {
            modules.add(module);
        }
    }

    public void register(Module... mods) {
        for (Module m : mods) {
            register(m);
        }
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> clazz) {
        for (Module module : modules) {
            if (module.getClass().equals(clazz)) {
                return (T) module;
            }
        }
        return null;
    }

    public Module getByName(String name) {
        for (Module module : modules) {
            if (module.getName().equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }

    public List<Module> getByCategory(Category category) {
        List<Module> list = new ArrayList<>();
        for (Module module : modules) {
            if (module.getCategory() == category) {
                list.add(module);
            }
        }
        return list;
    }

    @EventHandler
    public void onKey(KeyEvent event) {
        if (MinecraftClient.getInstance().currentScreen != null) return;
        if (event.action != GLFW.GLFW_PRESS) return;

        for (Module module : modules) {
            if (module.getKey() != GLFW.GLFW_KEY_UNKNOWN && module.getKey() == event.key) {
                module.toggle();
            }
        }
    }
}
