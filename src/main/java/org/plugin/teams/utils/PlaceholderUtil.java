package org.plugin.teams.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.plugin.teams.Teams;

public class PlaceholderUtil {

    public static boolean hasPlayerStats() {
        return Bukkit.getPluginManager().getPlugin("PlayerStats") != null &&
                Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public static double getPlaytimeHours(Player player) {
        if (!hasPlayerStats()) {
            Teams.getInstance().getLogger().warning("PlayerStats not found - playtime check bypassed");
            return Double.MAX_VALUE; // Allow if PlayerStats not found
        }

        try {
            // Try multiple possible PlayerStats placeholders
            String[] placeholders = {
                    "%playerstats_playtime_hours%",
                    "%playerstats_time_played_hours%",
                    "%playerstats_hours_played%",
                    "%playerstats_playtime%"
            };

            for (String placeholder : placeholders) {
                String playtimeStr = PlaceholderAPI.setPlaceholders(player, placeholder);

                // Check if placeholder was actually replaced
                if (!playtimeStr.equals(placeholder) && !playtimeStr.isEmpty()) {
                    try {
                        // Handle different formats (some might include text)
                        String numericStr = playtimeStr.replaceAll("[^0-9.]", "");
                        if (!numericStr.isEmpty()) {
                            double hours = Double.parseDouble(numericStr);

                            // If the placeholder returns minutes, convert to hours
                            if (placeholder.contains("playtime") && !placeholder.contains("hours") && hours > 100) {
                                hours = hours / 60.0;
                            }

                            Teams.getInstance().getLogger().info("Found playtime for " + player.getName() +
                                    ": " + hours + " hours using placeholder " + placeholder);
                            return hours;
                        }
                    } catch (NumberFormatException e) {
                        // Try next placeholder
                        continue;
                    }
                }
            }

            // If no placeholder worked, log and return 0
            Teams.getInstance().getLogger().warning("Could not parse playtime for " + player.getName() +
                    " - PlayerStats might not be tracking this player yet");
            return 0;

        } catch (Exception e) {
            Teams.getInstance().getLogger().warning("Failed to get playtime for " + player.getName() + ": " + e.getMessage());
            return 0;
        }
    }

    public static boolean hasRequiredPlaytime(Player player) {
        if (player.hasPermission("teams.bypass.playtime")) {
            return true;
        }

        double requiredHours = Teams.getInstance().getConfig().getDouble("minimum-playtime-hours", 10);
        double playerHours = getPlaytimeHours(player);

        Teams.getInstance().getLogger().info("Playtime check for " + player.getName() +
                ": " + playerHours + " hours (required: " + requiredHours + ")");

        return playerHours >= requiredHours;
    }

    public static String formatPlaytime(double hours) {
        if (hours < 1) {
            int minutes = (int)(hours * 60);
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        } else if (hours >= 24) {
            double days = hours / 24;
            return String.format("%.1f day%s", days, days >= 2 ? "s" : "");
        } else {
            return String.format("%.1f hour%s", hours, hours >= 2 ? "s" : "");
        }
    }
}
