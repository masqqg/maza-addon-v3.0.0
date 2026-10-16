package com.maza.client.module.modules.client;

import com.maza.client.MazaClient;
import com.maza.client.event.EventHandler;
import com.maza.client.event.Render2DEvent;
import com.maza.client.module.Category;
import com.maza.client.module.Module;
import com.maza.client.module.ModuleManager;
import com.maza.client.setting.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Direction;

import java.text.SimpleDateFormat;
import java.util.*;

public class HUD extends Module {

    public enum ArrayListMode {
        NORMAL, RAINBOW, GRADIENT
    }

    public enum WatermarkMode {
        SIMPLE, BOX, CSGO
    }

    // Watermark
    public final BoolSetting watermark = addSetting(new BoolSetting.Builder()
            .name("Watermark")
            .description("Displays client name and version")
            .defaultValue(true)
            .build());

    public final EnumSetting<WatermarkMode> watermarkMode = addSetting(new EnumSetting.Builder<WatermarkMode>()
            .name("Watermark Style")
            .description("Visual style of watermark")
            .defaultValue(WatermarkMode.BOX)
            .visible(watermark::get)
            .build());

    // ArrayList
    public final BoolSetting arrayList = addSetting(new BoolSetting.Builder()
            .name("ArrayList")
            .description("Displays enabled modules list")
            .defaultValue(true)
            .build());

    public final EnumSetting<ArrayListMode> arrayListMode = addSetting(new EnumSetting.Builder<ArrayListMode>()
            .name("ArrayList Color")
            .description("Color animation style")
            .defaultValue(ArrayListMode.GRADIENT)
            .visible(arrayList::get)
            .build());

    // Info HUD Elements
    public final BoolSetting showFps = addSetting(new BoolSetting.Builder()
            .name("FPS")
            .description("Show FPS counter")
            .defaultValue(true)
            .build());

    public final BoolSetting showCoords = addSetting(new BoolSetting.Builder()
            .name("Coordinates")
            .description("Show XYZ coordinates")
            .defaultValue(true)
            .build());

    public final BoolSetting showDirection = addSetting(new BoolSetting.Builder()
            .name("Direction")
            .description("Show facing direction")
            .defaultValue(true)
            .build());

    public final BoolSetting showPing = addSetting(new BoolSetting.Builder()
            .name("Ping")
            .description("Show server ping")
            .defaultValue(true)
            .build());

    public final BoolSetting showSpeed = addSetting(new BoolSetting.Builder()
            .name("Speed")
            .description("Show movement speed (b/s)")
            .defaultValue(true)
            .build());

    public final BoolSetting showTime = addSetting(new BoolSetting.Builder()
            .name("Clock")
            .description("Show real-world clock")
            .defaultValue(true)
            .build());

    public final BoolSetting showServer = addSetting(new BoolSetting.Builder()
            .name("Server")
            .description("Show current server address")
            .defaultValue(true)
            .build());

    public final BoolSetting showBiome = addSetting(new BoolSetting.Builder()
            .name("Biome")
            .description("Show current biome")
            .defaultValue(false)
            .build());

    // Styling
    public final ColorSetting primaryColor = addSetting(new ColorSetting.Builder()
            .name("Primary Color")
            .description("Main accent color")
            .defaultValue(new SettingColor(124, 77, 255, 255))
            .build());

    public final ColorSetting secondaryColor = addSetting(new ColorSetting.Builder()
            .name("Secondary Color")
            .description("Secondary gradient color")
            .defaultValue(new SettingColor(255, 80, 180, 255))
            .build());

    public final ColorSetting infoColor = addSetting(new ColorSetting.Builder()
            .name("Info Label Color")
            .description("Color for labels")
            .defaultValue(new SettingColor(200, 200, 220, 255))
            .build());

    public HUD() {
        super("HUD", "In-game heads-up display", Category.RENDER);
        setEnabled(true);
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (mc.options.hudHidden) return;
        DrawContext context = event.drawContext;

        int mainColor = primaryColor.get().getPacked();

        // 1. Watermark
        if (watermark.get()) {
            renderWatermark(context);
        }

        // 2. ArrayList
        if (arrayList.get()) {
            renderArrayList(context, event.width);
        }

        // 3. Info Elements (Bottom Left)
        renderInfoList(context, event.height);
    }

