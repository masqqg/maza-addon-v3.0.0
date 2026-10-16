package com.maza.client;

import com.maza.client.config.ConfigManager;
import com.maza.client.event.EventManager;
import com.maza.client.module.CommandManager;
import com.maza.client.module.ModuleManager;
import com.maza.client.module.modules.client.ClickGUI;
import com.maza.client.module.modules.client.FriendManager;
import com.maza.client.module.modules.client.HUD;
import com.maza.client.module.modules.client.NotificationManager;
import com.maza.client.module.modules.misc.AntiVanish;
import com.maza.client.module.modules.render.RegionMapHud;
import com.maza.client.module.modules.render.RegionMapModule;
import com.maza.client.module.modules.render.StorageESP;
import com.maza.client.module.modules.world.NetheriteFinderPlus;
import com.maza.client.module.modules.world.PlayerBypass;
import com.maza.client.module.modules.world.PrimeChunkFinder;
import com.maza.client.module.modules.world.SpawnerFlag;
import com.maza.client.module.modules.world.SusChunkFinder;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MazaClient implements ClientModInitializer {
    public static final String MOD_ID = "maza";
    public static final String MOD_NAME = "Maza";
    public static final String MOD_VERSION = "3.0.0";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    public static MazaClient INSTANCE;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        LOG.info("Initializing {} v{}", MOD_NAME, MOD_VERSION);

        // Core systems
        EventManager eventManager = EventManager.INSTANCE;
        ModuleManager moduleManager = ModuleManager.INSTANCE;
        CommandManager commandManager = CommandManager.INSTANCE;

        // Register all modules
        moduleManager.register(
            // Client
            new HUD(),
            new ClickGUI(),
            new NotificationManager(),
            new FriendManager(),

            // Render
            new StorageESP(),
            new RegionMapModule(),
            new RegionMapHud(),

            // World
            new PlayerBypass(),
            new SpawnerFlag(),
            new SusChunkFinder(),
            new PrimeChunkFinder(),
            new NetheriteFinderPlus(),

            // Misc
            new AntiVanish()
        );

        // Load saved configuration
        ConfigManager.INSTANCE.load();

        // Save config on JVM shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ConfigManager.INSTANCE.save();
        }));

        LOG.info("{} initialized with {} modules.", MOD_NAME, moduleManager.getModules().size());
    }
}
