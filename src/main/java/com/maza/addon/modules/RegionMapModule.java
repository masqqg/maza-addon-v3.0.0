package com.maza.addon.modules;

import com.maza.addon.MazaCategory;
import com.maza.addon.hud.RegionMapHud;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.hud.screens.HudEditorScreen;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import java.util.HashMap;
import java.util.Map;

import static com.maza.addon.hud.RegionMapHud.DEFAULT_COLORS;
import static com.maza.addon.hud.RegionMapHud.REGIONS;

/**
 * Region Map: shows the DonutSMP shard map on screen.
 *
 * Based on the CC0 "Region Map" addon by Jeff (Meteor addon template, CC0 1.0).
 * Added: ocean style settings.
 */
public class RegionMapModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgOcean = settings.createGroup("Ocean Style");
    private final SettingGroup sgColors = settings.createGroup("Region Colors");
    private final SettingGroup sgNumbers = settings.createGroup("Shard Numbers");
    private final SettingGroup sgMarker = settings.createGroup("Marker");

    public final Map<String, Setting<SettingColor>> regionColors = new HashMap<>();

    // ---- general
    public final Setting<Double> cellSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("cell-size")
        .description("Size of one grid box in pixels.")
        .defaultValue(4).min(1).sliderRange(1, 12).build());

    public final Setting<Boolean> showKey = sgGeneral.add(new BoolSetting.Builder()
        .name("show-key")
        .description("Shows the color key under the map.")
        .defaultValue(true).build());

    public final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("text-color")
        .defaultValue(new SettingColor(220, 245, 255)).build());

    public final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").defaultValue(true).build());

    public final Setting<Boolean> shardBorders = sgGeneral.add(new BoolSetting.Builder()
        .name("goliath-borders")
        .description("Draws lines between shards inside the same region.")
        .defaultValue(true).build());

    public final Setting<SettingColor> shardBorderColor = sgGeneral.add(new ColorSetting.Builder()
        .name("goliath-border-color")
        .defaultValue(new SettingColor(0, 0, 0, 90))
        .visible(shardBorders::get).build());

    // ---- ocean style
    public final Setting<Boolean> oceanStyle = sgOcean.add(new BoolSetting.Builder()
        .name("ocean-style")
        .description("Deep blue striped panel with a bright frame, and a water tint over the map.")
        .defaultValue(true).build());

    public final Setting<Boolean> showTitle = sgOcean.add(new BoolSetting.Builder()
        .name("show-title")
        .defaultValue(true)
        .visible(oceanStyle::get).build());

    public final Setting<SettingColor> oceanColor = sgOcean.add(new ColorSetting.Builder()
        .name("ocean-color")
        .description("Base colour of the panel.")
        .defaultValue(new SettingColor(6, 30, 62, 225))
        .visible(oceanStyle::get).build());

    public final Setting<SettingColor> waveColor = sgOcean.add(new ColorSetting.Builder()
        .name("wave-color")
        .description("Colour of the lighter stripes. They fade towards the bottom.")
        .defaultValue(new SettingColor(30, 110, 180, 120))
        .visible(oceanStyle::get).build());

    public final Setting<SettingColor> frameColor = sgOcean.add(new ColorSetting.Builder()
        .name("frame-color")
        .defaultValue(new SettingColor(90, 210, 255, 235))
        .visible(oceanStyle::get).build());

    public final Setting<SettingColor> tintColor = sgOcean.add(new ColorSetting.Builder()
        .name("water-tint")
        .description("Blue tint drawn over the whole map.")
        .defaultValue(new SettingColor(0, 120, 210, 50))
        .visible(oceanStyle::get).build());

    public final Setting<Double> stripeSize = sgOcean.add(new DoubleSetting.Builder()
        .name("stripe-size")
        .description("Height of one stripe in pixels.")
        .defaultValue(2).min(1).sliderRange(1, 6)
        .visible(oceanStyle::get).build());

    // ---- shard numbers
    public final Setting<Boolean> showNumbers = sgNumbers.add(new BoolSetting.Builder()
        .name("show-numbers")
        .description("Shows each shard's number in its centre. Numbers start at 1 in every region.")
        .defaultValue(true).build());

    public final Setting<Double> numberScale = sgNumbers.add(new DoubleSetting.Builder()
        .name("number-scale")
        .description("Maximum text scale.")
        .defaultValue(0.6).min(0.1).sliderRange(0.1, 2)
        .visible(showNumbers::get).build());

    public final Setting<SettingColor> numberColor = sgNumbers.add(new ColorSetting.Builder()
        .name("number-color")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(showNumbers::get).build());

    // ---- marker
    public final Setting<Boolean> showMarker = sgMarker.add(new BoolSetting.Builder()
        .name("show-marker")
        .description("Shows your position on the map.")
        .defaultValue(true).build());

    public final Setting<SettingColor> markerColor = sgMarker.add(new ColorSetting.Builder()
        .name("marker-color")
        .description("Color of the direction line.")
        .defaultValue(new SettingColor(170, 0, 0))
        .visible(showMarker::get).build());

    public final Setting<SettingColor> pivotColor = sgMarker.add(new ColorSetting.Builder()
        .name("pivot-color")
        .description("Color of the pixel at your position.")
        .defaultValue(new SettingColor(255, 70, 70))
        .visible(showMarker::get).build());

    public final Setting<Double> markerLength = sgMarker.add(new DoubleSetting.Builder()
        .name("marker-length")
        .description("Length of the direction line in pixels.")
        .defaultValue(8).min(1).sliderRange(2, 30)
        .visible(showMarker::get).build());

    public final Setting<Double> markerThickness = sgMarker.add(new DoubleSetting.Builder()
        .name("marker-thickness")
        .description("Thickness of the line and size of the pivot pixel.")
        .defaultValue(1.5).min(1).sliderRange(1, 5)
        .visible(showMarker::get).build());

    public RegionMapModule() {
        super(MazaCategory.INSTANCE, "region-map", "Shows the DonutSMP shard region map on screen.");

        for (String region : REGIONS.keySet()) {
            regionColors.put(region, sgColors.add(new ColorSetting.Builder()
                .name(region.toLowerCase().replace(' ', '-') + "-color")
                .description("Color for " + region + ".")
                .defaultValue(DEFAULT_COLORS.getOrDefault(region, new SettingColor(255, 255, 255)))
                .build()
            ));
        }
    }

    @Override
    public void onActivate() {
        Hud hud = Hud.get();

        // Reuse the element if it's already placed
        for (HudElement element : hud) {
            if (element instanceof RegionMapHud) {
                if (!element.isActive()) element.toggle();
                return;
            }
        }

        // Otherwise add it in the top-left corner
        hud.add(RegionMapHud.INFO, 4, 4);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.currentScreen instanceof HudEditorScreen) return; // the editor draws it already
        if (mc.options.hudHidden) return;                        // still respect F1

        for (HudElement element : Hud.get()) {
            if (element instanceof RegionMapHud map && map.isActive()) {
                RegionMapHud.renderingFromModule = true;
                try {
                    // Pass 1: panel, map, borders, numbers, key
                    HudRenderer.INSTANCE.begin(event.drawContext);
                    map.render(HudRenderer.INSTANCE);
                    HudRenderer.INSTANCE.end();

                    // Pass 2: marker, so it draws over the text from pass 1
                    HudRenderer.INSTANCE.begin(event.drawContext);
                    map.renderMarker(HudRenderer.INSTANCE);
                    HudRenderer.INSTANCE.end();
                } finally {
                    RegionMapHud.renderingFromModule = false;
                }
                return;
            }
        }
    }
}
