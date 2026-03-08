package com.swag.swagjobs.manager;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import com.swag.swagjobs.model.JobProgress;
import com.swag.swagjobs.model.PlayerJobData;
import com.swag.swagjobs.model.Reward;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;

public class PlayerDataManager {
    private final SwagJobsPlugin plugin;
    private final Map<UUID, PlayerJobData> cachedData;

    public PlayerDataManager(SwagJobsPlugin plugin) {
        this.plugin = plugin;
        this.cachedData = new HashMap<>();
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

            // REPAIR LOGIC: Ensure every reached level has a reward object
            for (Job job : Job.values()) {
                JobProgress progress = data.getJobProgress(job);
                for (int i = 1; i <= progress.getLevel(); i++) {
                    final int level = i;
                    final int prestige = progress.getPrestige();

                    // Check if a reward already exists for this level/prestige
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
        // Save cached players first
        for (PlayerJobData data : cachedData.values()) {
            try {
                plugin.getDatabaseManager().savePlayerData(data); // must be synchronous
                plugin.getLogger().info("Saved data for " + data.getPlayerId());
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to save player data for " + data.getPlayerId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Extra safety: ensure any online player is saved (in case cache missed someone)
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

    /**
     * Claim all unclaimed rewards for the given job and deposit them.
     * This method now checks for reward objects (not only total money),
     * deposits each reward, marks them claimed, and saves the player data.
     */
    public void claimRewards(Player player, Job job) {
        PlayerJobData data = getPlayerData(player.getUniqueId());
        if (data == null) {
            player.sendMessage("§c§lERROR: PlayerJobData is null!");
            return;
        }

        // Get the unclaimed rewards for this job
        var rewards = data.getUnclaimedRewards(job);

        if (rewards.isEmpty()) {
            player.sendMessage("§c§lNo rewards to claim!");
            return;
        }

        Economy economy = plugin.getEconomy();
        double total = 0.0;
        int claimedCount = 0;

        // Mark each reward as claimed (in-memory) and sum money
        synchronized (data) {
            for (Reward r : rewards) {
                if (r.isClaimed()) continue;
                total += r.getMoney();
                r.claim();
                claimedCount++;
            }

            // Persist the changed reward states to DB even if economy is missing
            plugin.getDatabaseManager().savePlayerData(data);
        }

        // Try to deposit money if economy is available
        if (economy != null && total > 0.0) {
            try {
                economy.depositPlayer(player, total);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to deposit claimed rewards for " + player.getName() + ": " + e.getMessage());
                player.sendMessage("§cFailed to deposit rewards due to an economy error. They have been marked as claimed.");
                return;
            }
        } else if (economy == null && total > 0.0) {
            // Economy not hooked – still mark claimed, but inform the player
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

        // Save immediately to persist reward
        plugin.getDatabaseManager().savePlayerData(data);
    }

    // ====
    // Job Points (Restored from old file)
    // ====
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
}