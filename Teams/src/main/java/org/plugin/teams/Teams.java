package org.plugin.teams;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.plugin.teams.api.TeamsAPI;
import org.plugin.teams.commands.TeamCommand;
import org.plugin.teams.database.DatabaseManager;
import org.plugin.teams.listeners.ChatListener;
import org.plugin.teams.listeners.PlayerJoinListener;
import org.plugin.teams.managers.ConfirmationManager;
import org.plugin.teams.managers.InviteManager;
import org.plugin.teams.managers.TeamManager;
import org.plugin.teams.models.TeamRole;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class Teams extends JavaPlugin {

    private static Teams instance;
    private DatabaseManager databaseManager;
    private TeamManager teamManager;
    private InviteManager inviteManager;
    private ConfirmationManager confirmationManager;
    private ChatListener chatListener;
    private List<String> bannedWords;
    private volatile boolean databaseConnected = false;
    private SimpleDateFormat dateFormat;
    private org.plugin.teams.hooks.TeamsPlaceholderExpansion placeholderExpansion;

    // Message configuration
    private final Map<String, String> messages = new HashMap<>();
    private final Map<TeamRole, RoleFormat> roleFormats = new HashMap<>();

    // Simple vanilla-style prefixes (minimal)
    public static String PREFIX = "";
    public static String ERROR_PREFIX = "§c";
    public static String SUCCESS_PREFIX = "§a";
    public static String INFO_PREFIX = "§e";

    @Override
    public void onEnable() {
        instance = this;

        // Load configuration
        saveDefaultConfig();
        loadBannedWords();
        loadMessages();
        loadDateFormat();

        // Simple console output
        getLogger().info("Teams Plugin v" + getDescription().getVersion());
        getLogger().info("─────────────────────");

        // Check for PlayerStats
        if (getServer().getPluginManager().getPlugin("PlayerStats") == null) {
            getLogger().warning("PlayerStats not found! Playtime restrictions disabled.");
        }

        // Check for ChatPlugin
        if (getServer().getPluginManager().getPlugin("ChatPlugin") != null) {
            getLogger().info("ChatPlugin found! Team chat will support placeholders.");
        } else {
            getLogger().info("ChatPlugin not found. Team chat will work without placeholders.");
        }

        // Check for PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                Class.forName("org.plugin.teams.hooks.TeamsPlaceholderExpansion");
                placeholderExpansion = new org.plugin.teams.hooks.TeamsPlaceholderExpansion(this);
                placeholderExpansion.register();
                getLogger().info("PlaceholderAPI hooked successfully!");
            } catch (ClassNotFoundException e) {
                getLogger().warning("PlaceholderAPI found but expansion class not available");
            }
        }

        // Initialize database asynchronously to prevent startup blocking
        getLogger().info("Initializing database connection...");
        CompletableFuture.supplyAsync(() -> {
            databaseManager = new DatabaseManager(this);
            return databaseManager.initialize();
        }).thenAccept(success -> {
            if (!success) {
                getLogger().severe("Failed to connect to database!");
                getLogger().severe("Plugin functionality disabled.");
                getLogger().severe("Please check your database configuration.");
                databaseConnected = false;

                // Schedule a retry after 30 seconds
                Bukkit.getScheduler().runTaskLaterAsynchronously(this, this::retryDatabaseConnection, 600L);
            } else {
                databaseConnected = true;
                getLogger().info("Database connected successfully!");

                // Initialize managers on main thread
                Bukkit.getScheduler().runTask(this, () -> {
                    try {
                        // Initialize managers only if database is connected
                        teamManager = new TeamManager(this);
                        inviteManager = new InviteManager(this);
                        confirmationManager = new ConfirmationManager(this);

                        // Register listeners
                        chatListener = new ChatListener(this);
                        getServer().getPluginManager().registerEvents(chatListener, this);
                        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

                        // Initialize API
                        TeamsAPI.setPlugin(this);
                        getLogger().info("Teams API initialized!");
                    } catch (Exception e) {
                        getLogger().severe("Failed to initialize plugin components: " + e.getMessage());
                        e.printStackTrace();
                        databaseConnected = false;
                    }
                });
            }
        }).exceptionally(ex -> {
            getLogger().severe("Database initialization failed with exception: " + ex.getMessage());
            ex.printStackTrace();
            databaseConnected = false;
            return null;
        });

        // Register command (always register to show error message)
        getCommand("team").setExecutor(new TeamCommand(this));
        getCommand("team").setTabCompleter(new TeamCommand(this));

        getLogger().info("─────────────────────");
        getLogger().info("Teams plugin startup complete!");
    }

    private void retryDatabaseConnection() {
        if (databaseConnected) {
            return; // Already connected
        }

        getLogger().info("Retrying database connection...");
        CompletableFuture.supplyAsync(() -> {
            if (databaseManager == null) {
                databaseManager = new DatabaseManager(this);
            }
            return databaseManager.initialize();
        }).thenAccept(success -> {
            if (success) {
                databaseConnected = true;
                getLogger().info("Database reconnection successful!");

                // Initialize managers on main thread
                Bukkit.getScheduler().runTask(this, () -> {
                    if (teamManager == null) {
                        teamManager = new TeamManager(this);
                        inviteManager = new InviteManager(this);
                        confirmationManager = new ConfirmationManager(this);

                        // Initialize API
                        TeamsAPI.setPlugin(this);
                        getLogger().info("Plugin components initialized after reconnection!");
                    }
                });
            } else {
                getLogger().warning("Database reconnection failed. Will retry in 60 seconds...");
                // Schedule another retry
                Bukkit.getScheduler().runTaskLaterAsynchronously(this, this::retryDatabaseConnection, 1200L);
            }
        }).exceptionally(ex -> {
            getLogger().severe("Database reconnection failed with exception: " + ex.getMessage());
            // Schedule another retry
            Bukkit.getScheduler().runTaskLaterAsynchronously(this, this::retryDatabaseConnection, 1200L);
            return null;
        });
    }

    @Override
    public void onDisable() {
        getLogger().info("Teams Plugin");
        getLogger().info("─────────────────────");

        // Unregister PlaceholderAPI expansion
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }

        // Close database connection
        if (databaseManager != null) {
            try {
                databaseManager.closeConnection();
                getLogger().info("Database connection closed");
            } catch (Exception e) {
                getLogger().warning("Error closing database: " + e.getMessage());
            }
        }

        getLogger().info("Teams plugin disabled!");
    }

    /**
     * Reload the plugin configuration
     * This method can be called to reload config without restarting the server
     */
    public void reloadConfiguration() {
        // Reload the config file from disk
        reloadConfig();

        // Clear existing data
        messages.clear();
        roleFormats.clear();
        bannedWords.clear();

        // Reload all configuration sections
        loadBannedWords();
        loadMessages();
        loadDateFormat();

        getLogger().info("Configuration reloaded successfully!");
    }

    /**
     * Reload the chat listener to reconnect to ChatPlugin
     */
    public void reloadChatListener() {
        if (chatListener != null) {
            // Unregister old listener
            HandlerList.unregisterAll(chatListener);

            // Reload the listener
            chatListener.reload();

            // Re-register the listener
            getServer().getPluginManager().registerEvents(chatListener, this);

            getLogger().info("Chat listener reloaded successfully!");
        }
    }

    private void loadBannedWords() {
        FileConfiguration config = getConfig();
        bannedWords = config.getStringList("banned-words");
        if (bannedWords.isEmpty()) {
            // Default banned words
            bannedWords = Arrays.asList("fuck", "shit", "ass", "bitch", "dick", "cunt", "nigger", "faggot");
            config.set("banned-words", bannedWords);
            saveConfig();
        }
    }

    private void loadDateFormat() {
        String format = getConfig().getString("date-format", "dd MMM yyyy");
        try {
            dateFormat = new SimpleDateFormat(format, Locale.ENGLISH);
        } catch (Exception e) {
            getLogger().warning("Invalid date format in config! Using default: dd MMM yyyy");
            dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
        }
    }

    private void loadMessages() {
        FileConfiguration config = getConfig();
        ConfigurationSection msgSection = config.getConfigurationSection("messages");

        if (msgSection == null) {
            getLogger().warning("Messages section not found in config! Using defaults.");
            return;
        }

        // Load prefixes (minimal for vanilla look)
        PREFIX = translateColorCodes(msgSection.getString("prefix", ""));
        ERROR_PREFIX = translateColorCodes(msgSection.getString("error-prefix", "&c"));
        SUCCESS_PREFIX = translateColorCodes(msgSection.getString("success-prefix", "&a"));
        INFO_PREFIX = translateColorCodes(msgSection.getString("info-prefix", "&e"));

        // Load all messages recursively
        loadMessagesFromSection(msgSection, "");

        // Load team chat format separately as it's used frequently
        messages.put("team-chat-format", translateColorCodes(msgSection.getString("team-chat-format",
                "&7[Team] &f%player_name%: %message%")));

        // Load role formats
        ConfigurationSection roles = msgSection.getConfigurationSection("roles");
        if (roles != null) {
            for (TeamRole role : TeamRole.values()) {
                String roleName = role.name().toLowerCase();
                ConfigurationSection roleSection = roles.getConfigurationSection(roleName);
                if (roleSection != null) {
                    RoleFormat format = new RoleFormat(
                            translateColorCodes(roleSection.getString("prefix", "")),
                            translateColorCodes(roleSection.getString("color", "&f")),
                            translateColorCodes(roleSection.getString("display", "&7(" + role.getDisplayName() + ")"))
                    );
                    roleFormats.put(role, format);
                }
            }
        }
    }

    private void loadMessagesFromSection(ConfigurationSection section, String prefix) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;

            if (section.isConfigurationSection(key)) {
                loadMessagesFromSection(section.getConfigurationSection(key), path);
            } else {
                messages.put(path, translateColorCodes(section.getString(key)));
            }
        }
    }

    public String translateColorCodes(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getMessage(String key) {
        return messages.getOrDefault(key, key);
    }

    public String getMessage(String key, String... replacements) {
        String message = getMessage(key);
        if (replacements.length % 2 == 0) {
            for (int i = 0; i < replacements.length; i += 2) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
        }
        return message;
    }

    public String formatPlural(int count, String singular, String plural) {
        return count == 1 ? singular : plural;
    }

    public RoleFormat getRoleFormat(TeamRole role) {
        return roleFormats.getOrDefault(role, new RoleFormat("", "§f", "§7(" + role.getDisplayName() + ")"));
    }

    public String getTeamChatFormat() {
        return messages.getOrDefault("team-chat-format", "§7[Team] §f%player_name%: %message%");
    }

    public boolean containsBannedWord(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        String lowerText = text.toLowerCase();

        // Check for SQL injection patterns
        String[] sqlPatterns = {
                "';", "--", "/*", "*/", "xp_", "sp_",
                "drop table", "drop database", "union select",
                "insert into", "delete from", "update set",
                "script>", "<script", "javascript:", "vbscript:"
        };

        for (String pattern : sqlPatterns) {
            if (lowerText.contains(pattern)) {
                return true;
            }
        }

        // Check banned words with word boundaries to avoid false positives
        for (String word : bannedWords) {
            // Use regex to match whole words only
            String pattern = "\\b" + word.toLowerCase() + "\\b";
            if (lowerText.matches(".*" + pattern + ".*")) {
                return true;
            }
        }

        return false;
    }

    public String formatDate(long timestamp) {
        return dateFormat.format(new Date(timestamp));
    }

    public int getMaxManagers() {
        return getConfig().getInt("max-managers", 1);
    }

    public boolean isDatabaseConnected() {
        return databaseConnected;
    }

    public static Teams getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public InviteManager getInviteManager() {
        return inviteManager;
    }

    public ConfirmationManager getConfirmationManager() {
        return confirmationManager;
    }

    public static class RoleFormat {
        private final String prefix;
        private final String color;
        private final String display;

        public RoleFormat(String prefix, String color, String display) {
            this.prefix = prefix;
            this.color = color;
            this.display = display;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getColor() {
            return color;
        }

        public String getDisplay() {
            return display;
        }
    }
}