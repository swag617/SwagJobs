package com.swag.swagjobs.command;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import com.swag.swagjobs.model.JobProgress;
import com.swag.swagjobs.model.PlayerJobData;
import com.swag.swagjobs.model.Reward;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

public class DevCommand implements CommandExecutor {
    private final SwagJobsPlugin plugin;

    public DevCommand(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("SwagJobs.dev")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "addpoints" -> handleAddPoints(sender, args);
                case "setpoints" -> handleSetPoints(sender, args);
                case "addxp" -> handleAddXp(sender, args);
                case "setxp" -> handleSetXp(sender, args);
                case "giveprestige" -> handleGivePrestige(sender, args);
                case "setprestige" -> handleSetPrestige(sender, args);
                case "checkcurve" -> handleCheckCurve(sender, args);
                case "clearlevels" -> handleClearLevels(sender, args);
                case "setlevel" -> handleSetLevel(sender, args);
                case "addlevel" -> handleAddLevel(sender, args);
                case "investigate" -> handleInvestigate(sender, args);
                case "smeltercap" -> handleSmelterCap(sender, args);
                default -> sendUsage(sender);
            }
        } catch (Exception e) {
            sender.sendMessage("§cError: " + e.getMessage());
            e.printStackTrace();
        }
        return true;
    }

    private void handleAddPoints(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /SwagJobsdev addpoints <player> <amount>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        int amount = Integer.parseInt(args[2]);

        plugin.getPlayerDataManager().addJobPoints(target.getUniqueId(), amount);
        sender.sendMessage("§aAdded " + amount + " job points to " + args[1]);
    }

    private void handleSetPoints(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /SwagJobsdev setpoints <player> <amount>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        int amount = Integer.parseInt(args[2]);

        PlayerJobData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        data.setJobPoints(amount);
        plugin.getDatabaseManager().savePlayerData(data);
        sender.sendMessage("§aSet " + args[1] + " job points to " + amount);
    }

    private void handleAddXp(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /SwagJobsdev addxp <player> <job> <amount>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        Job job = Job.fromString(args[2]);
        if (job == null) {
            sender.sendMessage("§cUnknown job: " + args[2]);
            return;
        }
        double amount = Double.parseDouble(args[3]);

        PlayerJobData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        JobProgress progress = data.getJobProgress(job);
        progress.setXp(progress.getXp() + amount);

        plugin.getDatabaseManager().savePlayerData(data);

        String jobDisplayName = ChatColor.translateAlternateColorCodes('&', job.getDisplayName());
        sender.sendMessage("§aAdded " + amount + " XP to " + args[1] + " for job " + jobDisplayName);
    }

    private void handleSetXp(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /SwagJobsdev setxp <player> <job> <amount>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        Job job = Job.fromString(args[2]);
        if (job == null) {
            sender.sendMessage("§cUnknown job: " + args[2]);
            return;
        }
        double amount = Double.parseDouble(args[3]);

        PlayerJobData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        JobProgress progress = data.getJobProgress(job);
        progress.setXp(amount);

        plugin.getDatabaseManager().savePlayerData(data);

        String jobDisplayName = ChatColor.translateAlternateColorCodes('&', job.getDisplayName());
        sender.sendMessage("§aSet XP to " + amount + " for " + args[1] + " in job " + jobDisplayName);
    }

    private void handleGivePrestige(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /SwagJobsdev giveprestige <player> <job>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        Job job = Job.fromString(args[2]);
        if (job == null) {
            sender.sendMessage("§cUnknown job: " + args[2]);
            return;
        }

        PlayerJobData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        JobProgress progress = data.getJobProgress(job);

        int maxPrestige = plugin.getJobsConfig().getMaxPrestige();
        int newPrestige = Math.min(progress.getPrestige() + 1, maxPrestige);
        progress.setPrestige(newPrestige);

        data.addJobPoints(100);

        plugin.getDatabaseManager().savePlayerData(data);

        String jobDisplayName = ChatColor.translateAlternateColorCodes('&', job.getDisplayName());
        sender.sendMessage("§aGave prestige to " + args[1] + " in " + jobDisplayName + " (now Prestige " + progress.getPrestige() + ")");
    }

    private void handleSetPrestige(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /SwagJobsdev setprestige <player> <job> <prestige>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        Job job = Job.fromString(args[2]);
        if (job == null) {
            sender.sendMessage("§cUnknown job: " + args[2]);
            return;
        }
        int prestige = Integer.parseInt(args[3]);

        int maxPrestige = plugin.getJobsConfig().getMaxPrestige();
        if (prestige < 0 || prestige > maxPrestige) {
            sender.sendMessage("§cPrestige must be between 0 and " + maxPrestige);
            return;
        }

        PlayerJobData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        JobProgress progress = data.getJobProgress(job);
        progress.setPrestige(prestige);

        plugin.getDatabaseManager().savePlayerData(data);

        String jobDisplayName = ChatColor.translateAlternateColorCodes('&', job.getDisplayName());
        sender.sendMessage("§aSet " + args[1] + "'s " + jobDisplayName + " prestige to " + prestige);
    }

    private void handleCheckCurve(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /SwagJobsdev checkcurve <player> <job> [level] [prestige]");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID uuid = target.getUniqueId();

        Job job = Job.fromString(args[2]);
        if (job == null) {
            sender.sendMessage("§cUnknown job: " + args[2]);
            return;
        }

        PlayerJobData data = plugin.getPlayerDataManager().getPlayerData(uuid);
        JobProgress progress = data.getJobProgress(job);

        int level = progress.getLevel();
        int prestige = progress.getPrestige();

        if (args.length >= 4) level = Integer.parseInt(args[3]);
        if (args.length >= 5) prestige = Integer.parseInt(args[4]);

        int maxLevel = plugin.getJobsConfig().getMaxLevel();
        int maxPrestige = plugin.getJobsConfig().getMaxPrestige();

        if (level < 1 || level > maxLevel) {
            sender.sendMessage("§cLevel must be between 1 and " + maxLevel);
            return;
        }
        if (prestige < 0 || prestige > maxPrestige) {
            sender.sendMessage("§cPrestige must be between 0 and " + maxPrestige);
            return;
        }

        String jobDisplayName = ChatColor.translateAlternateColorCodes('&', job.getDisplayName());

        double requiredXP = plugin.getJobsConfig().getXpRequired(job, level, prestige);
        double moneyReward = plugin.getJobsConfig().getMoneyReward(level, prestige);

        double rankXpMult = 1.0;
        if (target.isOnline() && target.getPlayer() != null) {
            rankXpMult = plugin.getJobsConfig().getRankXPMultiplier(target.getPlayer());
        }

        double prestigeXpGainMult = 1.0 + (prestige * plugin.getJobsConfig().getPrestigeXPMultiplier());
        double totalXpGainMult = rankXpMult * prestigeXpGainMult;
        double prestigeMoneyMult = 1.0 + (prestige * plugin.getJobsConfig().getPrestigeMoneyMultiplier());

        sender.sendMessage("§8§m====");
        sender.sendMessage("§6§lCurve Check");
        sender.sendMessage("§7Player: §f" + (target.getName() != null ? target.getName() : uuid.toString()));
        sender.sendMessage("§7Job: §f" + jobDisplayName);
        sender.sendMessage("§7Level: §f" + level + " §7/ §f" + maxLevel + "   §7Prestige: §d" + prestige + " §7/ §d" + maxPrestige);
        sender.sendMessage("§8§m----");
        sender.sendMessage("§eXP Curve:");
        sender.sendMessage("§7  Base XP Required: §f" + String.format("%,.0f", plugin.getJobsConfig().getBaseXPForJob(job) * level));
        sender.sendMessage("§7  Prestige Scaling: §f" + String.format("%.0f%%", (1.0 + prestige * plugin.getJobsConfig().getPrestigeXPReqMultiplier()) * 100));
        sender.sendMessage("§7  Final XP Required: §f" + String.format("%,.0f", requiredXP));
        sender.sendMessage("§8§m----");
        sender.sendMessage("§eMoney Curve:");
        sender.sendMessage("§7  Base Money: §a$" + String.format("%,.2f", plugin.getJobsConfig().getBaseMoneyPerLevel() * level));
        sender.sendMessage("§7  Prestige Bonus: §a+" + String.format("%.0f%%", prestige * plugin.getJobsConfig().getPrestigeMoneyMultiplier() * 100));
        sender.sendMessage("§7  Final Money Reward: §a$" + String.format("%,.2f", moneyReward));
        sender.sendMessage("§8§m----");
        sender.sendMessage("§eMultipliers:");
        sender.sendMessage("§7  Rank XP Mult: §f" + String.format("%.2f", rankXpMult) + "x" + (target.isOnline() ? "" : " §8(offline → 1.00x)"));
        sender.sendMessage("§7  Prestige XP Gain Mult: §f" + String.format("%.2f", prestigeXpGainMult) + "x");
        sender.sendMessage("§7  Total XP Gain Mult: §f" + String.format("%.2f", totalXpGainMult) + "x");
        sender.sendMessage("§7  Prestige Money Mult: §a" + String.format("%.2f", prestigeMoneyMult) + "x");
        sender.sendMessage("§8§m----");
        sender.sendMessage("§eEstimated Actions Needed:");
        sender.sendMessage("§7  Assuming 10 base XP/action:");
        sender.sendMessage("§7  With multipliers: §f" + String.format("%,.0f", requiredXP / (10.0 * totalXpGainMult)) + " actions");
        sender.sendMessage("§8§m====");
    }

    private void handleClearLevels(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /SwagJobsdev clearlevels <player|all> [--reset-prestige]");
            return;
        }

        boolean resetPrestige = args.length >= 3 &&
                (args[2].equalsIgnoreCase("--reset-prestige") || args[2].equalsIgnoreCase("resetprestige"));

        if (args[1].equalsIgnoreCase("all")) {
            int count = 0;
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.hasPlayedBefore()) {
                    clearPlayerLevels(op.getUniqueId(), resetPrestige);
                    count++;
                }
            }
            sender.sendMessage("§aCleared levels for " + count + " players" + (resetPrestige ? " (including prestige)" : ""));
        } else {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            clearPlayerLevels(target.getUniqueId(), resetPrestige);
            sender.sendMessage("§aCleared levels for " + args[1] + (resetPrestige ? " (including prestige)" : ""));
        }
    }

    private void clearPlayerLevels(UUID uuid, boolean resetPrestige) {
        PlayerJobData data = plugin.getPlayerDataManager().getPlayerData(uuid);
        for (Job job : Job.values()) {
            JobProgress progress = data.getJobProgress(job);
            progress.setLevel(1);
            progress.setXp(0);
            if (resetPrestige) {
                progress.setPrestige(0);
            }
        }
        plugin.getDatabaseManager().savePlayerData(data);
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /SwagJobsdev setlevel <player> <job> <level>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        Job job = Job.fromString(args[2]);
        if (job == null) {
            sender.sendMessage("§cUnknown job: " + args[2]);
            return;
        }
        int level = Integer.parseInt(args[3]);

        int maxLevel = plugin.getJobsConfig().getMaxLevel();
        if (level < 1 || level > maxLevel) {
            sender.sendMessage("§cLevel must be between 1 and " + maxLevel);
            return;
        }

        PlayerJobData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        JobProgress progress = data.getJobProgress(job);
        int oldLevel = progress.getLevel();
        progress.setLevel(level);
        progress.setXp(0);

        for (int i = 1; i <= level; i++) {
            final int rewardLevel = i;
            final int currentPrestige = progress.getPrestige();

            boolean exists = data.getRewards().stream()
                    .anyMatch(r -> r.getJob() == job &&
                            r.getLevel() == rewardLevel &&
                            r.getPrestige() == currentPrestige);

            if (!exists) {
                double amount = plugin.getJobsConfig().getMoneyReward(rewardLevel, currentPrestige);
                data.addReward(new Reward(job, rewardLevel, currentPrestige, amount, false));
            }
        }

        if (level < oldLevel) {
            data.getUnclaimedRewards().removeIf(r ->
                    r.getJob() == job &&
                            r.getPrestige() == progress.getPrestige() &&
                            r.getLevel() > level
            );
        }

        plugin.getDatabaseManager().savePlayerData(data);

        String jobDisplayName = ChatColor.translateAlternateColorCodes('&', job.getDisplayName());
        sender.sendMessage("§aSet " + args[1] + "'s " + jobDisplayName + " level to " + level);

        Player onlinePlayer = target.getPlayer();
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            String inventoryTitle = onlinePlayer.getOpenInventory().getTitle();
            if (inventoryTitle.contains(job.getName() + " Progress")) {
                int currentPage = 1;
                if (inventoryTitle.contains("Page ")) {
                    try {
                        String pageStr = inventoryTitle.split("Page ")[1].split("/")[0].trim();
                        currentPage = Integer.parseInt(pageStr);
                    } catch (Exception ignored) {}
                }
                final Job finalJob = job;
                final int finalPage = currentPage;
                final Player finalPlayer = onlinePlayer;
                Bukkit.getScheduler().runTask(plugin, () ->
                        new com.swag.swagjobs.gui.JobProgressGUI(plugin).openPage(finalPlayer, finalJob, finalPage)
                );
            }
        }
    }

    private void handleAddLevel(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /SwagJobsdev addlevel <player> <job> <amount>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        Job job = Job.fromString(args[2]);
        if (job == null) {
            sender.sendMessage("§cUnknown job: " + args[2]);
            return;
        }
        int amount = Integer.parseInt(args[3]);

        PlayerJobData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        JobProgress progress = data.getJobProgress(job);

        int maxLevel = plugin.getJobsConfig().getMaxLevel();
        int newLevel = Math.min(progress.getLevel() + amount, maxLevel);
        progress.setLevel(newLevel);
        progress.setXp(0);

        plugin.getDatabaseManager().savePlayerData(data);

        String jobDisplayName = ChatColor.translateAlternateColorCodes('&', job.getDisplayName());
        sender.sendMessage("§aAdded " + amount + " levels to " + args[1] + "'s " + jobDisplayName + " (now level " + newLevel + ")");

        Player onlinePlayer = target.getPlayer();
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            String inventoryTitle = onlinePlayer.getOpenInventory().getTitle();
            if (inventoryTitle.contains(job.getName() + " Progress")) {
                int currentPage = 1;
                if (inventoryTitle.contains("Page ")) {
                    try {
                        String pageStr = inventoryTitle.split("Page ")[1].split("/")[0].trim();
                        currentPage = Integer.parseInt(pageStr);
                    } catch (Exception ignored) {}
                }
                final Job finalJob = job;
                final int finalPage = currentPage;
                final Player finalPlayer = onlinePlayer;
                Bukkit.getScheduler().runTask(plugin, () ->
                        new com.swag.swagjobs.gui.JobProgressGUI(plugin).openPage(finalPlayer, finalJob, finalPage)
                );
            }
        }
    }

    private void handleInvestigate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /SwagJobsdev investigate <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }

        player.teleport(target.getLocation());
        sender.sendMessage("§aTeleported to " + target.getName() + " for investigation.");

        if (plugin.getVanishManager() != null) {
            plugin.getVanishManager().setVanished(player, true);
            sender.sendMessage("§7(Vanish enabled)");
        }
    }

    private void handleSmelterCap(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /SwagJobsdev smeltercap <set|get|reset> ...");
            return;
        }

        Object manager = null;
        try {
            manager = plugin.getSmelterCapManager();
        } catch (NoSuchMethodError | Exception e) {
            // ignored
        }

        if (manager == null) {
            sender.sendMessage("§cSmelterCapManager not available. Make sure the plugin provides getSmelterCapManager().");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /SwagJobsdev smeltercap set <rank> <amount>");
                    return;
                }
                String rank = args[2];
                int amount;
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cAmount must be a number.");
                    return;
                }

                try {
                    manager.getClass().getMethod("setCapForRank", String.class, int.class).invoke(manager, rank, amount);
                    try {
                        plugin.getJobsConfig().saveSmelterCaps();
                    } catch (Exception ignored) {}

                    sender.sendMessage("§aSet smelter cap for rank '" + rank + "' to " + amount);
                } catch (NoSuchMethodException nsme) {
                    sender.sendMessage("§cSmelterCapManager.setCapForRank(String,int) not found.");
                } catch (Exception e) {
                    sender.sendMessage("§cFailed to set cap: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            case "get" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /SwagJobsdev smeltercap get <rank>");
                    return;
                }
                String rank = args[2];
                try {
                    Object capObj = manager.getClass().getMethod("getCapForRank", String.class).invoke(manager, rank);
                    int cap = capObj instanceof Integer ? (Integer) capObj : Integer.parseInt(capObj.toString());
                    sender.sendMessage("§aSmelter cap for rank '" + rank + "' is " + cap);
                } catch (NoSuchMethodException nsme) {
                    sender.sendMessage("§cSmelterCapManager.getCapForRank(String) not found.");
                } catch (Exception e) {
                    sender.sendMessage("§cFailed to get cap: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            case "reset" -> {
                try {
                    manager.getClass().getMethod("resetToDefaults").invoke(manager);
                    try {
                        plugin.getJobsConfig().saveSmelterCaps();
                    } catch (Exception ignored) {}
                    sender.sendMessage("§aSmelter caps reset to defaults.");
                } catch (NoSuchMethodException nsme) {
                    sender.sendMessage("§cSmelterCapManager.resetToDefaults() not found.");
                } catch (Exception e) {
                    sender.sendMessage("§cFailed to reset caps: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            default -> sender.sendMessage("§cUnknown action. Usage: /SwagJobsdev smeltercap <set|get|reset> ...");
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6SwagJobsDev Commands:");
        sender.sendMessage(" §7/SwagJobsdev addpoints <player> <amount>");
        sender.sendMessage(" §7/SwagJobsdev setpoints <player> <amount>");
        sender.sendMessage(" §7/SwagJobsdev addxp <player> <job> <amount>");
        sender.sendMessage(" §7/SwagJobsdev setxp <player> <job> <amount>");
        sender.sendMessage(" §7/SwagJobsdev giveprestige <player> <job>");
        sender.sendMessage(" §7/SwagJobsdev setprestige <player> <job> <prestige>");
        sender.sendMessage(" §7/SwagJobsdev checkcurve <player> <job> [level] [prestige]");
        sender.sendMessage(" §7/SwagJobsdev clearlevels <player|all> [--reset-prestige]");
        sender.sendMessage(" §7/SwagJobsdev setlevel <player> <job> <level>");
        sender.sendMessage(" §7/SwagJobsdev addlevel <player> <job> <amount>");
        sender.sendMessage(" §7/SwagJobsdev investigate <player>");
        sender.sendMessage(" §7/SwagJobsdev smeltercap set <rank> <amount>");
        sender.sendMessage(" §7/SwagJobsdev smeltercap get <rank>");
        sender.sendMessage(" §7/SwagJobsdev smeltercap reset");
    }
}