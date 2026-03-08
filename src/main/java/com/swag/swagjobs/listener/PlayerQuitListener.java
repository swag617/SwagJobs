package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {
    private final SwagJobsPlugin plugin;

    public PlayerQuitListener(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Clean up the boss bar so it doesn't break on next join
        plugin.getBossBarManager().removePlayer(event.getPlayer());

        // Use the correct method from your PlayerDataManager to save and remove from cache
        plugin.getPlayerDataManager().unloadPlayer(event.getPlayer());

        // Clean up per-player streak and cheat-detection maps to prevent memory leaks
        plugin.getJobManager().cleanupPlayer(event.getPlayer().getUniqueId());
        plugin.getCheatDetectionManager().cleanupPlayer(event.getPlayer().getUniqueId());
    }
}