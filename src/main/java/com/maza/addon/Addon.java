package com.maza.addon;

import meteordevelopment.meteorclient.addons.GithubFolder;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Addon extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("Maza Addon");
    public static final Category CATEGORY = new Category("Maza");
    public static final HudGroup HUD_GROUP = new HudGroup("Maza");

    @Override
    public void onInitialize() {
        LOG.info("Maza Addon basariyla yuklendi!");
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.maza.addon";
    }

    @Override
    public GithubFolder getGithubFolder() {
        return new GithubFolder("kullanici-adi", "maza-addon", "main");
    }
}
