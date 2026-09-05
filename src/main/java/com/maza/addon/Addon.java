package com.maza.addon;

import com.maza.addon.modules.ActivityDebug;
import com.maza.addon.modules.AirBypass;
import com.maza.addon.modules.SpeedMineBypass;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class Addon extends MeteorAddon {
    @Override
    public void onInitialize() {
        Modules.get().add(new ActivityDebug());
        Modules.get().add(new AirBypass());
        Modules.get().add(new SpeedMineBypass());
    }

    @Override
    public void onRegisterCategories() {
        super.onRegisterCategories();
        Modules.registerCategory(MazaCategory.INSTANCE);
    }

    @Override
    public String getPackage() {
        return "com.maza.addon";
    }
}
