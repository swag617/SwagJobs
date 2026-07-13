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
        plugin.getBossBarManager().removePlayer(event.getPlayer());
        plugin.getPlayerDataManager().unloadPlayer(event.getPlayer());
        plugin.getJobManager().cleanupPlayer(event.getPlayer().getUniqueId());
        plugin.getPlaceBreakManager().cleanupPlayer(event.getPlayer().getUniqueId());
    }
}