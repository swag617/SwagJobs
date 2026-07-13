package com.swag.swagjobs.manager;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import com.swag.swagjobs.model.JobProgress;
import com.swag.swagjobs.model.PlayerJobData;
import com.swag.swagjobs.model.Reward;
// MIGRATED: net.milkbowl.vault.economy.Economy replaced by SwagAPI IEconomyService
// import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {
    private final SwagJobsPlugin plugin;
    private final Map<UUID, PlayerJobData> cachedData;

    public PlayerDataManager(SwagJobsPlugin plugin) {
        this.plugin = plugin;
        this.cachedData = new ConcurrentHashMap<>();
    }

    public void loadPlayer(Player player) {
        getPlayerData(player.getUniqueId());
    }

    public void unloadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerJobData data = cachedData.remove(uuid);
        if (data != null) {
            plugin.getDatabaseManager().savePlayerData(data);
        }
    }

    public PlayerJobData getPlayerData(UUID uuid) {
        return cachedData.computeIfAbsent(uuid, id -> {
            PlayerJobData data = plugin.getDatabaseManager().loadPlayerData(id);
            if (data == null) {
                data = new PlayerJobData(id);
            }

            for (Job job : Job.values()) {
                JobProgress progress = data.getJobProgress(job);
                for (int i = 1; i <= progress.getLevel(); i++) {
                    final int level = i;
                    final int prestige = progress.getPrestige();

                    boolean exists = data.getRewards().stream()
                            .anyMatch(r -> r.getJob() == job && r.getLevel() == level && r.getPrestige() == prestige);

                    if (!exists) {
                        double amount = plugin.getJobsConfig().getMoneyReward(level, prestige);
                        data.addReward(new Reward(job, level, prestige, amount, false));
                    }
                }
            }
            return data;
        });
    }

    public void saveAll() {
        plugin.getLogger().info("SwagJobs: Saving all player data (" + cachedData.size() + " cached entries)");
        for (PlayerJobData data : cachedData.values()) {
            try {
                plugin.getDatabaseManager().savePlayerData(data);
                plugin.getLogger().info("Saved data for " + data.getPlayerId());
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to save player data for " + data.getPlayerId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            if (!cachedData.containsKey(uuid)) {
                PlayerJobData data = plugin.getPlayerDataManager().getPlayerData(uuid);
                if (data != null) {
                    try {
                        plugin.getDatabaseManager().savePlayerData(data);
                        plugin.getLogger().info("Saved online-but-not-cached player data for " + p.getName() + " (" + uuid + ")");
                    } catch (Exception e) {
                        plugin.getLogger().severe("Failed to save on-save for online player " + p.getName() + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    plugin.getLogger().warning("No PlayerJobData instance for online player " + p.getName() + " (" + uuid + ")");
                }
            }
        }
    }

    public void claimRewards(Player player, Job job) {
        PlayerJobData data = getPlayerData(player.getUniqueId());
        if (data == null) {
            player.sendMessage("§c§lERROR: PlayerJobData is null!");
            return;
        }

        var rewards = data.getUnclaimedRewards(job);

        if (rewards.isEmpty()) {
            player.sendMessage("§c§lNo rewards to claim!");
            return;
        }

        // MIGRATED: net.milkbowl.vault.economy.Economy replaced by SwagAPI IEconomyService
        com.SwagDev.SwagAPI.api.IEconomyService economy = plugin.getEcoService();
        boolean economyUsable = economy != null && economy.isEnabled();
        double total = 0.0;
        int claimedCount = 0;

        for (Reward r : rewards) {
            if (r.isClaimed()) continue;
            total += r.getMoney();
            r.claim();
            claimedCount++;
        }

        plugin.getDatabaseManager().savePlayerData(data);

        if (economyUsable && total > 0.0) {
            try {
                economy.deposit(player, total);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to deposit claimed rewards for " + player.getName() + ": " + e.getMessage());
                player.sendMessage("§cFailed to deposit rewards due to an economy error. They have been marked as claimed.");
                return;
            }
        } else if (!economyUsable && total > 0.0) {
            player.sendMessage("§eEconomy not hooked – rewards were marked claimed but not paid out. Contact an admin.");
        }

        if (claimedCount > 0) {
            player.sendMessage("§a§lClaimed §f" + String.format("%.2f", total) + " §afrom " + claimedCount + " reward(s)!");
        } else {
            player.sendMessage("§c§lNo rewards to claim!");
        }
    }

    public void addUnclaimedReward(UUID uuid, Job job, double money) {
        PlayerJobData data = getPlayerData(uuid);
        if (data == null) return;

        JobProgress progress = data.getJobProgress(job);
        data.addReward(new Reward(job, progress.getLevel(), progress.getPrestige(), money));

        plugin.getDatabaseManager().savePlayerData(data);
    }

    public int getTotalJobPoints(UUID uuid) {
        PlayerJobData data = getPlayerData(uuid);
        return data.getJobPoints();
    }

    public boolean spendJobPoints(UUID uuid, int amount) {
        if (amount <= 0) return true;
        PlayerJobData data = getPlayerData(uuid);
        if (!data.spendJobPoints(amount)) return false;
        plugin.getDatabaseManager().savePlayerData(data);
        return true;
    }

    public void addJobPoints(UUID uuid, int amount) {
        if (amount <= 0) return;
        PlayerJobData data = getPlayerData(uuid);
        data.addJobPoints(amount);
        plugin.getDatabaseManager().savePlayerData(data);
    }

    @Deprecated
    public int getTotalPrestigePoints(UUID uuid) {
        return getTotalJobPoints(uuid);
    }

    @Deprecated
    public boolean spendPrestigePoints(UUID uuid, int amount) {
        return spendJobPoints(uuid, amount);
    }

    @Deprecated
    public void addPrestigePoints(UUID uuid, int amount) {
        addJobPoints(uuid, amount);
    }

    /**
     * Grants the configured daily job-points bonus the first time this is called for a
     * player on a given calendar day (tracked in the player_daily_bonus table). Safe to
     * call on every join — it's a no-op if the player already claimed today's bonus.
     */
    public void tryGrantDailyBonus(Player player) {
        if (!plugin.getConfig().getBoolean("daily-bonus.enabled", true)) return;

        UUID uuid = player.getUniqueId();
        if (!plugin.getDatabaseManager().tryClaimDailyBonus(uuid)) return;

        int points = plugin.getConfig().getInt("daily-bonus.job-points", 25);
        if (points <= 0) return;

        addJobPoints(uuid, points);

        String message = plugin.getConfig().getString("daily-bonus.message",
                "&a&lDAILY BONUS &8» &7You received &a{points} job points &7for playing today!");
        message = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                message.replace("{points}", String.valueOf(points)));
        player.sendMessage(message);
    }
}