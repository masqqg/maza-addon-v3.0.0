package com.maza.addon;

import com.maza.addon.modules.ActivityDebug;
import com.maza.addon.modules.MovementDebug;
import com.maza.addon.modules.EntityTracker;
import com.maza.addon.modules.SpeedMineBypass;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class MazaAddon extends MeteorAddon {
    @Override
    public void onInitialize() {
        Categories.add(MazaCategory.INSTANCE);
        
        Modules.get().add(new ActivityDebug());
        Modules.get().add(new MovementDebug());
        Modules.get().add(new EntityTracker());
        Modules.get().add(new SpeedMineBypass());
    }

    @Override
    public void onRegisterCategories() {
        Categories.add(MazaCategory.INSTANCE);
    }

    @Override
    public String getWebsite() {
        return null;
    }

    @Override
    public String getGithubRepo() {
        return null;
    }

    @Override
    public String getPackage() {
        return "com.maza.addon";
    }
}
