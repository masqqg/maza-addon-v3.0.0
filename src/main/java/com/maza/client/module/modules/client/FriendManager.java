package com.maza.client.module.modules.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.maza.client.MazaClient;
import com.maza.client.module.Category;
import com.maza.client.module.Module;
import com.maza.client.setting.BoolSetting;
import com.maza.client.setting.ColorSetting;
import com.maza.client.setting.SettingColor;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FriendManager extends Module {
    public static final FriendManager INSTANCE = new FriendManager();
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Friend>>() {}.getType();
    
    private final Path friendsFile;
    private final List<Friend> friends = new ArrayList<>();
    
    public final BoolSetting highlightFriends = addSetting(new BoolSetting.Builder()
        .name("Highlight Friends")
        .description("Highlight friend names in chat")
        .defaultValue(true)
        .build());
    
    public final ColorSetting friendColor = addSetting(new ColorSetting.Builder()
        .name("Friend Color")
        .description("Color for friend names")
        .defaultValue(new SettingColor(60, 255, 140))
        .build());
    
    public final BoolSetting hideVanished = addSetting(new BoolSetting.Builder()
        .name("Hide Vanished")
        .description("Hide vanished players")
        .defaultValue(false)
        .build());
    
    public FriendManager() {
        super("Friends", "Manage friends", Category.CLIENT);
        this.friendsFile = FabricLoader.getInstance().getConfigDir().resolve("maza/friends.json");
        load();
        setEnabled(true);
    }
    
    public static boolean isFriend(String name) {
        return INSTANCE.friends.stream()
            .anyMatch(f -> f.name.equalsIgnoreCase(name));
    }
    
    public static boolean isFriend(net.minecraft.entity.player.PlayerEntity player) {
        if (player == null) return false;
        return isFriend(player.getName().getString());
    }
    
    public static String getFriendName(String name) {
        for (Friend friend : INSTANCE.friends) {
            if (friend.name.equalsIgnoreCase(name)) {
                return friend.alias != null && !friend.alias.isEmpty() ? friend.alias : friend.name;
            }
        }
        return name;
    }
    
    public static Friend getFriend(String name) {
        return INSTANCE.friends.stream()
            .filter(f -> f.name.equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }
    
    public static boolean addFriend(String name) {
        return INSTANCE.addFriendInternal(name, null);
    }
    
    public static boolean addFriend(String name, String alias) {
        return INSTANCE.addFriendInternal(name, alias);
    }
    
    public static boolean removeFriend(String name) {
        return INSTANCE.removeFriendInternal(name);
    }
    
    private boolean addFriendInternal(String name, String alias) {
        if (name == null || name.isEmpty()) return false;
        
        for (Friend friend : friends) {
            if (friend.name.equalsIgnoreCase(name)) {
                return false;
            }
        }
        
        friends.add(new Friend(name, alias, System.currentTimeMillis()));
        save();
        return true;
    }
    
    private boolean removeFriendInternal(String name) {
        Friend toRemove = null;
        for (Friend friend : friends) {
            if (friend.name.equalsIgnoreCase(name)) {
                toRemove = friend;
                break;
            }
        }
        
        if (toRemove != null) {
            friends.remove(toRemove);
            save();
            return true;
        }
        return false;
    }
    
    public List<Friend> getFriends() {
        return new ArrayList<>(friends);
    }
    
    public int getFriendCount() {
        return friends.size();
    }
    
    private void load() {
        File file = friendsFile.toFile();
        if (!file.exists()) return;
        
        try (FileReader reader = new FileReader(file)) {
            List<Friend> loaded = GSON.fromJson(reader, LIST_TYPE);
            if (loaded != null) {
                friends.clear();
                friends.addAll(loaded);
            }
        } catch (Exception e) {
            MazaClient.LOG.error("Failed to load friends", e);
        }
    }
    
    private void save() {
        try {
            File file = friendsFile.toFile();
            File parent = file.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(friends, writer);
            }
        } catch (Exception e) {
            MazaClient.LOG.error("Failed to save friends", e);
        }
    }
    
    public static class Friend {
        public String name;
        public String alias;
        public long addedTime;
        
        public Friend() {}
        
        public Friend(String name, String alias, long addedTime) {
            this.name = name;
            this.alias = alias;
            this.addedTime = addedTime;
        }
        
        public String getDisplayName() {
            return alias != null && !alias.isEmpty() ? alias : name;
        }
    }
}
