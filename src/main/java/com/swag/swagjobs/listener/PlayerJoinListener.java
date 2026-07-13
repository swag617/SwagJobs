package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {
    private final SwagJobsPlugin plugin;

    public PlayerJoinListener(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getPlayerDataManager().loadPlayer(player);

        try {
            plugin.getBossBarManager().show(player);
        } catch (Exception ignored) { }

        try {
            plugin.getPlayerDataManager().tryGrantDailyBonus(player);
        } catch (Exception ignored) { }
    }

}