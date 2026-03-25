package org.plugin.teams.managers;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.plugin.teams.Teams;

import java.util.*;

public class ConfirmationManager {
    private final Teams plugin;
    private final Map<UUID, PendingConfirmation> pendingConfirmations = new HashMap<>();
    private final Set<String> processedConfirmations = new HashSet<>(); // Track processed confirmations

    public ConfirmationManager(Teams plugin) {
        this.plugin = plugin;

        // Clean up expired confirmations every second
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanExpired, 20L, 20L);
    }

    public void requestConfirmation(Player player, ConfirmationType type, String targetName, Runnable onConfirm) {
        // Remove any existing confirmation for this player
        PendingConfirmation oldConfirmation = pendingConfirmations.remove(player.getUniqueId());
        if (oldConfirmation != null) {
            // Mark old confirmation as processed to prevent reuse
            processedConfirmations.add(oldConfirmation.id);
        }

        // Create new confirmation
        String confirmId = UUID.randomUUID().toString();
        PendingConfirmation confirmation = new PendingConfirmation(confirmId, type, targetName, onConfirm);
        pendingConfirmations.put(player.getUniqueId(), confirmation);

        // Send confirmation message
        sendConfirmationMessage(player, type, targetName, confirmId);
    }

    private void sendConfirmationMessage(Player player, ConfirmationType type, String targetName, String confirmId) {
        String questionKey = switch (type) {
            case KICK -> "confirmations.kick-question";
            case DEMOTE -> "confirmations.demote-question";
            case PROMOTE -> "confirmations.promote-question";
            case PROMOTE_TO_OWNER -> "confirmations.promote-owner-question";
            case DELETE -> "confirmations.delete-question";
            case LEAVE_MANAGER -> "confirmations.leave-manager-question";
            case LEAVE_OWNER_ALONE -> "confirmations.leave-owner-alone-question";
        };

        String question = plugin.getMessage(questionKey, "%target%", targetName);

        player.sendMessage("");
        player.sendMessage(question);

        // Create clickable confirm/cancel buttons
        TextComponent confirm = new TextComponent(plugin.getMessage("confirmations.confirm-button"));
        confirm.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team confirm " + confirmId));
        confirm.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(plugin.getMessage("confirmations.confirm-hover")).create()));

        TextComponent space = new TextComponent("  ");

        TextComponent cancel = new TextComponent(plugin.getMessage("confirmations.cancel-button"));
        cancel.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team cancel " + confirmId));
        cancel.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(plugin.getMessage("confirmations.cancel-hover")).create()));

        TextComponent message = new TextComponent("");
        message.addExtra(confirm);
        message.addExtra(space);
        message.addExtra(cancel);

        player.spigot().sendMessage(message);
        player.sendMessage(Teams.INFO_PREFIX + plugin.getMessage("confirmations.expires-in", "%seconds%", "20"));
        player.sendMessage("");
    }

    public boolean processConfirmation(Player player, String confirmId) {
        // Check if this confirmation was already processed
        if (processedConfirmations.contains(confirmId)) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.confirmation-already-processed",
                    "%action%", plugin.getMessage("validation.confirmed")));
            return false;
        }

        PendingConfirmation confirmation = pendingConfirmations.get(player.getUniqueId());

        if (confirmation == null || !confirmation.id.equals(confirmId)) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.confirmation-expired"));
            return false;
        }

        if (confirmation.isExpired()) {
            pendingConfirmations.remove(player.getUniqueId());
            processedConfirmations.add(confirmId);
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.confirmation-expired"));
            return false;
        }

        // Execute the confirmation action
        confirmation.onConfirm.run();
        pendingConfirmations.remove(player.getUniqueId());
        processedConfirmations.add(confirmId); // Mark as processed
        return true;
    }

    public void cancelConfirmation(Player player) {
        PendingConfirmation confirmation = pendingConfirmations.remove(player.getUniqueId());
        if (confirmation != null) {
            processedConfirmations.add(confirmation.id); // Mark as processed
            player.sendMessage(Teams.SUCCESS_PREFIX + plugin.getMessage("validation.action-cancelled"));
        } else {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.confirmation-expired"));
        }
    }

    // Handle cancel with specific ID
    public void cancelConfirmation(Player player, String confirmId) {
        // Check if this confirmation was already processed
        if (processedConfirmations.contains(confirmId)) {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.confirmation-already-processed",
                    "%action%", plugin.getMessage("validation.cancelled")));
            return;
        }

        PendingConfirmation confirmation = pendingConfirmations.get(player.getUniqueId());

        if (confirmation != null && confirmation.id.equals(confirmId)) {
            pendingConfirmations.remove(player.getUniqueId());
            processedConfirmations.add(confirmId); // Mark as processed
            player.sendMessage(Teams.SUCCESS_PREFIX + plugin.getMessage("validation.action-cancelled"));
        } else {
            player.sendMessage(Teams.ERROR_PREFIX + plugin.getMessage("validation.confirmation-expired"));
        }
    }

    private void cleanExpired() {
        // Clean expired pending confirmations
        Iterator<Map.Entry<UUID, PendingConfirmation>> iterator = pendingConfirmations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingConfirmation> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                processedConfirmations.add(entry.getValue().id);
                iterator.remove();
            }
        }

        // Clean old processed confirmations (keep for 5 minutes)
        processedConfirmations.removeIf(id -> {
            // Since we don't track timestamp for processed confirmations,
            // we'll clear them periodically to prevent memory growth
            // This is called every second, so we'll use a random chance to clean
            return Math.random() < 0.001; // ~0.1% chance per second = clears in ~16-17 minutes on average
        });
    }

    public enum ConfirmationType {
        KICK, DEMOTE, PROMOTE, PROMOTE_TO_OWNER, DELETE, LEAVE_MANAGER, LEAVE_OWNER_ALONE
    }

    private static class PendingConfirmation {
        final String id;
        final ConfirmationType type;
        final String targetName;
        final Runnable onConfirm;
        final long timestamp;

        PendingConfirmation(String id, ConfirmationType type, String targetName, Runnable onConfirm) {
            this.id = id;
            this.type = type;
            this.targetName = targetName;
            this.onConfirm = onConfirm;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 20000; // 20 seconds
        }
    }
}