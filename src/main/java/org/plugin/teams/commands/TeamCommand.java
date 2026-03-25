package org.plugin.teams.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.plugin.teams.Teams;
import org.plugin.teams.managers.ConfirmationManager;
import org.plugin.teams.managers.InviteManager;
import org.plugin.teams.managers.TeamManager;
import org.plugin.teams.models.Team;
import org.plugin.teams.models.TeamInvite;
import org.plugin.teams.models.TeamRole;
import org.plugin.teams.utils.PlaceholderUtil;

import java.util.*;
import java.util.stream.Collectors;

public class TeamCommand implements CommandExecutor, TabCompleter {
    private final Teams plugin;

    public TeamCommand(Teams plugin) {
        this.plugin = plugin;
    }

    // Lazy getters to avoid null pointer exceptions
    private TeamManager getTeamManager() {
        return plugin.getTeamManager();
    }

    private InviteManager getInviteManager() {
        return plugin.getInviteManager();
    }

    private ConfirmationManager getConfirmationManager() {
        return plugin.getConfirmationManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle reload command - can be used from console
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            handleReload(sender);
            return true;
        }

        // Handle help command for team chat
        if (args.length > 0 && args[0].equalsIgnoreCase("chathelp")) {
            handleChatHelp(sender);
            return true;
        }