    private void renderWatermark(DrawContext context) {
        String title = "Guide " + MazaClient.MOD_NAME;
        String version = "v" + MazaClient.MOD_VERSION;
        int mainColor = primaryColor.get().getPacked();

        switch (watermarkMode.get()) {
            case SIMPLE -> {
                String wm = title + " §7" + version;
                context.drawTextWithShadow(mc.textRenderer, wm, 6, 6, mainColor);
            }
            case BOX -> {
                String wm = title + " §7| " + version;
                int textW = mc.textRenderer.getWidth(wm);
                int bg = 0xD0121218;
                context.fill(4, 4, 10 + textW + 4, 4 + 14, bg);
                context.fill(4, 4, 10 + textW + 4, 5, mainColor); // top line accent
                context.drawTextWithShadow(mc.textRenderer, wm, 8, 8, 0xFFFFFFFF);
            }
            case CSGO -> {
                String user = mc.player != null ? mc.player.getName().getString() : "User";
                String fps = mc.getCurrentFps() + " FPS";
                String ping = getPing() + "ms";
                String full = title + " §7| §f" + user + " §7| §f" + fps + " §7| §f" + ping;
                int textW = mc.textRenderer.getWidth(full);
                int bg = 0xDD101015;

                context.fill(5, 5, 11 + textW, 20, bg);
                context.fill(5, 5, 7, 20, mainColor); // side vertical accent
                context.drawTextWithShadow(mc.textRenderer, full, 10, 8, mainColor);
            }
        }
    }

    private void renderArrayList(DrawContext context, int screenWidth) {
        List<Module> active = new ArrayList<>();
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m.isEnabled() && !(m instanceof HUD) && !(m instanceof ClickGUI)) {
                active.add(m);
            }
        }

        active.sort(Comparator.comparingInt(m -> -mc.textRenderer.getWidth(m.getName())));

        int y = 4;
        long time = System.currentTimeMillis();

        for (int i = 0; i < active.size(); i++) {
            Module m = active.get(i);
            String name = m.getName();
            int textW = mc.textRenderer.getWidth(name);
            int x = screenWidth - textW - 6;

            int color;
            if (arrayListMode.get() == ArrayListMode.RAINBOW) {
                float hue = ((time / 10) + (i * 15)) % 360 / 360.0f;
                color = java.awt.Color.HSBtoRGB(hue, 0.7f, 1.0f) | 0xFF000000;
            } else if (arrayListMode.get() == ArrayListMode.GRADIENT) {
                float ratio = (float) i / Math.max(1, active.size() - 1);
                color = interpolateColor(primaryColor.get().getPacked(), secondaryColor.get().getPacked(), ratio);
            } else {
                color = primaryColor.get().getPacked();
            }

            // Dark background panel
            context.fill(x - 3, y - 1, screenWidth - 2, y + 10, 0xAA101016);
            // Right edge bar
            context.fill(screenWidth - 2, y - 1, screenWidth, y + 10, color);
            // Text
            context.drawTextWithShadow(mc.textRenderer, name, x - 1, y, color);

            y += 11;
        }
    }

    private void renderInfoList(DrawContext context, int screenHeight) {
        List<String> lines = new ArrayList<>();

        if (showFps.get()) {
            lines.add("§7FPS: §f" + mc.getCurrentFps());
        }

        if (showPing.get()) {
            lines.add("§7Ping: §f" + getPing() + "ms");
        }

        if (showCoords.get() && mc.player != null) {
            lines.add(String.format(Locale.ROOT, "§7XYZ: §f%.1f, %.1f, %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        }

        if (showDirection.get() && mc.player != null) {
            Direction dir = mc.player.getHorizontalFacing();
            String axis = switch (dir) {
                case NORTH -> "-Z";
                case SOUTH -> "+Z";
                case WEST -> "-X";
                case EAST -> "+X";
                default -> "";
            };
            lines.add("§7Facing: §f" + dir.name() + " §7(" + axis + ")");
        }

        if (showSpeed.get() && mc.player != null) {
            double vx = mc.player.getVelocity().x;
            double vz = mc.player.getVelocity().z;
            double bps = Math.sqrt(vx * vx + vz * vz) * 20.0;
            lines.add(String.format(Locale.ROOT, "§7Speed: §f%.2f b/s", bps));
        }

        if (showServer.get()) {
            String server = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Singleplayer";
            lines.add("§7Server: §f" + server);
        }

        if (showBiome.get() && mc.world != null && mc.player != null) {
            String biome = mc.world.getBiome(mc.player.getBlockPos()).getKey()
                    .map(key -> key.getValue().getPath()).orElse("Unknown");
            lines.add("§7Biome: §f" + capitalize(biome));
        }

        if (showTime.get()) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            lines.add("§7Time: §f" + sdf.format(new Date()));
        }

        int y = screenHeight - (lines.size() * 11) - 6;
        for (String line : lines) {
            context.drawTextWithShadow(mc.textRenderer, line, 5, y, 0xFFFFFFFF);
            y += 11;
        }
    }

    private int getPing() {
        if (mc.getNetworkHandler() != null && mc.player != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) {
                return entry.getLatency();
            }
        }
        return 0;
    }

    private int interpolateColor(int c1, int c2, float ratio) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        String[] parts = str.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }
}
