package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import org.bukkit.Bukkit;
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
        // Load job data off the main thread (it's a synchronous JDBC read), then apply the
        // boss bar + daily bonus — both of which need the loaded data — back on the main
        // thread once it's ready. See PlayerDataManager#loadPlayerAsync for why this can no
        // longer go through the old synchronous loadPlayer()/getPlayerData() path here.
        plugin.getPlayerDataManager().loadPlayerAsync(player.getUniqueId()).thenAccept(data ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    try {
                        plugin.getBossBarManager().show(player);
                    } catch (Exception ignored) { }

                    try {
                        plugin.getPlayerDataManager().tryGrantDailyBonus(player);
                    } catch (Exception ignored) { }
                }));
    }

}