package com.swag.swagjobs.manager;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import com.swag.swagjobs.model.JobProgress;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossBarManager {
    private final SwagJobsPlugin plugin;
    private final Map<UUID, BossBar> activeBars = new HashMap<>();
    private final Map<UUID, BukkitRunnable> hideTasks = new HashMap<>();

    public BossBarManager(SwagJobsPlugin plugin) {
        this.plugin = plugin;
        // REMOVED: The periodic refresh task that was forcing the bar to show every 5 seconds.
    }

    public void updateBossBar(Player player, Job job, JobProgress progress, double requiredXP) {
        if (!plugin.getConfig().getBoolean("boss-bar.enabled", true)) return;

        UUID uuid = player.getUniqueId();

        // Ensure the bar exists and the player is on it
        BossBar bar = activeBars.computeIfAbsent(uuid, k -> createNewBar());
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }

        // Calculate percentage
        double percentage = requiredXP <= 0 ? 1.0 : Math.min(1.0, progress.getXp() / requiredXP);
        int percentInt = (int) (percentage * 100);

        // Format Title
        String title = plugin.getConfig().getString("boss-bar.title", "&a{job} &7| &fLevel {level} &7| &e{xp}/{xp_required} XP &7({percentage}%)")
                .replace("{job}", job.getDisplayName())
                .replace("{level}", String.valueOf(progress.getLevel()))
                .replace("{xp}", String.format("%.1f", progress.getXp()))
                .replace("{xp_required}", String.format("%.1f", requiredXP))
                .replace("{percentage}", String.valueOf(percentInt))
                .replace("{prestige}", String.valueOf(progress.getPrestige()));

        bar.setTitle(org.bukkit.ChatColor.translateAlternateColorCodes('&', title));
        bar.setProgress(percentage);

        // Force visibility
        bar.setVisible(true);

        // Handle Auto-Hide
        if (hideTasks.containsKey(uuid)) {
            hideTasks.get(uuid).cancel();
        }

        BukkitRunnable hideTask = new BukkitRunnable() {
            @Override
            public void run() {
                bar.setVisible(false);
                hideTasks.remove(uuid);
            }
        };

        long delayTicks = (long) (plugin.getConfig().getDouble("boss-bar.hide-delay", 2.5) * 20);
        hideTask.runTaskLater(plugin, Math.max(1L, delayTicks));
        hideTasks.put(uuid, hideTask);
    }

    /**
     * Re-attaches the bar if needed (e.g. on join), but doesn't force it to be visible.
     */
    public void show(Player player) {
        if (!plugin.getConfig().getBoolean("boss-bar.enabled", true)) {
            removePlayer(player);
            return;
        }

        var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getActiveJob() == null) {
            removePlayer(player);
            return;
        }

        // We create the bar object in memory so it's ready, but we don't call updateBossBar
        // because updateBossBar sets setVisible(true).
        // The bar will now only appear when the player actually gains XP.
        activeBars.computeIfAbsent(player.getUniqueId(), k -> createNewBar());
    }

    public void removePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        BossBar bar = activeBars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }

        if (hideTasks.containsKey(uuid)) {
            hideTasks.get(uuid).cancel();
            hideTasks.remove(uuid);
        }
    }

    private BossBar createNewBar() {
        BarColor color;
        BarStyle style;
        try {
            color = BarColor.valueOf(plugin.getConfig().getString("boss-bar.color", "GREEN").toUpperCase());
        } catch (Exception e) {
            color = BarColor.GREEN;
        }
        try {
            style = BarStyle.valueOf(plugin.getConfig().getString("boss-bar.style", "SOLID").toUpperCase());
        } catch (Exception e) {
            style = BarStyle.SOLID;
        }
        return Bukkit.createBossBar("", color, style);
    }

    public void removeAll() {
        activeBars.values().forEach(BossBar::removeAll);
        activeBars.clear();
        hideTasks.values().forEach(BukkitRunnable::cancel);
        hideTasks.clear();
    }
}