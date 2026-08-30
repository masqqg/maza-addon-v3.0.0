package com.maza.addon;

import com.maza.addon.modules.ActivityDebug;
import com.maza.addon.modules.MovementDebug;
import com.maza.addon.modules.EntityTracker;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class Addon extends MeteorAddon {
    @Override
    public void onInitialize() {
        Modules.get().add(new ActivityDebug());
        Modules.get().add(new MovementDebug());
        Modules.get().add(new EntityTracker());
    }

    @Override
    public String getPackage() {
        return "com.maza.addon";
    }
}
