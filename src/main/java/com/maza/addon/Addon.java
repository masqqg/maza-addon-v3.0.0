package com.maza.addon;

import com.maza.addon.modules.StorageESP;
import com.maza.addon.modules.SusChunkFinder;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class Addon extends MeteorAddon {

    @Override
    public void onInitialize() {
        Modules.get().add(new SusChunkFinder());
        Modules.get().add(new StorageESP());
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
