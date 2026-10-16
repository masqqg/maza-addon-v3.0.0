package com.maza.client.module.modules.client;

import com.maza.client.gui.ClickGuiScreen;
import com.maza.client.module.Category;
import com.maza.client.module.Module;
import org.lwjgl.glfw.GLFW;

public class ClickGUI extends Module {
    public ClickGUI() {
        super("ClickGUI", "Opens the Maza graphical interface", Category.CLIENT, GLFW.GLFW_KEY_P);
    }

    @Override
    public void onEnable() {
        if (mc.currentScreen == null) {
            mc.setScreen(new ClickGuiScreen());
        }
        disable();
    }
}
