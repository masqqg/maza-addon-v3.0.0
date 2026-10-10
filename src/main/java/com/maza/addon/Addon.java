package com.maza.addon;

import com.maza.addon.hud.RegionMapHud;
import com.maza.addon.modules.AntiVanish;
import com.maza.addon.modules.NetheriteFinderPlus;
import com.maza.addon.modules.PlayerBypass;
import com.maza.addon.modules.PrimeChunkFinder;
import com.maza.addon.modules.RegionMapModule;
import com.maza.addon.modules.SpawnerFlag;
import com.maza.addon.modules.StorageESP;
import com.maza.addon.modules.SusChunkFinder;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Addon extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("Maza Addon");
    public static final Category CATEGORY = MazaCategory.INSTANCE;
    public static final HudGroup HUD_GROUP = new HudGroup("Maza");

    @Override
    public void onInitialize() {
        LOG.info("Maza Addon basariyla yuklendi!");

        Modules modules = Modules.get();
        modules.add(new SusChunkFinder());
        modules.add(new NetheriteFinderPlus());
        modules.add(new PrimeChunkFinder());
        modules.add(new StorageESP());
        modules.add(new AntiVanish());
        modules.add(new PlayerBypass());
        modules.add(new SpawnerFlag());
        modules.add(new RegionMapModule());

        Hud.get().register(RegionMapHud.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(MazaCategory.INSTANCE);
    }

    @Override
    public String getPackage() {
        return "com.maza.addon";
    }
}
