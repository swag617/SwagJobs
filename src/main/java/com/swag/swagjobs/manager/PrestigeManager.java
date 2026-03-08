package com.swag.swagjobs.manager;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import com.swag.swagjobs.model.JobProgress;
import com.swag.swagjobs.model.PlayerJobData;
import com.swag.swagjobs.model.Reward;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PrestigeManager {
    private final SwagJobsPlugin plugin;

    public PrestigeManager(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    public void prestige(Player player, Job job) {
        PlayerJobData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        JobProgress progress = playerData.getJobProgress(job);

        int maxLevel = plugin.getJobsConfig().getMaxLevel();

        // Validation
        if (progress.getLevel() < maxLevel) {
            player.sendMessage("§cYou must be level " + maxLevel + " to prestige!");
            return;
        }

        if (progress.getPrestige() >= plugin.getJobsConfig().getMaxPrestige()) {
            player.sendMessage("§cYou are already at max prestige!");
            return;
        }

        // ===============================
        // AUTO-CLAIM UNCLAIMED REWARDS
        // ===============================
        int currentPrestige = progress.getPrestige();
        int currentLevel = progress.getLevel();

        double autoClaimedMoney = 0.0;
        int rewardsClaimed = 0;

        for (int level = 1; level <= currentLevel; level++) {
            final int checkLevel = level;

            boolean alreadyClaimed = playerData.getRewards().stream()
                    .anyMatch(r -> r.getJob() == job
                            && r.getLevel() == checkLevel
                            && r.getPrestige() == currentPrestige
                            && r.isClaimed());

            if (!alreadyClaimed) {
                double rewardAmount = plugin.getJobsConfig().getMoneyReward(level, currentPrestige);
                playerData.addReward(new Reward(job, level, currentPrestige, rewardAmount, true));
                autoClaimedMoney += rewardAmount;
                rewardsClaimed++;
            }
        }

        if (autoClaimedMoney > 0 && plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(player, autoClaimedMoney);
            player.sendMessage("§a§lAUTO-CLAIMED §7" + rewardsClaimed +
                    " unclaimed rewards (§a₣" + String.format("%.2f", autoClaimedMoney) + "§7)");
        }

        // ===============================
        // CLEAR OLD PRESTIGE REWARDS
        // ===============================
        // Remove all rewards from the previous prestige for this job
        // This allows players to claim rewards again in the new prestige
        List<Reward> rewardsToKeep = new ArrayList<>();
        for (Reward reward : playerData.getRewards()) {
            // Keep rewards from other jobs or from future prestiges (shouldn't happen but safety)
            if (reward.getJob() != job || reward.getPrestige() != currentPrestige) {
                rewardsToKeep.add(reward);
            }
            // All rewards from current prestige for this job are removed
        }

        // Replace rewards list with filtered list (must use the mutable backing list)
        playerData.getUnclaimedRewards().clear();
        playerData.getUnclaimedRewards().addAll(rewardsToKeep);

        // ===============================
        // PERFORM PRESTIGE
        // ===============================
        int newPrestige = progress.getPrestige() + 1;
        progress.setPrestige(newPrestige);
        progress.setLevel(1);
        progress.setXp(0);

        // Award prestige points (job points)
        plugin.getPlayerDataManager().addJobPoints(player.getUniqueId(), 100);

        // CRITICAL FIX: Delete old prestige rewards from DATABASE
        // The in-memory list was already cleared, but DB still has them
        plugin.getDatabaseManager().deletePrestigeRewards(player.getUniqueId(), job, currentPrestige);

        // Save
        plugin.getDatabaseManager().savePlayerData(playerData);

        // Celebration
        String jobName = ChatColor.translateAlternateColorCodes('&', job.getDisplayName());

        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.sendTitle(
                ChatColor.translateAlternateColorCodes('&', "&5&l✦ PRESTIGED ✦"),
                ChatColor.translateAlternateColorCodes('&',
                        "&d" + jobName + " &7→ Prestige &d" + newPrestige),
                10, 70, 20
        );

        player.sendMessage("§8§m                    ");
        player.sendMessage("§d§lPRESTIGE COMPLETE!");
        player.sendMessage("§7Job: §f" + jobName);
        player.sendMessage("§7New Prestige: §d" + newPrestige);
        player.sendMessage("§7XP Multiplier: §a+" + (newPrestige * 2) + "%");
        player.sendMessage("§7Money Multiplier: §a+" + (newPrestige * 8) + "%");
        player.sendMessage("§7Job Points Earned: §e+100");
        player.sendMessage("§8§m                    ");

        // ===============================
        // GRANT TAG (if at milestone: 1, 5, or 10)
        // ===============================
        if (plugin.getTagManager() != null) {
            plugin.getTagManager().handlePrestigeTag(player, job, newPrestige);
        }
    }
}