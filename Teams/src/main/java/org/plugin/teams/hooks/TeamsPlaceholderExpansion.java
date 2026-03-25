package org.plugin.teams.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.plugin.teams.Teams;
import org.plugin.teams.models.Team;
import org.plugin.teams.models.TeamRole;

public class TeamsPlaceholderExpansion extends PlaceholderExpansion {

    private final Teams plugin;
    // Removed cache to ensure immediate updates

    public TeamsPlaceholderExpansion(Teams plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "teams";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        if (!plugin.isDatabaseConnected()) {
            return "";
        }

        // Always get fresh data - no caching
        Team team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());

        return processPlaceholder(team, player, params);
    }

    private String processPlaceholder(Team team, OfflinePlayer player, String params) {
        switch (params.toLowerCase()) {
            case "team_name":
                return team != null ? team.getName() : "";

            case "role":
                if (team == null) return "";
                TeamRole role = team.getMemberRole(player.getUniqueId());
                return role != null ? role.getDisplayName() : "";

            case "role_formatted":
                if (team == null) return "";
                TeamRole roleFormatted = team.getMemberRole(player.getUniqueId());
                if (roleFormatted == null) return "";
                Teams.RoleFormat format = plugin.getRoleFormat(roleFormatted);
                return format.getColor() + format.getPrefix() + roleFormatted.getDisplayName();

            case "role_prefix":
                if (team == null) return "";
                TeamRole rolePrefix = team.getMemberRole(player.getUniqueId());
                if (rolePrefix == null) return "";
                Teams.RoleFormat prefixFormat = plugin.getRoleFormat(rolePrefix);
                return prefixFormat.getPrefix();

            case "members_count":
                return team != null ? String.valueOf(team.getMembers().size()) : "0";

            case "has_team":
                return team != null ? "true" : "false";

            case "team_description":
                return team != null ? team.getDescription() : "";

            case "is_owner":
                if (team == null) return "false";
                return team.getMemberRole(player.getUniqueId()) == TeamRole.OWNER ? "true" : "false";

            case "is_manager":
                if (team == null) return "false";
                return team.getMemberRole(player.getUniqueId()) == TeamRole.MANAGER ? "true" : "false";

            case "is_member":
                if (team == null) return "false";
                return team.getMemberRole(player.getUniqueId()) == TeamRole.MEMBER ? "true" : "false";

            case "team_created":
                if (team == null) return "";
                return plugin.formatDate(team.getCreatedAt());

            default:
                return null;
        }
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        return onRequest(player, params);
    }
}