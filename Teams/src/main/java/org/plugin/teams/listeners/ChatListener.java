package org.plugin.teams.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;
import org.Denis496.chatPlugin.ChatPlugin;
import org.Denis496.chatPlugin.utils.PlaceholderManager;
import org.plugin.teams.Teams;
import org.plugin.teams.managers.TeamManager;
import org.plugin.teams.models.Team;
import org.plugin.teams.models.TeamRole;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatListener implements Listener {
    private final Teams plugin;
    private final TeamManager teamManager;
    private ChatPlugin chatPlugin;
    private PlaceholderManager placeholderManager;
    private boolean chatPluginHooked = false;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    public ChatListener(Teams plugin) {
        this.plugin = plugin;
        this.teamManager = plugin.getTeamManager();
        hookIntoChatPlugin();
    }

    private void hookIntoChatPlugin() {
        Plugin chatPluginRaw = Bukkit.getPluginManager().getPlugin("ChatPlugin");
        if (chatPluginRaw != null && chatPluginRaw.isEnabled()) {
            try {
                chatPlugin = ChatPlugin.getInstance();
                if (chatPlugin != null) {
                    placeholderManager = chatPlugin.getPlaceholderManager();
                    if (placeholderManager != null) {
                        chatPluginHooked = true;
                        plugin.getLogger().info("Successfully hooked into ChatPlugin for placeholders!");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to hook into ChatPlugin: " + e.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Check if message starts with #
        if (message.startsWith("#")) {
            event.setCancelled(true);

            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("commands.not-in-team"));
                return;
            }

            // Remove # from the beginning
            String teamMessage = message.substring(1).trim();
            if (teamMessage.isEmpty()) {
                player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.empty-message"));
                return;
            }

            // Process message with ChatPlugin placeholders if available
            Component processedMessage = processTeamMessage(player, teamMessage, team);

            // Get sender's role
            TeamRole senderRole = team.getMemberRole(player.getUniqueId());
            Teams.RoleFormat roleFormat = plugin.getRoleFormat(senderRole);

            // Build the full message
            Component prefix = legacySerializer.deserialize("§aT §8| " + roleFormat.getPrefix() + roleFormat.getColor() + player.getName() + " §8› ");
            Component fullMessage = prefix.append(processedMessage);

            // Send message to all team members
            for (Player teamMember : Bukkit.getOnlinePlayers()) {
                if (team.isMember(teamMember.getUniqueId())) {
                    teamMember.sendMessage(fullMessage);
                }
            }

            // Log to console
            Bukkit.getConsoleSender().sendMessage(fullMessage);
        }
    }

    private Component processTeamMessage(Player player, String message, Team team) {
        if (!chatPluginHooked || placeholderManager == null) {
            // If ChatPlugin not available, just return colored message
            return legacySerializer.deserialize(message);
        }

        try {
            // IMPORTANT: Process all ChatPlugin placeholders in the correct order

            // 1. First, process basic placeholders like :team:, :item:, :loc:
            Component processedComponent = placeholderManager.processPlaceholdersAsComponent(player, message);

            // 2. Process marks if Marks plugin is available (:x1234:)
            if (chatPlugin.getMarksHook() != null && chatPlugin.getMarksHook().isHooked()) {
                // Convert component to string for mark processing
                String componentText = legacySerializer.serialize(processedComponent);

                // Process mark placeholders (like :x1234:)
                Component marksProcessed = chatPlugin.getMarksHook().processMarkPlaceholders(
                        legacySerializer.deserialize(componentText)
                );

                if (marksProcessed != null) {
                    processedComponent = marksProcessed;
                }
            }

            // 3. Process mentions (@player) for team members only
            if (message.contains("@")) {
                // Get team members for mention processing
                List<Player> teamMembers = new ArrayList<>();
                for (UUID memberId : team.getMembers().keySet()) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null && member.isOnline()) {
                        teamMembers.add(member);
                    }
                }

                // Process mentions if MentionManager is available
                if (chatPlugin.getMentionManager() != null && !teamMembers.isEmpty()) {
                    // Convert component to plain text for mention processing
                    String plainText = plainTextSerializer(processedComponent);

                    // Process mentions for team members only
                    String mentionProcessed = chatPlugin.getMentionManager().processMentions(
                            player,
                            plainText,
                            teamMembers
                    );

                    // Convert back to component
                    processedComponent = legacySerializer.deserialize(mentionProcessed);
                }
            }

            // 4. Additional placeholder processing if needed
            // If ChatPlugin has other placeholder processors, call them here

            return processedComponent;

        } catch (Exception e) {
            plugin.getLogger().warning("Error processing team message placeholders: " + e.getMessage());
            e.printStackTrace();

            // Fallback to basic message if processing fails
            return legacySerializer.deserialize(message);
        }
    }

    private String plainTextSerializer(Component component) {
        StringBuilder builder = new StringBuilder();
        extractText(component, builder);
        return builder.toString();
    }

    private void extractText(Component component, StringBuilder builder) {
        if (component instanceof net.kyori.adventure.text.TextComponent textComponent) {
            builder.append(textComponent.content());
        }
        // Also handle other component types if needed
        for (Component child : component.children()) {
            extractText(child, builder);
        }
    }

    public void reload() {
        chatPluginHooked = false;
        chatPlugin = null;
        placeholderManager = null;
        hookIntoChatPlugin();

        plugin.getLogger().info("ChatListener reloaded - ChatPlugin hook status: " + chatPluginHooked);
    }

    public boolean isChatPluginHooked() {
        return chatPluginHooked;
    }
}