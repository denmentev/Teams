package org.plugin.teams.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.plugin.teams.Teams;

public class PlayerJoinListener implements Listener {
    private final Teams plugin;

    public PlayerJoinListener(Teams plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Update player name in database asynchronously to avoid blocking
        // This prevents the server freeze issue
        if (plugin.isDatabaseConnected()) {
            // Run database update asynchronously
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    plugin.getDatabaseManager().updatePlayerName(player.getUniqueId());
                } catch (Exception e) {
                    // Log error but don't crash
                    plugin.getLogger().warning("Failed to update player name on join for " +
                            player.getName() + ": " + e.getMessage());
                }
            });
        }
    }
}