package com.swag.swagjobs.command;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class GrantTagCommand implements CommandExecutor, TabCompleter {

    private final SwagJobsPlugin plugin;

    public GrantTagCommand(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("SwagJobs.admin.granttag")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /granttag <player> <job> <tier>");
            sender.sendMessage(ChatColor.GRAY + "Example: /granttag Steve miner expert");
            sender.sendMessage(ChatColor.GRAY + "Jobs: " + String.join(", ", plugin.getTagManager().getJobNames()));
            sender.sendMessage(ChatColor.GRAY + "Tiers: novice, expert, master");
            return true;
        }

        String playerName = args[0];
        String jobName = args[1];
        String tier = args[2];

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null || !target.hasPlayedBefore()) {
            sender.sendMessage(ChatColor.RED + "Player '" + playerName + "' not found!");
            return true;
        }

        Job job = plugin.getTagManager().parseJob(jobName);
        if (job == null) {
            sender.sendMessage(ChatColor.RED + "Invalid job! Available jobs:");
            sender.sendMessage(ChatColor.GRAY + String.join(", ", plugin.getTagManager().getJobNames()));
            return true;
        }

        String[] validTiers = plugin.getTagManager().getTierNames();
        if (!Arrays.asList(validTiers).contains(tier.toLowerCase())) {
            sender.sendMessage(ChatColor.RED + "Invalid tier! Available tiers:");
            sender.sendMessage(ChatColor.GRAY + String.join(", ", validTiers));
            return true;
        }

        boolean success = plugin.getTagManager().grantPresetTag(target.getUniqueId(), job, tier);

        if (success) {
            String jobDisplay = ChatColor.translateAlternateColorCodes('&', job.getDisplayName());
            sender.sendMessage(ChatColor.GREEN + "✓ Granted " + ChatColor.DARK_PURPLE + jobDisplay + " " +
                    tier.substring(0, 1).toUpperCase() + tier.substring(1) + ChatColor.GREEN + " tag to " +
                    ChatColor.WHITE + target.getName() + ChatColor.GREEN + "!");

            if (target.isOnline()) {
                Player onlineTarget = target.getPlayer();
                onlineTarget.sendMessage("");
                onlineTarget.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&d&l✦ TAG GRANTED! &d&l✦"));
                onlineTarget.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&7An admin granted you the &d" + jobDisplay + " " + tier + " &7tag!"));
                onlineTarget.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&7Use &e/tag &7to equip it!"));
                onlineTarget.sendMessage("");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to grant tag! Check console for errors.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("SwagJobs.admin.granttag")) {
            return completions;
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();
            return Arrays.stream(plugin.getTagManager().getJobNames())
                    .filter(job -> job.startsWith(partial))
                    .collect(Collectors.toList());
        } else if (args.length == 3) {
            String partial = args[2].toLowerCase();
            return Arrays.stream(plugin.getTagManager().getTierNames())
                    .filter(tier -> tier.startsWith(partial))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}