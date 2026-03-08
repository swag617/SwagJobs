package com.swag.swagjobs.manager;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import com.swag.swagjobs.model.JobProgress;
import com.swag.swagjobs.model.Reward;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class JobManager {
    private final SwagJobsPlugin plugin;
    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0.00");

    // ====================================================================
    // FIX: Streak is now tracked PER-PLAYER PER-JOB instead of per-player.
    // Key format: "UUID:JOB_NAME" (e.g. "abc-123:LUMBERJACK")
    // This prevents a streak built in one job from applying to other jobs.
    // ====================================================================
    private final Map<String, Integer> playerStreaks = new HashMap<>();
    private final Map<String, Long> lastActionTime = new HashMap<>();
    private static final long STREAK_TIMEOUT = 5000;

    // Streak scaling: logarithmic diminishing returns
    // Streak 100 = 2.0x, 250 = 2.5x, 500 = 3.0x, 1000 = 3.5x, 2000 = 4.0x (hard cap)
    private static final int MAX_STREAK = 2000;
    private static final double MAX_STREAK_MULTIPLIER = 4.0;

    // Debug toggle per-player (resets on restart)
    private final Set<UUID> debugPlayers = new HashSet<>();

    public JobManager(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Build a composite key for per-player-per-job streak tracking.
     */
    private String streakKey(UUID uuid, Job job) {
        return uuid.toString() + ":" + job.name();
    }

    public void cleanupPlayer(UUID uuid) {
        for (Job job : Job.values()) {
            String key = uuid + ":" + job.name();
            playerStreaks.remove(key);
            lastActionTime.remove(key);
        }
        debugPlayers.remove(uuid);
    }

    public void toggleDebug(Player player) {
        UUID uuid = player.getUniqueId();
        if (debugPlayers.contains(uuid)) {
            debugPlayers.remove(uuid);
            player.sendMessage("§c§lDEBUG §8» §7Job debug mode §cdisabled§7.");
        } else {
            debugPlayers.add(uuid);
            player.sendMessage("§a§lDEBUG §8» §7Job debug mode §aenabled§7. You will now see XP/Money breakdowns.");
        }
    }

    public boolean isDebugging(Player player) {
        return debugPlayers.contains(player.getUniqueId());
    }

    public void processAction(Player player, Job job, String actionName) {
        plugin.getCheatDetectionManager().recordAction(player);
        updateStreak(player, job);

        double baseXP = plugin.getJobsConfig().getActionXP(job, actionName);
        double baseMoney = plugin.getJobsConfig().getActionMoney(job, actionName);

        addExperience(player, job, actionName, baseXP, baseMoney);
    }

    /**
     * Update streak for a specific player+job combination.
     * If more than STREAK_TIMEOUT ms have passed since their last action
     * in THIS specific job, the streak resets to 1.
     */
    private void updateStreak(Player player, Job job) {
        String key = streakKey(player.getUniqueId(), job);
        long now = System.currentTimeMillis();
        if (now - lastActionTime.getOrDefault(key, 0L) > STREAK_TIMEOUT) {
            playerStreaks.put(key, 1);
        } else {
            playerStreaks.put(key, playerStreaks.getOrDefault(key, 0) + 1);
        }
        lastActionTime.put(key, now);
    }

    /**
     * Get the streak multiplier for a specific player+job.
     * Uses a power curve for diminishing returns:
     *   Streak 0:    1.0x
     *   Streak 10:   1.4x
     *   Streak 50:   1.8x
     *   Streak 100:  2.0x  (fast early gains)
     *   Streak 250:  2.4x  (slowing down)
     *   Streak 500:  2.8x
     *   Streak 1000: 3.3x
     *   Streak 2000: 4.0x  (hard cap)
     *
     * Formula: 1.0 + 3.0 * (streak / MAX_STREAK) ^ 0.3667
     * The exponent 0.3667 is calibrated so streak 100 = exactly 2.0x.
     */
    public double getStreakMultiplier(Player player, Job job) {
        String key = streakKey(player.getUniqueId(), job);
        int streak = playerStreaks.getOrDefault(key, 0);
        if (streak <= 0) return 1.0;

        // Clamp to max streak
        int clampedStreak = Math.min(streak, MAX_STREAK);

        // Power curve: rises fast at first, then flattens out
        // Exponent 0.3667 calibrated so streak=100 gives exactly 2.0x
        double progress = Math.pow((double) clampedStreak / MAX_STREAK, 0.3667);
        double mult = 1.0 + (MAX_STREAK_MULTIPLIER - 1.0) * progress;

        // Round to 1 decimal for clean display
        return Math.round(mult * 10.0) / 10.0;
    }

    /**
     * Get the current streak count for a specific player+job.
     */
    public int getStreak(Player player, Job job) {
        String key = streakKey(player.getUniqueId(), job);
        return playerStreaks.getOrDefault(key, 0);
    }

    public double getXPMultiplier(Player player, int prestige) {
        double rankMult = plugin.getJobsConfig().getRankXPMultiplier(player);
        double prestigeMult = 1.0 + (prestige * plugin.getJobsConfig().getPrestigeXPMultiplier());
        return rankMult * prestigeMult;
    }

    public double getMoneyMultiplier(Player player, Job job, int prestige) {
        double prestigeMult = 1.0 + (prestige * plugin.getJobsConfig().getPrestigeMoneyMultiplier());
        return prestigeMult * getStreakMultiplier(player, job);
    }

    public void addExperience(Player player, Job job, String actionName, double baseXP, double baseMoney) {
        var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        JobProgress progress = data.getJobProgress(job);
        if (progress.getLevel() >= plugin.getJobsConfig().getMaxLevel()) return;

        // Normalize action key for logging/fallback lookups
        String normalizedAction = actionName == null ? "" : actionName.toLowerCase().replace(" ", "_").replace("-", "_");

        // If the config didn't provide XP for this action, try job-level default, then global default
        if (baseXP <= 0.0) {
            String xpPath = "jobs." + job.name().toLowerCase() + ".default-xp";
            baseXP = plugin.getConfig().getDouble(xpPath, plugin.getConfig().getDouble("jobs.default-xp", 0.05));
        }

        // XP: apply multipliers and add
        double xpMult = getXPMultiplier(player, progress.getPrestige());
        double finalXP = baseXP * xpMult;
        progress.setXp(progress.getXp() + finalXP);

        // Resolve money: prefer explicit action money, then job default, then money-per-xp
        if (baseMoney <= 0.0) {
            String moneyPath = "jobs." + job.name().toLowerCase() + ".default-money";
            baseMoney = plugin.getConfig().getDouble(moneyPath, -1.0);
            if (baseMoney <= 0.0) {
                double moneyPerXp = plugin.getConfig().getDouble("money.money-per-xp", 0.05);
                baseMoney = baseXP * moneyPerXp;
            }
        }

        // Money (apply prestige/streak multipliers) - now per-job streak!
        double moneyMult = getMoneyMultiplier(player, job, progress.getPrestige());
        double rawFinalMoney = baseMoney * moneyMult;

        // Round to 2 decimals so tiny values are visible/consistent
        double finalMoney = Math.round(rawFinalMoney * 100.0) / 100.0;

        // Debug (only when player toggles debug)
        if (isDebugging(player)) {
            double rankMult = plugin.getJobsConfig().getRankXPMultiplier(player);
            double prestigeXpMult = 1.0 + (progress.getPrestige() * plugin.getJobsConfig().getPrestigeXPMultiplier());
            double prestigeMoneyMult = 1.0 + (progress.getPrestige() * plugin.getJobsConfig().getPrestigeMoneyMultiplier());
            double streakMult = getStreakMultiplier(player, job);

            player.sendMessage(" ");
            player.sendMessage("§b§lDEBUG §8» §f" + job.name() + " §7(" + normalizedAction + ")");
            player.sendMessage("§8» §7XP: §a+" + String.format("%.2f", finalXP)
                    + " §8(§7Base: " + String.format("%.2f", baseXP)
                    + " §8| §7Rank: " + String.format("%.2f", rankMult) + "x"
                    + " §8| §7Prestige: " + String.format("%.2f", prestigeXpMult) + "x§8)");
            player.sendMessage("§8» §7₣: §6+₣" + moneyFormat.format(finalMoney)
                    + " §8(§7Base: " + String.format("%.2f", baseMoney)
                    + " §8| §7Prestige: " + String.format("%.2f", prestigeMoneyMult) + "x"
                    + " §8| §7Streak: " + String.format("%.2f", streakMult) + "x§8)");
            player.sendMessage(" ");
        }

        // Deposit (only if economy hooked)
        if (plugin.getEconomy() != null && finalMoney > 0.0) {
            try {
                plugin.getEconomy().depositPlayer(player, finalMoney);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to deposit money for " + player.getName() + ": " + e.getMessage());
            }
        }

        // Always show action-bar so players always see streak/multiplier info
        {
            int streak = getStreak(player, job);
            double sMult = getStreakMultiplier(player, job);
            String streakColor = sMult >= 2.0 ? "§6§l" : (sMult > 1.0 ? "§e" : "§7");

            String message = "§a+₣" + moneyFormat.format(finalMoney) + " §8| " + streakColor + "Streak: " + streak + " (" + String.format("%.1fx", sMult) + ")";
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        }

        // Level up check and bossbar update
        double requiredXP = plugin.getJobsConfig().getXpRequired(job, progress.getLevel(), progress.getPrestige());
        if (progress.getXp() >= requiredXP) {
            levelUp(player, job, progress);
            // recompute requiredXP for the new level
            requiredXP = plugin.getJobsConfig().getXpRequired(job, progress.getLevel(), progress.getPrestige());
        }

        plugin.getBossBarManager().updateBossBar(player, job, progress, requiredXP);
    }

    private void levelUp(Player player, Job job, JobProgress progress) {
        progress.setLevel(progress.getLevel() + 1);
        progress.setXp(0);

        double moneyReward = plugin.getJobsConfig().getMoneyReward(progress.getLevel(), progress.getPrestige());
        var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Use the new constructor with prestige!
        data.addReward(new Reward(job, progress.getLevel(), progress.getPrestige(), moneyReward));

        // Chat message (no on-screen title, no sound - leveling is fast now)
        player.sendMessage(" ");
        player.sendMessage("§a§lLEVEL UP! §8» §7You are now level §f" + progress.getLevel() + " §7in §f" + job.getDisplayName() + "§7!");
        player.sendMessage("§8» §7A reward of §a₣" + moneyFormat.format(moneyReward) + " §7has been added to your collection menu.");
        player.sendMessage(" ");

        plugin.getDatabaseManager().savePlayerData(data);
    }
}