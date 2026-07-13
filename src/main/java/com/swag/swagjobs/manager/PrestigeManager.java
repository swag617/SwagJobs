package com.swag.swagjobs.manager;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import com.swag.swagjobs.model.JobProgress;
import com.swag.swagjobs.model.PlayerJobData;
import com.swag.swagjobs.model.Reward;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;


public class PrestigeManager {
    private final SwagJobsPlugin plugin;

    public PrestigeManager(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    public void prestige(Player player, Job job) {
        PlayerJobData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        JobProgress progress = playerData.getJobProgress(job);

        int maxLevel = plugin.getJobsConfig().getMaxLevel();

        if (progress.getLevel() < maxLevel) {
            player.sendMessage("§cYou must be level " + maxLevel + " to prestige!");
            return;
        }

        if (progress.getPrestige() >= plugin.getJobsConfig().getMaxPrestige()) {
            player.sendMessage("§cYou are already at max prestige!");
            return;
        }

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

        // Remove old prestige rewards from the in-memory list before saving
        playerData.clearRewardsForPrestige(job, currentPrestige);

        int newPrestige = progress.getPrestige() + 1;
        progress.setPrestige(newPrestige);
        progress.setLevel(1);
        progress.setXp(0);

        plugin.getPlayerDataManager().addJobPoints(player.getUniqueId(), 100);

        // Delete from DB as well; in-memory list was already cleared above
        plugin.getDatabaseManager().deletePrestigeRewards(player.getUniqueId(), job, currentPrestige);

        plugin.getDatabaseManager().savePlayerData(playerData);

        // Deposit auto-claimed money only after successful save
        // MIGRATED: plugin.getEconomy().depositPlayer(player, autoClaimedMoney) replaced by SwagAPI IEconomyService
        if (autoClaimedMoney > 0 && plugin.getEcoService() != null && plugin.getEcoService().isEnabled()) {
            plugin.getEcoService().deposit(player, autoClaimedMoney);
            player.sendMessage("§a§lAUTO-CLAIMED §7" + rewardsClaimed +
                    " unclaimed rewards (§a₣" + String.format("%.2f", autoClaimedMoney) + "§7)");
        }

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

        if (plugin.getTagManager() != null) {
            plugin.getTagManager().handlePrestigeTag(player, job, newPrestige);
        }
    }
}