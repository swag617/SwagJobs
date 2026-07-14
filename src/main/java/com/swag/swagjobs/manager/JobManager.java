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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class JobManager {
    private final SwagJobsPlugin plugin;
    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0.00");

    // Streak key format: "UUID:JOB_NAME" — tracked per-player per-job so streaks don't bleed across jobs
    private final Map<String, Integer> playerStreaks = new HashMap<>();
    private final Map<String, Long> lastActionTime = new HashMap<>();
    private static final long STREAK_TIMEOUT = 5000;

    private static final int MAX_STREAK = 2000;
    private static final double MAX_STREAK_MULTIPLIER = 4.0;

    private final Set<UUID> debugPlayers = new HashSet<>();

    public JobManager(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

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

        int clampedStreak = Math.min(streak, MAX_STREAK);

        // Exponent 0.3667 calibrated so streak=100 gives exactly 2.0x
        double progress = Math.pow((double) clampedStreak / MAX_STREAK, 0.3667);
        double mult = 1.0 + (MAX_STREAK_MULTIPLIER - 1.0) * progress;

        return Math.round(mult * 10.0) / 10.0;
    }

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

        String normalizedAction = actionName == null ? "" : actionName.toLowerCase().replace(" ", "_").replace("-", "_");

        if (baseXP <= 0.0) {
            String xpPath = "jobs." + job.name().toLowerCase() + ".default-xp";
            baseXP = plugin.getConfig().getDouble(xpPath, plugin.getConfig().getDouble("jobs.default-xp", 0.05));
        }

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

        double moneyMult = getMoneyMultiplier(player, job, progress.getPrestige());
        double rawFinalMoney = baseMoney * moneyMult;

        // Round to 2 decimals so tiny values are visible/consistent
        double finalMoney = Math.round(rawFinalMoney * 100.0) / 100.0;

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

        // MIGRATED: plugin.getEconomy().depositPlayer(player, finalMoney) replaced by SwagAPI IEconomyService
        if (plugin.getEcoService() != null && plugin.getEcoService().isEnabled() && finalMoney > 0.0) {
            try {
                plugin.getEcoService().deposit(player, finalMoney);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to deposit money for " + player.getName() + ": " + e.getMessage());
            }
        }

        {
            int streak = getStreak(player, job);
            double sMult = getStreakMultiplier(player, job);
            String streakColor = sMult >= 2.0 ? "§6§l" : (sMult > 1.0 ? "§e" : "§7");

            String message = "§a+₣" + moneyFormat.format(finalMoney) + " §8| " + streakColor + "Streak: " + streak + " (" + String.format("%.1fx", sMult) + ")";
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        }

        double requiredXP = plugin.getJobsConfig().getXpRequired(job, progress.getLevel(), progress.getPrestige());

        double prevXp = progress.getXp() - finalXP;
        checkMilestonePing(player, job, progress.getLevel(), prevXp, progress.getXp(), requiredXP);

        if (progress.getXp() >= requiredXP) {
            levelUp(player, job, progress);
            // recompute requiredXP for the new level
            requiredXP = plugin.getJobsConfig().getXpRequired(job, progress.getLevel(), progress.getPrestige());
        }

        plugin.getBossBarManager().updateBossBar(player, job, progress, requiredXP);
    }

    /**
     * Sends an actionbar ping the moment this XP gain carries the player across a configured
     * progress-percentage threshold (e.g. 50/75/90%) toward their next level. Naturally fires
     * once per threshold per level since XP only rises monotonically within a level.
     */
    private void checkMilestonePing(Player player, Job job, int level, double prevXp, double newXp, double requiredXp) {
        if (!plugin.getConfig().getBoolean("milestones.enabled", true)) return;
        if (requiredXp <= 0 || newXp >= requiredXp) return; // level-up message covers reaching 100%

        List<Integer> percentages = plugin.getConfig().getIntegerList("milestones.percentages");
        if (percentages.isEmpty()) percentages = Arrays.asList(50, 75, 90);

        double prevPercent = Math.max(0.0, prevXp / requiredXp * 100.0);
        double newPercent = newXp / requiredXp * 100.0;

        int highestCrossed = -1;
        for (int pct : percentages) {
            if (prevPercent < pct && newPercent >= pct) {
                highestCrossed = Math.max(highestCrossed, pct);
            }
        }
        if (highestCrossed < 0) return;

        String template = plugin.getConfig().getString("milestones.message",
                "&a&l{job} &7| &fYou're &a{percent}% &7to level {next_level}!");
        String message = ChatColor.translateAlternateColorCodes('&', template
                .replace("{job}", job.getDisplayName())
                .replace("{percent}", String.valueOf(highestCrossed))
                .replace("{next_level}", String.valueOf(level + 1)));

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    /** Dispatches any console commands configured for this job/level under milestone-rewards. */
    private void dispatchMilestoneRewards(Player player, Job job, int level) {
        List<String> commands = plugin.getConfig().getStringList("milestone-rewards." + job.name() + "." + level);
        if (commands.isEmpty()) return;

        for (String cmd : commands) {
            String parsed = cmd.replace("%player%", player.getName());
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), parsed);
        }
    }

    private void levelUp(Player player, Job job, JobProgress progress) {
        int maxLevel = plugin.getJobsConfig().getMaxLevel();
        if (progress.getLevel() >= maxLevel) return;

        progress.setLevel(progress.getLevel() + 1);
        progress.setXp(0);

        // Publish swagjobs:level_up so other plugins (e.g. SwagFishing, SwagFarming) can react
        // without calling SwagJobsAPI directly.
        if (plugin.getBusService() != null) {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("uuid", player.getUniqueId().toString());
            payload.put("job", job.getName());
            payload.put("newLevel", progress.getLevel());

            plugin.getBusService().publish(new com.SwagDev.SwagAPI.events.SwagCrossPluginMessageEvent(
                    "swagjobs:level_up",
                    "SwagJobs",
                    payload,
                    player.getUniqueId()
            ));

            // Optional Discord announcement — DiscordUtils (if installed with a matching
            // webhooks.* entry configured) picks this up with zero coupling here.
            if (plugin.getConfig().getBoolean("discord.enabled", true)) {
                String webhookName = plugin.getConfig().getString("discord.webhook-name", "jobs");
                java.util.Map<String, Object> discordPayload = new java.util.HashMap<>();
                discordPayload.put("webhook", webhookName);
                discordPayload.put("description", "**" + player.getName() + "** reached level "
                        + progress.getLevel() + " in **" + job.getDisplayName() + "**"
                        + (progress.getPrestige() > 0 ? " (Prestige " + progress.getPrestige() + ")" : "") + "!");
                discordPayload.put("color", 0x57F287);
                discordPayload.put("username", "SwagJobs");

                plugin.getBusService().publish(new com.SwagDev.SwagAPI.events.SwagCrossPluginMessageEvent(
                        "discordutils:notify",
                        "SwagJobs",
                        discordPayload,
                        player.getUniqueId()
                ));
            }
        }

        double moneyReward = plugin.getJobsConfig().getMoneyReward(progress.getLevel(), progress.getPrestige());
        var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        data.addReward(new Reward(job, progress.getLevel(), progress.getPrestige(), moneyReward));

        dispatchMilestoneRewards(player, job, progress.getLevel());

        player.sendMessage(" ");
        player.sendMessage("§a§lLEVEL UP! §8» §7You are now level §f" + progress.getLevel() + " §7in §f" + job.getDisplayName() + "§7!");
        player.sendMessage("§8» §7A reward of §a₣" + moneyFormat.format(moneyReward) + " §7has been added to your collection menu.");
        player.sendMessage(" ");

        plugin.getDatabaseManager().savePlayerData(data);
    }
}