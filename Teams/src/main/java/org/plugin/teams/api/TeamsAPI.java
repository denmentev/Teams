package org.plugin.teams.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.plugin.teams.Teams;
import org.plugin.teams.models.Team;
import org.plugin.teams.models.TeamRole;

import java.util.UUID;

public class TeamsAPI {
    private static Teams plugin;

    // Add a listener interface for team changes
    public interface TeamChangeListener {
        void onPlayerTeamChange(UUID playerId, String oldTeamName, String newTeamName);
    }

    private static TeamChangeListener changeListener;

    // This method should be called by Teams plugin on enable
    public static void setPlugin(Teams instance) {
        plugin = instance;
    }

    /**
     * Register a listener for team changes
     * Your chat plugin should register this to clear its cache
     */
    public static void setTeamChangeListener(TeamChangeListener listener) {
        changeListener = listener;
    }

    /**
     * Called internally when a player's team changes
     */
    public static void notifyTeamChange(UUID playerId, String oldTeamName, String newTeamName) {
        if (changeListener != null) {
            changeListener.onPlayerTeamChange(playerId, oldTeamName, newTeamName);
        }

        // Also try to notify your chat plugin directly if it's loaded
        Plugin chatPlugin = Bukkit.getPluginManager().getPlugin("YourChatPlugin");
        if (chatPlugin != null) {
            // If your chat plugin has a method to clear cache, call it here
            try {
                chatPlugin.getClass().getMethod("clearPlayerCache", UUID.class).invoke(chatPlugin, playerId);
            } catch (Exception ignored) {
                // Method might not exist
            }
        }
    }

    /**
     * Get the team name of a player
     * @param player The player
     * @return Team name or null if not in a team
     */
    public static String getTeamName(Player player) {
        if (plugin == null || !plugin.isDatabaseConnected()) return null;

        Team team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        return team != null ? team.getName() : null;
    }

    /**
     * Get the team name of a player by UUID
     * @param playerUUID The player's UUID
     * @return Team name or null if not in a team
     */
    public static String getTeamName(UUID playerUUID) {
        if (plugin == null || !plugin.isDatabaseConnected()) return null;

        Team team = plugin.getTeamManager().getPlayerTeam(playerUUID);
        return team != null ? team.getName() : null;
    }

    /**
     * Get the team of a player
     * @param player The player
     * @return Team object or null if not in a team
     */
    public static Team getTeam(Player player) {
        if (plugin == null || !plugin.isDatabaseConnected()) return null;

        return plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
    }

    /**
     * Get the team of a player by UUID
     * @param playerUUID The player's UUID
     * @return Team object or null if not in a team
     */
    public static Team getTeam(UUID playerUUID) {
        if (plugin == null || !plugin.isDatabaseConnected()) return null;

        return plugin.getTeamManager().getPlayerTeam(playerUUID);
    }

    /**
     * Get player's role in their team
     * @param player The player
     * @return TeamRole or null if not in a team
     */
    public static TeamRole getPlayerRole(Player player) {
        if (plugin == null || !plugin.isDatabaseConnected()) return null;

        Team team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        return team != null ? team.getMemberRole(player.getUniqueId()) : null;
    }

    /**
     * Check if player is in a team
     * @param player The player
     * @return true if in a team, false otherwise
     */
    public static boolean hasTeam(Player player) {
        if (plugin == null || !plugin.isDatabaseConnected()) return false;

        return plugin.getTeamManager().getPlayerTeam(player.getUniqueId()) != null;
    }

    /**
     * Get formatted team name with colors (if you want to support color codes)
     * @param player The player
     * @return Formatted team name or null
     */
    public static String getFormattedTeamName(Player player) {
        if (plugin == null || !plugin.isDatabaseConnected()) return null;

        Team team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) return null;

        TeamRole role = team.getMemberRole(player.getUniqueId());
        Teams.RoleFormat format = plugin.getRoleFormat(role);

