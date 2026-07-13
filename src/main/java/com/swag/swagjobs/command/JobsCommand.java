package com.swag.swagjobs.command;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.gui.JobProgressGUI;
import com.swag.swagjobs.gui.JobSelectionGUI;
import com.swag.swagjobs.gui.PrestigeShopEditGUI;
import com.swag.swagjobs.gui.PrestigeShopGUI;
import com.swag.swagjobs.manager.JobManager;
import com.swag.swagjobs.model.Job;
import com.swag.swagjobs.model.JobProgress;
import com.swag.swagjobs.model.PlayerJobData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class JobsCommand implements CommandExecutor, TabCompleter {
    private final SwagJobsPlugin plugin;

    public JobsCommand(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        if (args.length == 0) {
            new JobSelectionGUI(plugin).open(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "investigate" -> {
                if (!player.hasPermission("SwagJobs.admin")) {
                    player.sendMessage("§cYou don't have permission to investigate players.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /jobs investigate <player>");
                    return true;
                }
                handleInvestigate(player, args[1]);
                return true;
            }

            case "select", "choose" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /jobs select <job>");
                    return true;
                }
                handleJobSelect(player, args[1]);
                return true;
            }

            case "progress", "stats", "info" -> {
                if (args.length < 2) {
                    PlayerJobData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                    if (data.getActiveJob() == null) {
                        player.sendMessage("§cYou don't have an active job! Use /jobs to select one.");
                        return true;
                    }
                    new JobProgressGUI(plugin).open(player, data.getActiveJob());
                } else {
                    Job job = parseJob(args[1]);
                    if (job == null) {
                        player.sendMessage("§cInvalid job! Available: " + getJobList());
                        return true;
                    }
                    new JobProgressGUI(plugin).open(player, job);
                }
                return true;
            }

            case "prestige" -> {
                handlePrestige(player);
                return true;
            }

            case "prestigeshop", "pshop", "shop" -> {
                new PrestigeShopGUI(plugin).open(player);
                return true;
            }

            case "shopedit" -> {
                if (!player.hasPermission("SwagJobs.admin.shopedit")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                new PrestigeShopEditGUI(plugin).open(player);
                return true;
            }

            case "reload" -> {
                if (!player.hasPermission("SwagJobs.admin.reload")) {
                    player.sendMessage("§cYou don't have permission to do that!");
                    return true;
                }
                plugin.getJobsConfig().load();
                plugin.getPrestigeShopManager().load();
                player.sendMessage("§aSwagJobs config reloaded!");
                return true;
            }

            case "reset" -> {
                if (!player.hasPermission("SwagJobs.admin.reset")) {
                    player.sendMessage("§cYou don't have permission to do that!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /jobs reset <player>");
                    return true;
                }
                player.sendMessage("§cNot yet implemented!");
                return true;
            }

            case "debug" -> {
                if (!player.hasPermission("SwagJobs.admin")) {
                    player.sendMessage("§cYou don't have permission to use debug mode.");
                    return true;
                }
                JobManager jm = plugin.getJobManager();
                jm.toggleDebug(player);
                return true;
            }

            case "top" -> {
                Job job;
                if (args.length < 2) {
                    PlayerJobData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                    job = data.getActiveJob();
                    if (job == null) {
                        player.sendMessage("§cUsage: /jobs top <job> (or select an active job first)");
                        return true;
                    }
                } else {
                    job = parseJob(args[1]);
                    if (job == null) {
                        player.sendMessage("§cInvalid job! Available: " + getJobList());
                        return true;
                    }
                }
                handleTop(player, job);
                return true;
            }

            case "help" -> {
                sendHelp(player);
                return true;
            }

            default -> {
                player.sendMessage("§cUnknown subcommand! Use /jobs help");
                return true;
            }
        }
    }

    private void handleInvestigate(Player admin, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            admin.sendMessage(ChatColor.RED + "Player not found or not online.");
            return;
        }

        admin.teleport(target.getLocation());
        admin.sendMessage(ChatColor.GREEN + "Teleported to " + target.getName() + ".");

        setVanished(admin, true);

        admin.setMetadata("SwagJobs_investigating", new FixedMetadataValue(plugin, target.getUniqueId()));

        admin.sendMessage(ChatColor.YELLOW + "You are now vanished to non-admins. Use /jobs help to find how to unvanish.");
    }

    private void setVanished(Player admin, boolean vanish) {
        if (vanish) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.hasPermission("SwagJobs.admin")) {
                    p.hidePlayer(plugin, admin);
                }
            }
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showPlayer(plugin, admin);
            }
            admin.removeMetadata("SwagJobs_investigating", plugin);
        }
    }

    private void handleJobSelect(Player player, String jobName) {
        Job job = parseJob(jobName);
        if (job == null) {
            player.sendMessage("§cInvalid job! Available: " + getJobList());
            return;
        }

        PlayerJobData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        data.setActiveJob(job);
        player.sendMessage("§aYou are now working as a " + job.getDisplayName() + "§a!");
    }

    private void handlePrestige(Player player) {
        PlayerJobData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data.getActiveJob() == null) {
            player.sendMessage("§cYou don't have an active job!");
            return;
        }

        Job activeJob = data.getActiveJob();
        JobProgress progress = data.getJobProgress(activeJob);

        if (progress.getLevel() < plugin.getJobsConfig().getMaxLevel()) {
            player.sendMessage("§cYou must be level " + plugin.getJobsConfig().getMaxLevel() + " to prestige!");
            player.sendMessage("§7Current level: §f" + progress.getLevel());
            return;
        }

        if (progress.getPrestige() >= plugin.getJobsConfig().getMaxPrestige()) {
            player.sendMessage("§cYou are already at max prestige!");
            return;
        }

        plugin.getPrestigeManager().prestige(player, activeJob);
    }

    private void handleTop(Player player, Job job) {
        List<com.swag.swagjobs.database.DatabaseManager.TopEntry> entries =
                plugin.getDatabaseManager().getTopPlayers(job, 10);

        player.sendMessage("§8§m                    ");
        player.sendMessage("§a§lTop 10 " + job.getDisplayName() + "§a§ls");
        player.sendMessage("§8§m                    ");

        if (entries.isEmpty()) {
            player.sendMessage("§7No players have progress in this job yet.");
        } else {
            int rank = 1;
            for (var entry : entries) {
                String name = Bukkit.getOfflinePlayer(entry.uuid).getName();
                if (name == null) name = "Unknown";

                String rankColor = switch (rank) {
                    case 1 -> "§6";
                    case 2 -> "§7";
                    case 3 -> "§c";
                    default -> "§f";
                };

                String prestigeTag = entry.prestige > 0 ? " §d[P" + entry.prestige + "]" : "";
                player.sendMessage(rankColor + "#" + rank + " §7" + name + prestigeTag +
                        " §8- §fLevel " + entry.level + " §7(" + String.format("%.0f", entry.xp) + " xp)");
                rank++;
            }
        }
        player.sendMessage("§8§m                    ");
    }

    private Job parseJob(String name) {
        try {
            return Job.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String getJobList() {
        return Arrays.stream(Job.values())
                .map(Job::name)
                .map(String::toLowerCase)
                .collect(Collectors.joining(", "));
    }

    private void sendHelp(Player player) {
        player.sendMessage("§8§m                    ");
        player.sendMessage("§a§lSwagJobs Commands");
        player.sendMessage("§8§m                    ");
        player.sendMessage("§e/jobs §7- Open job selection GUI");
        player.sendMessage("§e/jobs select <job> §7- Select a job");
        player.sendMessage("§e/jobs progress [job] §7- View job progress");
        player.sendMessage("§e/jobs prestige §7- Prestige your active job");
        player.sendMessage("§e/jobs shop §7- Open prestige shop");
        player.sendMessage("§e/jobs top <job> §7- View the top 10 players for a job");
        player.sendMessage("§e/jobs investigate <player> §7- Teleport & vanish to investigate (admin)");
        if (player.hasPermission("SwagJobs.admin.shopedit")) {
            player.sendMessage("§c/jobs shopedit §7- Open shop editor (admin)");
        }
        player.sendMessage("§e/jobs help §7- Show this help menu");
        if (player.hasPermission("SwagJobs.admin.reload")) {
            player.sendMessage("§c/jobs reload §7- Reload config");
        }
        if (player.hasPermission("SwagJobs.admin")) {
            player.sendMessage("§c/jobs debug §7- Toggle per-player debug (shows XP/₣ calculations)");
        }
        player.sendMessage("§8§m                    ");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("select", "progress", "prestige", "shop", "shopedit", "top", "help", "reload", "investigate", "debug"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("select") || args[0].equalsIgnoreCase("progress") || args[0].equalsIgnoreCase("top")) {
                completions.addAll(Arrays.stream(Job.values())
                        .map(Job::name)
                        .map(String::toLowerCase)
                        .toList());
            } else if (args[0].equalsIgnoreCase("investigate")) {
                completions.addAll(Bukkit.getOnlinePlayers().stream().map(p -> p.getName()).toList());
            }
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}