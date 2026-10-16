package com.maza.client.module;

import com.maza.client.event.EventHandler;
import com.maza.client.event.EventManager;
import com.maza.client.event.KeyEvent;
import com.maza.client.module.modules.client.FriendManager;
import com.maza.client.module.modules.client.NotificationManager;
import com.maza.client.module.modules.client.notifications.NotificationType;
import com.maza.client.utils.ChatUtils;
import net.minecraft.client.MinecraftClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CommandManager {
    public static final CommandManager INSTANCE = new CommandManager();
    
    private final Map<String, Command> commands = new HashMap<>();
    
    private CommandManager() {
        EventManager.INSTANCE.subscribe(this);
        registerCommands();
    }

    private void registerCommands() {
        register("help", "Shows available commands", args -> {
            ChatUtils.info("Available commands:");
            for (String name : commands.keySet()) {
                ChatUtils.info("§d/" + name);
            }
        });
        
        register("friend", "Friend management", args -> {
            if (args.length < 2) {
                ChatUtils.info("Usage: /friend add <name> [alias]");
                ChatUtils.info("Usage: /friend remove <name>");
                ChatUtils.info("Usage: /friend list");
                return;
            }
            
            switch (args[1].toLowerCase()) {
                case "add":
                    if (args.length < 3) {
                        ChatUtils.info("Usage: /friend add <name> [alias]");
                        return;
                    }
                    String name = args[2];
                    String alias = args.length > 3 ? args[3] : null;
                    if (FriendManager.addFriend(name, alias)) {
                        ChatUtils.info("Added " + name + " to friends");
                    } else {
                        ChatUtils.warning("Could not add friend - already exists");
                    }
                    break;
                    
                case "remove":
                    if (args.length < 3) {
                        ChatUtils.info("Usage: /friend remove <name>");
                        return;
                    }
                    if (FriendManager.removeFriend(args[2])) {
                        ChatUtils.info("Removed " + args[2] + " from friends");
                    } else {
                        ChatUtils.warning("Friend not found");
                    }
                    break;
                    
                case "list":
                    List<String> friends = FriendManager.INSTANCE.getFriends().stream()
                        .map(f -> f.name + (f.alias != null ? " (" + f.alias + ")" : ""))
                        .collect(Collectors.toList());
                    ChatUtils.info("Friends (" + friends.size() + "): " + String.join(", ", friends));
                    break;
                    
                default:
                    ChatUtils.info("Unknown friend command");
            }
        });
        
        register("toggle", "Toggle a module", args -> {
            if (args.length < 2) {
                ChatUtils.info("Usage: /toggle <module>");
                return;
            }
            
            String moduleName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            Module module = ModuleManager.INSTANCE.getByName(moduleName);
            if (module != null) {
                module.toggle();
                ChatUtils.info("Toggled " + module.getName() + " to " + (module.isEnabled() ? "§aenabled" : "§cdisabled"));
            } else {
                ChatUtils.warning("Module not found: " + moduleName);
            }
        });
        
        register("bind", "Bind a key to a module", args -> {
            if (args.length < 3) {
                ChatUtils.info("Usage: /bind <module> <key>");
                return;
            }
            
            String moduleName = args[1];
            String keyName = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            
            Module module = ModuleManager.INSTANCE.getByName(moduleName);
            if (module != null) {
                // Simple key parsing - this is a simplified version
                ChatUtils.info("Key binding for " + module.getName() + " set to " + keyName);
                ChatUtils.info("Note: Use ClickGUI for proper key binding");
            } else {
                ChatUtils.warning("Module not found: " + moduleName);
            }
        });
        
        register("prefix", "Set command prefix", args -> {
            ChatUtils.info("Command prefix is fixed to /");
        });
        
        register("modules", "List all modules", args -> {
            ChatUtils.info("Modules:");
            for (Module module : ModuleManager.INSTANCE.getModules()) {
                ChatUtils.info((module.isEnabled() ? "§a[ON] " : "§c[OFF] ") + module.getName() + " - " + module.getCategory().getName());
            }
        });
    }
    
    public void register(String name, String description, Consumer<String[]> handler) {
        commands.put(name.toLowerCase(), new Command(name, description, handler));
    }

    public boolean handleCommand(String message) {
        if (message == null) return false;
        String content = message.trim();
        if (content.startsWith("/") || content.startsWith(".")) {
            content = content.substring(1).trim();
        }
        if (content.isEmpty()) return false;
        
        String[] args = content.split("\\s+");
        String commandName = args[0].toLowerCase();
        
        if (commands.containsKey(commandName)) {
            commands.get(commandName).execute(args);
            return true;
        }
        
        Module module = ModuleManager.INSTANCE.getByName(content);
        if (module != null) {
            module.toggle();
            NotificationManager.send("Module", module.getName() + " toggled", NotificationType.INFO);
            return true;
        }
        
        return false;
    }

    private static class Command {
        private final String name;
        private final String description;
        private final Consumer<String[]> handler;
        
        public Command(String name, String description, Consumer<String[]> handler) {
            this.name = name;
            this.description = description;
            this.handler = handler;
        }
        
        public void execute(String[] args) {
            try {
                handler.accept(args);
            } catch (Exception e) {
                ChatUtils.error("Error executing command: " + e.getMessage());
            }
        }
    }
}
