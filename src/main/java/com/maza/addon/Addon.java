package com.maza.addon;

import com.maza.addon.modules.ActivityDebug;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class Addon extends MeteorAddon {
    @Override
    public void onInitialize() {
        Modules.get().add(new ActivityDebug());
    }

    @Override
    public String getPackage() {
        return "com.maza.addon";
    }
}

