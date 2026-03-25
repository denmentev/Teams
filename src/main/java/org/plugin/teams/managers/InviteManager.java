package org.plugin.teams.managers;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.plugin.teams.Teams;
import org.plugin.teams.models.Team;
import org.plugin.teams.models.TeamInvite;

import java.util.*;

public class InviteManager {
    private final Teams plugin;
    private final Map<UUID, List<TeamInvite>> playerInvites;
    private final Map<String, Long> inviteCooldowns; // teamId:playerId -> timestamp

    private static final long INVITE_EXPIRY_TIME = 20 * 1000; // 20 seconds
    private static final long INVITE_COOLDOWN = 30 * 1000; // 30 seconds

    public InviteManager(Teams plugin) {
        this.plugin = plugin;
        this.playerInvites = new HashMap<>();
        this.inviteCooldowns = new HashMap<>();

        // Clean expired invites every second
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanExpiredInvites, 20L, 20L);
    }

    public boolean canInvite(String teamId, UUID inviterId, UUID targetId) {
        String cooldownKey = teamId + ":" + targetId.toString();
        Long lastInvite = inviteCooldowns.get(cooldownKey);

        if (lastInvite != null && System.currentTimeMillis() - lastInvite < INVITE_COOLDOWN) {
            return false;
        }

        return true;
    }

    public TeamInvite createInvite(String teamId, UUID inviterId, UUID targetId) {
        if (!canInvite(teamId, inviterId, targetId)) {
            return null;
        }

        TeamInvite invite = new TeamInvite(teamId, inviterId, targetId);

        playerInvites.computeIfAbsent(targetId, k -> new ArrayList<>()).add(invite);
        inviteCooldowns.put(teamId + ":" + targetId.toString(), System.currentTimeMillis());

        return invite;
    }

    public void sendInviteMessage(Player inviter, Player target, Team team) {
        target.sendMessage("");
        target.sendMessage(plugin.getMessage("commands.invite-received",
                "%team_name%", team.getName()));
        target.sendMessage(plugin.getMessage("commands.invite-from",
                "%inviter_name%", inviter.getName()));

        // Create clickable accept/deny message
        TextComponent accept = new TextComponent(plugin.getMessage("confirmations.confirm-button")
                .replace("CONFIRM", "ACCEPT"));
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team accept"));
        accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§aClick to join the team").create()));

        TextComponent space = new TextComponent("  ");

        TextComponent deny = new TextComponent(plugin.getMessage("confirmations.cancel-button")
                .replace("CANCEL", "DENY"));
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team deny"));
        deny.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§cClick to decline").create()));

        TextComponent message = new TextComponent("");
        message.addExtra(accept);
        message.addExtra(space);
        message.addExtra(deny);

        target.spigot().sendMessage(message);
        target.sendMessage(Teams.INFO_PREFIX + plugin.getMessage("commands.invite-expired"));
        target.sendMessage("");
    }

    public List<TeamInvite> getPlayerInvites(UUID playerId) {
        List<TeamInvite> invites = playerInvites.get(playerId);
        if (invites == null) return new ArrayList<>();

        // Filter expired invites
        invites.removeIf(TeamInvite::isExpired);
        return new ArrayList<>(invites);
    }

    public TeamInvite getLatestInvite(UUID playerId) {
        List<TeamInvite> invites = getPlayerInvites(playerId);
        if (invites.isEmpty()) return null;

        return invites.get(invites.size() - 1);
    }

    public void removeInvite(TeamInvite invite) {
        List<TeamInvite> invites = playerInvites.get(invite.getTargetId());
        if (invites != null) {
            invites.remove(invite);
            if (invites.isEmpty()) {
                playerInvites.remove(invite.getTargetId());
            }
        }
    }

    public void removeAllInvites(UUID playerId) {
        playerInvites.remove(playerId);
    }

    private void cleanExpiredInvites() {
        playerInvites.values().forEach(invites -> invites.removeIf(TeamInvite::isExpired));
        playerInvites.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public long getRemainingCooldown(String teamId, UUID targetId) {
        String cooldownKey = teamId + ":" + targetId.toString();
        Long lastInvite = inviteCooldowns.get(cooldownKey);

        if (lastInvite == null) return 0;

        long elapsed = System.currentTimeMillis() - lastInvite;
        if (elapsed >= INVITE_COOLDOWN) return 0;

        return (INVITE_COOLDOWN - elapsed) / 1000; // return in seconds
    }
}