        return format.getColor() + team.getName();
    }

    /**
     * Get team by name
     * @param teamName The team name
     * @return Team object or null if not found
     */
    public static Team getTeamByName(String teamName) {
        if (plugin == null || !plugin.isDatabaseConnected()) return null;

        return plugin.getTeamManager().getAllTeams().stream()
                .filter(team -> team.getName().equalsIgnoreCase(teamName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get team by MID (3-digit ID)
     * @param mid The team MID
     * @return Team object or null if not found
     */
    public static Team getTeamByMid(int mid) {
        if (plugin == null || !plugin.isDatabaseConnected()) return null;

        return plugin.getTeamManager().getTeamByMid(mid);
    }

    // ========== METHODS FOR HOVER INFORMATION ==========

    /**
     * Get the owner name of a team
     * @param teamName The team name
     * @return Owner name or null if team not found
     */
    public static String getTeamOwner(String teamName) {
        if (plugin == null || !plugin.isDatabaseConnected()) return null;

        Team team = getTeamByName(teamName);
        if (team == null) return null;

        return plugin.getDatabaseManager().getPlayerName(team.getOwnerId());
    }

    /**
     * Get the creation date of a team (formatted)
     * @param teamName The team name
     * @return Formatted creation date or null if team not found
     */
    public static String getTeamCreationDate(String teamName) {
        if (plugin == null || !plugin.isDatabaseConnected()) return null;

        Team team = getTeamByName(teamName);
        if (team == null) return null;

        return plugin.formatDate(team.getCreatedAt());
    }

    /**
     * Get the member count of a team
     * @param teamName The team name
     * @return Member count or 0 if team not found
     */
    public static int getTeamMemberCount(String teamName) {
        if (plugin == null || !plugin.isDatabaseConnected()) return 0;

        Team team = getTeamByName(teamName);
        if (team == null) return 0;

        return team.getMembers().size();
    }

    /**
     * Get the owner name of a player's team
     * @param player The player
     * @return Owner name or null if not in a team
     */
    public static String getPlayerTeamOwner(Player player) {
        if (plugin == null || !plugin.isDatabaseConnected()) return null;

        Team team = getTeam(player);
        if (team == null) return null;

        return plugin.getDatabaseManager().getPlayerName(team.getOwnerId());
    }

    /**
     * Get the creation date of a player's team (formatted)
     * @param player The player
     * @return Formatted creation date or null if not in a team
     */
    public static String getPlayerTeamCreationDate(Player player) {
        if (plugin == null || !plugin.isDatabaseConnected()) return null;

        Team team = getTeam(player);
        if (team == null) return null;

        return plugin.formatDate(team.getCreatedAt());
    }

    /**
     * Get the member count of a player's team
     * @param player The player
     * @return Member count or 0 if not in a team
     */
    public static int getPlayerTeamMemberCount(Player player) {
        if (plugin == null || !plugin.isDatabaseConnected()) return 0;

        Team team = getTeam(player);
        if (team == null) return 0;

        return team.getMembers().size();
    }

    // ========== NEW BANNER METHODS ==========

    /**
     * Get the banner link of a team
     * @param teamName The team name
     * @return Banner link or null if team not found or no banner
     */
    public static String getTeamBanner(String teamName) {
        if (plugin == null || !plugin.isDatabaseConnected()) return null;

        Team team = getTeamByName(teamName);
        if (team == null) return null;

        return team.getBannerLink();
    }

    /**
     * Get the banner link of a player's team
     * @param player The player
     * @return Banner link or null if not in a team or no banner
     */
    public static String getPlayerTeamBanner(Player player) {
        if (plugin == null || !plugin.isDatabaseConnected()) return null;

        Team team = getTeam(player);
        if (team == null) return null;

        return team.getBannerLink();
    }

    /**
     * Check if a team has a banner
     * @param teamName The team name
     * @return true if team has a banner, false otherwise
     */
    public static boolean teamHasBanner(String teamName) {
        if (plugin == null || !plugin.isDatabaseConnected()) return false;

        Team team = getTeamByName(teamName);
        if (team == null) return false;

        return team.hasBanner();
    }

    /**
     * Check if a player's team has a banner
     * @param player The player
     * @return true if team has a banner, false otherwise
     */
    public static boolean playerTeamHasBanner(Player player) {
        if (plugin == null || !plugin.isDatabaseConnected()) return false;

        Team team = getTeam(player);
        if (team == null) return false;

        return team.hasBanner();
    }

    /**
     * Get the banner link by team ID
     * @param teamId The team ID
     * @return Banner link or null if team not found or no banner
     */
    public static String getTeamBannerById(String teamId) {
        if (plugin == null || !plugin.isDatabaseConnected()) return null;

        Team team = plugin.getTeamManager().getTeam(teamId);
        if (team == null) return null;

        return team.getBannerLink();
    }

    /**
     * Get the banner link by team MID
     * @param mid The team MID
     * @return Banner link or null if team not found or no banner
     */
    public static String getTeamBannerByMid(int mid) {
        if (plugin == null || !plugin.isDatabaseConnected()) return null;

        Team team = getTeamByMid(mid);
        if (team == null) return null;

        return team.getBannerLink();
    }
}