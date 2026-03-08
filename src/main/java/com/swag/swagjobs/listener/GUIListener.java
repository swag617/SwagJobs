package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.gui.JobProgressGUI;
import com.swag.swagjobs.gui.JobSelectionGUI;
import com.swag.swagjobs.model.Job;
import com.swag.swagjobs.model.JobProgress;
import com.swag.swagjobs.model.PlayerJobData;
import com.swag.swagjobs.model.Reward;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class GUIListener implements Listener {
    private final SwagJobsPlugin plugin;

    public GUIListener(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String rawTitle = event.getView().getTitle();
        String title = ChatColor.stripColor(rawTitle == null ? "" : rawTitle).trim();

        boolean isJobSelection = title.contains("Your Jobs");
        boolean isProgress = title.contains("Progress");

        if (!isJobSelection && !isProgress) return;

        event.setCancelled(true);

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) return;

        Inventory clickedInventory = event.getClickedInventory();
        Inventory topInventory = event.getView().getTopInventory();
        boolean clickedTop = clickedInventory != null && clickedInventory.equals(topInventory);

        if (!clickedTop) return;

        // --- JOB SELECTION ---
        if (isJobSelection) {
            String clickedName = ChatColor.stripColor(current.getItemMeta().getDisplayName()).trim();
            for (Job job : Job.values()) {
                String jobDisplay = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', job.getDisplayName())).trim();
                if (clickedName.equalsIgnoreCase(jobDisplay)) {
                    player.closeInventory();
                    Bukkit.getScheduler().runTask(plugin, () -> new JobProgressGUI(plugin).open(player, job));
                    return;
                }
            }
        }

        // --- PROGRESS GUI ---
        if (isProgress) {
            int slot = event.getSlot();
            Job job = extractJobFromTitle(title);
            if (job == null) return;

            PlayerJobData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            JobProgress progress = data.getJobProgress(job);

            // Bulk Claim (Slot 7) - FIXED: Refresh GUI with fresh data!
            if (slot == 7) {
                plugin.getPlayerDataManager().claimRewards(player, job);

                // CRITICAL FIX: Run GUI refresh on next tick to ensure data is saved first
                // and get FRESH playerData from cache
                int currentPage = extractPageFromTitle(title);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    new JobProgressGUI(plugin).openPage(player, job, currentPage);
                });

                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                return;
            }

            // Prestige (Slot 49)
            if (slot == 49) {
                plugin.getPrestigeManager().prestige(player, job);
                player.closeInventory();
                return;
            }

            // Back Button (Slot 53)
            if (slot == 53) {
                new JobSelectionGUI(plugin).open(player);
                return;
            }

            // Pagination
            if (slot == 47) {
                int page = extractPageFromTitle(title);
                if (page > 1) new JobProgressGUI(plugin).openPage(player, job, page - 1);
                return;
            }
            if (slot == 51) {
                int page = extractPageFromTitle(title);
                if (page < 4) new JobProgressGUI(plugin).openPage(player, job, page + 1);
                return;
            }

            // Individual Level Claim (Slots 10-43)
            if (current.getType() == Material.YELLOW_STAINED_GLASS_PANE) {
                String name = ChatColor.stripColor(current.getItemMeta().getDisplayName());
                try {
                    int level = Integer.parseInt(name.replaceAll("[^0-9]", ""));
                    PlayerJobData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

                    synchronized (playerData) {
                        int prestige = progress.getPrestige();

                        boolean alreadyClaimed = playerData.getRewards().stream()
                                .anyMatch(r -> r.getJob() == job && r.getLevel() == level && r.getPrestige() == prestige && r.isClaimed());

                        if (alreadyClaimed) {
                            player.sendMessage("§c§lThis reward is already claimed!");
                            return;
                        }

                        // Attempt to claim in DB
                        boolean dbSuccess = plugin.getDatabaseManager().claimReward(player.getUniqueId(), job.getName(), level, prestige);

                        if (dbSuccess) {
                            double amount = plugin.getJobsConfig().getMoneyReward(level, prestige);

                            // Update memory state atomically
                            playerData.markRewardClaimed(job, level, prestige, amount);

                            if (plugin.getEconomy() != null) {
                                plugin.getEconomy().depositPlayer(player, amount);
                            }

                            plugin.getDatabaseManager().savePlayerData(playerData);

                            player.sendMessage("§a§lCLAIMED! §7Level " + level + " reward: §a₣" + String.format("%.2f", amount));
                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                        } else {
                            player.sendMessage("§cDatabase error! Could not claim reward.");
                        }
                    }

                    // FIXED: Refresh GUI on next tick with fresh data
                    int currentPage = extractPageFromTitle(title);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        new JobProgressGUI(plugin).openPage(player, job, currentPage);
                    });
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Job extractJobFromTitle(String title) {
        for (Job job : Job.values()) {
            if (title.contains(job.getName())) return job;
        }
        return null;
    }

    private int extractPageFromTitle(String title) {
        try {
            // Title format: "... Progress (Page X/4)"
            String part = title.substring(title.lastIndexOf("Page ") + 5, title.lastIndexOf("/"));
            return Integer.parseInt(part);
        } catch (Exception e) {
            return 1;
        }
    }
}