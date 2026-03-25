package org.plugin.teams.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.plugin.teams.Teams;
import org.plugin.teams.api.TeamsAPI;
import org.plugin.teams.models.Team;
import org.plugin.teams.models.TeamRole;

import java.util.*;

public class TeamManager {
    private final Teams plugin;
    private final Map<String, Team> teams;
    private final Map<UUID, String> playerTeams;
    private final Map<Integer, String> teamsByMid; // MID to team ID mapping

    public TeamManager(Teams plugin) {
        this.plugin = plugin;
        this.teams = new HashMap<>();
        this.playerTeams = new HashMap<>();
        this.teamsByMid = new HashMap<>();

        loadAllTeams();
    }

    private void loadAllTeams() {
        List<Team> loadedTeams = plugin.getDatabaseManager().loadAllTeams();
        for (Team team : loadedTeams) {
            teams.put(team.getId(), team);
            teamsByMid.put(team.getMid(), team.getId()); // Map MID to team ID
            for (UUID memberId : team.getMembers().keySet()) {
                playerTeams.put(memberId, team.getId());
            }
        }
    }

    public Team createTeam(String name, Player owner) {
        if (name.length() < 3 || name.length() > 16) {
            return null;
        }

        if (plugin.containsBannedWord(name)) {
            return null;
        }

        if (playerTeams.containsKey(owner.getUniqueId())) {
            return null;
        }

        String teamId = UUID.randomUUID().toString();
        Team team = new Team(teamId, name, owner.getUniqueId());

        // SET THE DEFAULT BANNER LINK HERE
        team.setBannerLink("https://i.imgur.com/wY1oUO0.png");

        teams.put(teamId, team);
        playerTeams.put(owner.getUniqueId(), teamId);

        plugin.getDatabaseManager().saveTeam(team);

        // After saving, update the MID mapping
        teamsByMid.put(team.getMid(), teamId);

        // Notify chat plugin about team change
        TeamsAPI.notifyTeamChange(owner.getUniqueId(), null, name);

        return team;
    }

    public boolean deleteTeam(String teamId) {
        Team team = teams.get(teamId);
        if (team == null) return false;

        String teamName = team.getName();

        // Store all member IDs before deletion
        List<UUID> memberIds = new ArrayList<>(team.getMembers().keySet());

        // Clear the cache for ALL team members
        for (UUID memberId : memberIds) {
            playerTeams.remove(memberId);
        }

        teams.remove(teamId);
        teamsByMid.remove(team.getMid()); // Remove MID mapping
        plugin.getDatabaseManager().deleteTeam(teamId);

        // Notify chat plugin about team changes for all former members
        for (UUID memberId : memberIds) {
            TeamsAPI.notifyTeamChange(memberId, teamName, null);
        }

        return true;
    }

    public void addMember(Team team, UUID playerId, TeamRole role) {
        team.addMember(playerId, role);
        playerTeams.put(playerId, team.getId());
        plugin.getDatabaseManager().saveTeam(team);

        // Notify chat plugin about team change
        TeamsAPI.notifyTeamChange(playerId, null, team.getName());
    }

    public void removeMember(Team team, UUID playerId) {
        String teamName = team.getName();

        team.removeMember(playerId);
        playerTeams.remove(playerId);
        plugin.getDatabaseManager().saveTeam(team);

        // Notify chat plugin about team change
        TeamsAPI.notifyTeamChange(playerId, teamName, null);
    }

    public void updateTeam(Team team) {
        plugin.getDatabaseManager().saveTeam(team);

        // No need to notify on general updates unless the name changed
    }

    public Team getTeam(String teamId) {
        return teams.get(teamId);
    }

    public Team getPlayerTeam(UUID playerId) {
        String teamId = playerTeams.get(playerId);
        return teamId != null ? teams.get(teamId) : null;
    }

    public Team getTeamByMid(int mid) {
        String teamId = teamsByMid.get(mid);
        return teamId != null ? teams.get(teamId) : null;
    }

    public List<Team> getAllTeams() {
        return new ArrayList<>(teams.values());
    }

    public boolean isTeamNameTaken(String name) {
        for (Team team : teams.values()) {
            if (team.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public void sendTeamMessage(Team team, String message) {
        for (UUID memberId : team.getMembers().keySet()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    // Method to force refresh a player's team data
    public void refreshPlayerTeamData(UUID playerId) {
        // Get old team name for notification
        Team oldTeam = getPlayerTeam(playerId);
        String oldTeamName = oldTeam != null ? oldTeam.getName() : null;

        // This method can be called to force a refresh
        String teamId = plugin.getDatabaseManager().getPlayerTeamId(playerId);
        if (teamId == null) {
            playerTeams.remove(playerId);
        } else {
            playerTeams.put(playerId, teamId);
        }

        // Get new team name
        Team newTeam = getPlayerTeam(playerId);
        String newTeamName = newTeam != null ? newTeam.getName() : null;

        // Notify if changed
        if (!Objects.equals(oldTeamName, newTeamName)) {
            TeamsAPI.notifyTeamChange(playerId, oldTeamName, newTeamName);
        }
    }
}