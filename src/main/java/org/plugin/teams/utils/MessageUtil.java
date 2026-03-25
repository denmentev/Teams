package org.plugin.teams.utils;

import org.bukkit.entity.Player;
import org.plugin.teams.Teams;

public class MessageUtil {

    // Color scheme
    private static final String PRIMARY = "§a";      // Light green
    private static final String SECONDARY = "§f";    // White
    private static final String ERROR = "§c";        // Red
    private static final String WARNING = "§e";      // Yellow
    private static final String INFO = "§b";         // Aqua
    private static final String GRAY = "§7";         // Gray
    private static final String DARK_GRAY = "§8";    // Dark gray

    // Prefixes with ASCII
    public static String success(String message) {
        return Teams.SUCCESS_PREFIX + SECONDARY + message;
    }

    public static String error(String message) {
        return Teams.ERROR_PREFIX + ERROR + message;
    }

    public static String info(String message) {
        return Teams.INFO_PREFIX + SECONDARY + message;
    }

    public static String normal(String message) {
        return Teams.PREFIX + message;
    }

    // Headers and formatting
    public static String header(String title) {
        return "\n" + PRIMARY + "▬▬▬▬▬▬▬▬▬ " + SECONDARY + title + PRIMARY + " ▬▬▬▬▬▬▬▬▬";
    }

    public static String footer() {
        return PRIMARY + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n";
    }

    public static String listItem(String item, String description) {
        return PRIMARY + " » " + SECONDARY + item + GRAY + " - " + description;
    }

    public static String highlight(String text) {
        return PRIMARY + text + SECONDARY;
    }

    // Team role colors
    public static String formatRole(String name, String role) {
        return switch (role.toUpperCase()) {
            case "OWNER" -> ERROR + "♠ " + name + GRAY + " (Owner)";
            case "MANAGER" -> WARNING + "♣ " + name + GRAY + " (Manager)";
            default -> SECONDARY + name + GRAY + " (Member)";
        };
    }

    // Common messages
    public static void sendNoPermission(Player player) {
        player.sendMessage(error("&c⚠ &8> &cYou don't have permission to do that!"));
    }

    public static void sendNotInTeam(Player player) {
        player.sendMessage(error("&c⚠ &8> &cYou are not in any team!"));
    }

    public static void sendAlreadyInTeam(Player player) {
        player.sendMessage(error("&c⚠ &8> &cYou are already in a team!"));
    }

    public static void sendTeamCreated(Player player, String teamName) {
        player.sendMessage(success("Team " + highlight(teamName) + " created successfully!"));
    }

    public static void sendUsage(Player player, String usage) {
        player.sendMessage(normal(GRAY + "Usage: " + SECONDARY + usage));
    }
}