        // All other commands require a player
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.console-only"));
            return true;
        }

        // Check if database is connected
        if (!plugin.isDatabaseConnected()) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.database-error"));
            return true;
        }

        // Check if managers are initialized
        if (getTeamManager() == null) {
            player.sendMessage(Teams.ERROR_PREFIX + "Teams system is still initializing, please wait...");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> handleCreate(player, args);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player);
            case "deny" -> handleDeny(player);
            case "kick" -> handleKick(player, args);
            case "promote" -> handlePromote(player, args);
            case "demote" -> handleDemote(player, args);
            case "delete" -> handleDelete(player, args);
            case "list" -> handleList(player);
            case "info" -> handleInfo(player, args);
            case "leave" -> handleLeave(player);
            case "confirm" -> handleConfirm(player, args);
            case "cancel" -> handleCancel(player, args);
            case "help" -> sendHelp(player);
            default -> player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.usage", "%usage%", "/team help"));
        }

        return true;
    }

    private void handleChatHelp(CommandSender sender) {
        sender.sendMessage("§7§m---------- Team Chat Help §7§m----------");
        sender.sendMessage("§e#<message> §7- Send a message to your team");
        sender.sendMessage("");
        sender.sendMessage("§6Available Placeholders in Team Chat:");
        sender.sendMessage("§e:team: §7- Show your team name");
        sender.sendMessage("§e:item: §7- Show item in hand");
        sender.sendMessage("§e:loc: §7- Show your location");
        sender.sendMessage("§e:x1234: §7- Reference marks (if Marks plugin installed)");
        sender.sendMessage("§e@player §7- Mention a team member");
        sender.sendMessage("");
        sender.sendMessage("§7Example: §f#Hey team, I'm at :loc: with :item:!");
        sender.sendMessage("§7Example: §f#Meet me at mark :x0001:");
        sender.sendMessage("§7Example: §f#@Steve can you help me?");
    }

    private void handleReload(CommandSender sender) {
        // Check for permission
        if (!sender.hasPermission("teams.admin")) {
            sender.sendMessage(Teams.ERROR_PREFIX + "You don't have permission to reload the plugin");
            return;
        }

        try {
            // Reload the configuration
            plugin.reloadConfiguration();

            // Reload chat listener to reconnect to ChatPlugin
            plugin.reloadChatListener();

            sender.sendMessage(Teams.SUCCESS_PREFIX + "Teams configuration reloaded successfully!");

            // Log to console if command was run by a player
            if (sender instanceof Player) {
                plugin.getLogger().info("Configuration reloaded by " + sender.getName());
            }
        } catch (Exception e) {
            sender.sendMessage(Teams.ERROR_PREFIX + "Failed to reload configuration: " + e.getMessage());
            plugin.getLogger().severe("Error reloading configuration: " + e.getMessage());
        }
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.usage", "%usage%", "/team create <name>"));
            return;
        }

        // Check playtime
        if (!PlaceholderUtil.hasRequiredPlaytime(player)) {
            double hours = PlaceholderUtil.getPlaytimeHours(player);
            double required = plugin.getConfig().getDouble("minimum-playtime-hours", 10);
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.playtime-required",
                    "%required%", PlaceholderUtil.formatPlaytime(required)));
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.current-playtime",
                    "%playtime%", PlaceholderUtil.formatPlaytime(hours)));
            return;
        }

        // Check if already in a team
        if (getTeamManager().getPlayerTeam(player.getUniqueId()) != null) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.already-in-team"));
            return;
        }

        String teamName = args[1];

        // Enhanced validation for special characters
        if (!isValidTeamName(teamName)) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.name-invalid-characters"));
            return;
        }

        if (teamName.length() < 3 || teamName.length() > 16) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.name-length",
                    "%min%", "3", "%max%", "16"));
            return;
        }

        if (plugin.containsBannedWord(teamName)) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.name-inappropriate"));
            return;
        }

        if (getTeamManager().isTeamNameTaken(teamName)) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.name-taken"));
            return;
        }

        Team team = getTeamManager().createTeam(teamName, player);
        if (team != null) {
            player.sendMessage(Teams.SUCCESS_PREFIX + plugin.getMessage("commands.team-created", "%team_name%", teamName));
        } else {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.database-error"));
        }
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.usage", "%usage%", "/team invite <player>"));
            return;
        }

        Team team = getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.not-in-team"));
            return;
        }

        TeamRole role = team.getMemberRole(player.getUniqueId());
        if (!role.canInvite()) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.no-permission"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.player-not-found"));
            return;
        }

        if (target.equals(player)) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.cannot-invite-self"));
            return;
        }

        if (getTeamManager().getPlayerTeam(target.getUniqueId()) != null) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.player-already-in-team"));
            return;
        }

        if (!getInviteManager().canInvite(team.getId(), player.getUniqueId(), target.getUniqueId())) {
            long cooldown = getInviteManager().getRemainingCooldown(team.getId(), target.getUniqueId());
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.invite-cooldown",
                    "%seconds%", String.valueOf(cooldown)));
            return;
        }

        TeamInvite invite = getInviteManager().createInvite(team.getId(), player.getUniqueId(), target.getUniqueId());
        if (invite != null) {
            player.sendMessage(Teams.SUCCESS_PREFIX + plugin.getMessage("commands.invite-sent", "%player_name%", target.getName()));
            getInviteManager().sendInviteMessage(player, target, team);
        }
    }

    private void handleAccept(Player player) {
        TeamInvite invite = getInviteManager().getLatestInvite(player.getUniqueId());
        if (invite == null) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.no-invites"));
            return;
        }

        Team team = getTeamManager().getTeam(invite.getTeamId());
        if (team == null) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.team-no-longer-exists"));
            getInviteManager().removeInvite(invite);
            return;
        }

        if (getTeamManager().getPlayerTeam(player.getUniqueId()) != null) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.already-in-team"));
            return;
        }

        getTeamManager().addMember(team, player.getUniqueId(), TeamRole.MEMBER);
        getInviteManager().removeAllInvites(player.getUniqueId());

        player.sendMessage(Teams.SUCCESS_PREFIX + plugin.getMessage("commands.invite-accepted", "%team_name%", team.getName()));
        getTeamManager().sendTeamMessage(team, Teams.SUCCESS_PREFIX + plugin.getMessage("commands.player-joined", "%player_name%", player.getName()));
    }

    private void handleDeny(Player player) {
        TeamInvite invite = getInviteManager().getLatestInvite(player.getUniqueId());
        if (invite == null) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.no-invites"));
            return;
        }

        getInviteManager().removeInvite(invite);
        player.sendMessage(Teams.SUCCESS_PREFIX + plugin.getMessage("commands.invite-declined"));

        Player inviter = Bukkit.getPlayer(invite.getInviterId());
        if (inviter != null) {
            inviter.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.invite-declined-notify",
                    "%player_name%", player.getName()));
        }
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.usage", "%usage%", "/team kick <player>"));
            return;
        }

        Team team = getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.not-in-team"));
            return;
        }

        TeamRole role = team.getMemberRole(player.getUniqueId());
        if (!role.canKick()) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.no-permission"));
            return;
        }

        // Check if trying to kick self BEFORE getting target player
        if (args[1].equalsIgnoreCase(player.getName())) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.cannot-kick-self"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.player-not-found"));
            return;
        }

        if (!team.isMember(target.getUniqueId())) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.player-not-in-team"));
            return;
        }

        // Double-check with UUID comparison
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.cannot-kick-self"));
            return;
        }

        TeamRole targetRole = team.getMemberRole(target.getUniqueId());
        if (targetRole == TeamRole.OWNER) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.cannot-kick-owner"));
            return;
        }

        if (role == TeamRole.MANAGER && targetRole == TeamRole.MANAGER) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.cannot-kick-manager"));
            return;
        }

        // Request confirmation
        getConfirmationManager().requestConfirmation(player, ConfirmationManager.ConfirmationType.KICK, target.getName(), () -> {
            getTeamManager().removeMember(team, target.getUniqueId());
            player.sendMessage(Teams.SUCCESS_PREFIX + plugin.getMessage("commands.player-kicked", "%player_name%", target.getName()));
            target.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.player-kicked", "%player_name%", "You"));
            getTeamManager().sendTeamMessage(team, Teams.ERROR_PREFIX + plugin.getMessage("commands.player-kicked", "%player_name%", target.getName()));
        });
    }

    private void handlePromote(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.usage", "%usage%", "/team promote <player>"));
            return;
        }

        Team team = getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.not-in-team"));
            return;
        }

        TeamRole role = team.getMemberRole(player.getUniqueId());
        if (role != TeamRole.OWNER && !player.hasPermission("teams.admin")) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.only-owner-promote"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.player-not-found"));
            return;
        }

        if (!team.isMember(target.getUniqueId())) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.player-not-in-team"));
            return;
        }

        // Check if trying to promote self
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.cannot-promote-owner"));
            return;
        }

        TeamRole currentRole = team.getMemberRole(target.getUniqueId());
        if (currentRole == TeamRole.OWNER) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.cannot-promote-owner"));
            return;
        }

        if (currentRole == TeamRole.MANAGER) {
            // Promoting Manager to Owner - swap roles
            if (role == TeamRole.OWNER) {
                getConfirmationManager().requestConfirmation(player, ConfirmationManager.ConfirmationType.PROMOTE_TO_OWNER,
                        target.getName(), () -> {
                            // Swap roles
                            team.setMemberRole(player.getUniqueId(), TeamRole.MANAGER);
                            team.setMemberRole(target.getUniqueId(), TeamRole.OWNER);
                            team.setOwnerId(target.getUniqueId());
                            getTeamManager().updateTeam(team);

                            player.sendMessage(Teams.SUCCESS_PREFIX + plugin.getMessage("commands.member-promoted",
                                    "%player_name%", target.getName(), "%role%", "Owner"));
                            target.sendMessage(Teams.INFO_PREFIX + plugin.getMessage("notifications.ownership-transferred"));
                            getTeamManager().sendTeamMessage(team, Teams.INFO_PREFIX + target.getName() + " is now the team Owner!");
                        });
            } else {
                player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.already-manager"));
            }
            return;
        }

        // Check max managers
        List<UUID> managers = team.getMembersByRole(TeamRole.MANAGER);
        if (managers.size() >= plugin.getMaxManagers()) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.max-managers-reached",
                    "%max%", String.valueOf(plugin.getMaxManagers())));
            return;
        }

        // Request confirmation for Member to Manager
        getConfirmationManager().requestConfirmation(player, ConfirmationManager.ConfirmationType.PROMOTE, target.getName(), () -> {
            team.setMemberRole(target.getUniqueId(), TeamRole.MANAGER);
            getTeamManager().updateTeam(team);

            player.sendMessage(Teams.SUCCESS_PREFIX + plugin.getMessage("commands.member-promoted",
                    "%player_name%", target.getName(), "%role%", "Manager"));
            target.sendMessage(Teams.SUCCESS_PREFIX + plugin.getMessage("commands.role-changed-notify",
                    "%action%", "promoted", "%role%", "Manager"));
            getTeamManager().sendTeamMessage(team, Teams.SUCCESS_PREFIX + plugin.getMessage("commands.member-promoted",
                    "%player_name%", target.getName(), "%role%", "Manager"));
        });
    }

    private void handleDemote(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.usage", "%usage%", "/team demote <player>"));
            return;
        }

        Team team = getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.not-in-team"));
            return;
        }

        TeamRole role = team.getMemberRole(player.getUniqueId());
        if (role != TeamRole.OWNER && !player.hasPermission("teams.admin")) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.only-owner-demote"));
            return;
        }

        // Check if trying to demote self BEFORE getting target player
        if (args[1].equalsIgnoreCase(player.getName()) && !player.hasPermission("teams.admin")) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.cannot-demote-self"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.player-not-found"));
            return;
        }

        if (!team.isMember(target.getUniqueId())) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.player-not-in-team"));
            return;
        }

        // Double-check with UUID comparison as well
        if (target.getUniqueId().equals(player.getUniqueId()) && !player.hasPermission("teams.admin")) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.cannot-demote-self"));
            return;
        }

        TeamRole currentRole = team.getMemberRole(target.getUniqueId());
        if (currentRole == TeamRole.MEMBER) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.cannot-demote-member"));
            return;
        }

        if (currentRole == TeamRole.OWNER && !player.hasPermission("teams.admin")) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.cannot-kick-owner"));
            return;
        }

        // Request confirmation
        getConfirmationManager().requestConfirmation(player, ConfirmationManager.ConfirmationType.DEMOTE, target.getName(), () -> {
            team.setMemberRole(target.getUniqueId(), TeamRole.MEMBER);
            getTeamManager().updateTeam(team);

            player.sendMessage(Teams.SUCCESS_PREFIX + plugin.getMessage("commands.member-demoted",
                    "%player_name%", target.getName(), "%role%", "Member"));
            target.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.role-changed-notify",
                    "%action%", "demoted", "%role%", "Member"));
            getTeamManager().sendTeamMessage(team, Teams.ERROR_PREFIX + plugin.getMessage("commands.member-demoted",
                    "%player_name%", target.getName(), "%role%", "Member"));
        });
    }

    private void handleDelete(Player player, String[] args) {
        Team team;

        if (args.length > 1 && player.hasPermission("teams.admin")) {
            // Admin deleting specific team
            String teamName = args[1];
            team = getTeamManager().getAllTeams().stream()
                    .filter(t -> t.getName().equalsIgnoreCase(teamName))
                    .findFirst()
                    .orElse(null);

            if (team == null) {
                player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.team-not-found"));
                return;
            }
        } else {
            team = getTeamManager().getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.not-in-team"));
                return;
            }

            // FIX: Check if player is actually the current owner
            if (!team.getOwnerId().equals(player.getUniqueId()) && !player.hasPermission("teams.admin")) {
                player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.only-owner-delete"));
                return;
            }
        }

        // Request confirmation
        final Team teamToDelete = team;
        getConfirmationManager().requestConfirmation(player, ConfirmationManager.ConfirmationType.DELETE, team.getName(), () -> {
            // Get all team members before deletion
            Set<UUID> teamMembers = new HashSet<>(teamToDelete.getMembers().keySet());

            getTeamManager().sendTeamMessage(teamToDelete, Teams.ERROR_PREFIX + "Team '" + teamToDelete.getName() + "' has been deleted!");
            getTeamManager().deleteTeam(teamToDelete.getId());
            player.sendMessage(Teams.SUCCESS_PREFIX + plugin.getMessage("commands.team-deleted"));

            // Force PlaceholderAPI to refresh for all former team members
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                for (UUID memberId : teamMembers) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null && member.isOnline()) {
                        // Force a placeholder refresh by updating the player
                        member.setPlayerListName(member.getPlayerListName());
                    }
                }
            }
        });
    }

    private void handleList(Player player) {
        List<Team> teams = getTeamManager().getAllTeams();
        if (teams.isEmpty()) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.no-teams-exist"));
            return;
        }

        player.sendMessage(plugin.getMessage("help.header").replace("Teams Commands", "All Teams"));
        for (Team team : teams) {
            String memberText = team.getMembers().size() + " member" +
                    plugin.formatPlural(team.getMembers().size(), "", "s");
            player.sendMessage("§e" + team.getName() + " §7- " + memberText);
            if (!team.getDescription().isEmpty()) {
                player.sendMessage("  §7" + team.getDescription());
            }
        }
    }

    private void handleInfo(Player player, String[] args) {
        Team team;

        if (args.length > 1) {
            // Find team by name
            String teamName = args[1];
            team = getTeamManager().getAllTeams().stream()
                    .filter(t -> t.getName().equalsIgnoreCase(teamName))
                    .findFirst()
                    .orElse(null);

            if (team == null) {
                player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.team-not-found"));
                return;
            }
        } else {
            team = getTeamManager().getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.not-in-team") + " Use /team info <name>");
                return;
            }
        }

        // Use messages from config
        player.sendMessage(plugin.getMessage("team-info.header"));
        player.sendMessage(plugin.getMessage("team-info.name", "%team_name%", team.getName()));
        player.sendMessage(plugin.getMessage("team-info.description",
                "%description%", team.getDescription().isEmpty() ?
                        plugin.getMessage("team-info.description-not-set") : team.getDescription()));
        player.sendMessage(plugin.getMessage("team-info.created", "%date%", plugin.formatDate(team.getCreatedAt())));

        // Members count with proper plural handling
        String memberCount = plugin.getMessage("team-info.members-count",
                "%count%", String.valueOf(team.getMembers().size()),
                "%s%", plugin.formatPlural(team.getMembers().size(), "", "s"));
        player.sendMessage(memberCount);

        player.sendMessage(plugin.getMessage("team-info.roster"));

        // Owner
        String ownerName = plugin.getDatabaseManager().getPlayerName(team.getOwnerId());
        OfflinePlayer ownerPlayer = Bukkit.getOfflinePlayer(team.getOwnerId());
        Teams.RoleFormat ownerFormat = plugin.getRoleFormat(TeamRole.OWNER);

        String ownerLine = plugin.getMessage(ownerPlayer.isOnline() ? "team-info.member-online" : "team-info.member-offline",
                "%role_prefix%", ownerFormat.getPrefix(),
                "%player_name%", ownerName,
                "%role_display%", ownerFormat.getDisplay());
        player.sendMessage(ownerLine);

        // Managers
        List<UUID> managers = team.getMembersByRole(TeamRole.MANAGER);
        if (!managers.isEmpty()) {
            Teams.RoleFormat managerFormat = plugin.getRoleFormat(TeamRole.MANAGER);
            for (UUID managerId : managers) {
                String managerName = plugin.getDatabaseManager().getPlayerName(managerId);
                OfflinePlayer managerPlayer = Bukkit.getOfflinePlayer(managerId);

                String managerLine = plugin.getMessage(managerPlayer.isOnline() ? "team-info.member-online" : "team-info.member-offline",
                        "%role_prefix%", managerFormat.getPrefix(),
                        "%player_name%", managerName,
                        "%role_display%", managerFormat.getDisplay());
                player.sendMessage(managerLine);
            }
        }

        // Members
        List<UUID> members = team.getMembersByRole(TeamRole.MEMBER);
        if (!members.isEmpty()) {
            Teams.RoleFormat memberFormat = plugin.getRoleFormat(TeamRole.MEMBER);
            for (UUID memberId : members) {
                String memberName = plugin.getDatabaseManager().getPlayerName(memberId);
                OfflinePlayer memberPlayer = Bukkit.getOfflinePlayer(memberId);

                String memberLine = plugin.getMessage(memberPlayer.isOnline() ? "team-info.member-online" : "team-info.member-offline",
                        "%role_prefix%", memberFormat.getPrefix(),
                        "%player_name%", memberName,
                        "%role_display%", memberFormat.getDisplay());
                player.sendMessage(memberLine);
            }
        }
    }

    private void handleLeave(Player player) {
        Team team = getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.not-in-team"));
            return;
        }

        TeamRole role = team.getMemberRole(player.getUniqueId());

        if (role == TeamRole.OWNER) {
            if (team.getMembers().size() == 1) {
                // Owner is alone - confirm deletion
                getConfirmationManager().requestConfirmation(player,
                        ConfirmationManager.ConfirmationType.LEAVE_OWNER_ALONE,
                        team.getName(), () -> {
                            getTeamManager().deleteTeam(team.getId());
                            player.sendMessage(Teams.SUCCESS_PREFIX + plugin.getMessage("commands.team-deleted"));
                        });
            } else {
                player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.owner-cannot-leave"));
            }
            return;
        }

        if (role == TeamRole.MANAGER) {
            // Manager leaving - needs confirmation
            getConfirmationManager().requestConfirmation(player,
                    ConfirmationManager.ConfirmationType.LEAVE_MANAGER,
                    player.getName(), () -> {
                        getTeamManager().removeMember(team, player.getUniqueId());
                        player.sendMessage(Teams.SUCCESS_PREFIX + plugin.getMessage("commands.player-left", "%player_name%", "You"));
                        getTeamManager().sendTeamMessage(team, Teams.ERROR_PREFIX + plugin.getMessage("notifications.manager-left"));
                    });
            return;
        }

        // Regular member can leave without confirmation
        getTeamManager().removeMember(team, player.getUniqueId());
        player.sendMessage(Teams.SUCCESS_PREFIX + plugin.getMessage("commands.player-left", "%player_name%", "You"));
        getTeamManager().sendTeamMessage(team, Teams.ERROR_PREFIX + plugin.getMessage("commands.player-left", "%player_name%", player.getName()));
    }

    private void handleConfirm(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.confirmation-expired"));
            return;
        }

        String confirmId = args[1];
        if (!getConfirmationManager().processConfirmation(player, confirmId)) {
            // Error message is already sent by processConfirmation
        }
    }

    private void handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            getConfirmationManager().cancelConfirmation(player);
        } else {
            getConfirmationManager().cancelConfirmation(player, args[1]);
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(plugin.getMessage("help.header"));
        player.sendMessage(plugin.getMessage("help.create"));
        player.sendMessage(plugin.getMessage("help.invite"));
        player.sendMessage(plugin.getMessage("help.accept"));
        player.sendMessage(plugin.getMessage("help.kick"));
        player.sendMessage(plugin.getMessage("help.promote"));
        player.sendMessage(plugin.getMessage("help.delete"));
        player.sendMessage(plugin.getMessage("help.list"));
        player.sendMessage(plugin.getMessage("help.info"));
        player.sendMessage(plugin.getMessage("help.leave"));
        player.sendMessage(plugin.getMessage("help.chat"));
        player.sendMessage("§e/team chathelp §7- Show team chat placeholders help");

        // Add reload command to help if player has admin permission
        if (player.hasPermission("teams.admin")) {
            player.sendMessage("§e/team reload §7- Reload plugin configuration");
        }
    }

    // Validation method for team names
    private boolean isValidTeamName(String name) {
        // Allow only alphanumeric characters, spaces, and some safe special characters
        return name.matches("^[a-zA-Z0-9 _-]+$");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return null;
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("create", "invite", "accept", "deny", "kick",
                    "promote", "demote", "delete", "list", "info", "leave", "help", "chathelp");

            // Add reload command to tab completion if player has admin permission
            if (player.hasPermission("teams.admin")) {
                subCommands = new ArrayList<>(subCommands);
                subCommands.add("reload");
            }

            completions.addAll(subCommands.stream()
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList()));
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            // Check if managers are initialized
            if (getTeamManager() == null) {
                return completions;
            }

            if (subCommand.equals("invite")) {
                // Show online players not in a team
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .filter(p -> getTeamManager().getPlayerTeam(p.getUniqueId()) == null)
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList()));
            } else if (subCommand.equals("kick") || subCommand.equals("promote") || subCommand.equals("demote")) {
                // Show team members
                Team team = getTeamManager().getPlayerTeam(player.getUniqueId());
                if (team != null) {
                    completions.addAll(team.getMembers().keySet().stream()
                            .map(Bukkit::getPlayer)
                            .filter(Objects::nonNull)
                            .filter(p -> !p.equals(player))
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList()));
                }
            } else if (subCommand.equals("info") || (subCommand.equals("delete") && player.hasPermission("teams.admin"))) {
                // Show all team names
                completions.addAll(getTeamManager().getAllTeams().stream()
                        .map(Team::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList()));
            }
        }

        return completions;
    }
}