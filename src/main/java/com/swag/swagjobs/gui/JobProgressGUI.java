package com.swag.swagjobs.gui;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import com.swag.swagjobs.model.JobProgress;
import com.swag.swagjobs.model.PlayerJobData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class JobProgressGUI {
    private final SwagJobsPlugin plugin;

    public JobProgressGUI(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Job job) {
        openPage(player, job, 1);
    }

    public void openPage(Player player, Job job, int page) {
        String title = color("&5&l✦ &d&l" + job.getName() + " Progress &5&l✦ &7(Page " + page + "/4)");
        Inventory inv = Bukkit.createInventory(null, 54, title);

        PlayerJobData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        JobProgress progress = playerData.getJobProgress(job);

        fillBorders(inv);

        inv.setItem(1, createJobInfoItem(job, progress));
        inv.setItem(4, createXPProgressItem(job, progress));
        inv.setItem(7, createRewardsItem(playerData, job, progress));

        fillLevelProgression(inv, job, progress, page, playerData);

        inv.setItem(46, createMultiplierItem(progress));
        inv.setItem(49, createPrestigeItem(progress));

        if (page > 1) inv.setItem(47, createPreviousPageButton());
        if (page < 4) inv.setItem(51, createNextPageButton());
        inv.setItem(53, createBackButton());

        player.openInventory(inv);
    }

    private void fillBorders(Inventory inv) {
        ItemStack purplePane = createColoredPane(Material.PURPLE_STAINED_GLASS_PANE, "&5");
        ItemStack magentaPane = createColoredPane(Material.MAGENTA_STAINED_GLASS_PANE, "&d");
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, i % 2 == 0 ? purplePane : magentaPane);
            }
        }
    }

    private void fillLevelProgression(Inventory inv, Job job, JobProgress progress, int page, PlayerJobData playerData) {
        int currentLevel = progress.getLevel();
        int[] allSlots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        int startLevel = (page - 1) * 28 + 1;
        for (int i = 0; i < allSlots.length; i++) {
            int level = startLevel + i;
            if (level > 100) break;
            inv.setItem(allSlots[i], createLevelPane(job, level, currentLevel >= level, progress, playerData));
        }
    }

    private ItemStack createLevelPane(Job job, int level, boolean unlocked, JobProgress progress, PlayerJobData playerData) {
        // Check if reward is claimed for THIS prestige
        boolean claimed = playerData.getRewards().stream()
                .anyMatch(r -> r.getJob() == job && r.getLevel() == level && r.getPrestige() == progress.getPrestige() && r.isClaimed());

        Material material;
        String statusColor;
        String statusText;

        if (claimed) {
            material = Material.LIME_STAINED_GLASS_PANE;
            statusColor = "&a";
            statusText = "&a&l✓ Claimed";
        } else if (unlocked) {
            material = Material.YELLOW_STAINED_GLASS_PANE;
            statusColor = "&e";
            statusText = "&e&l⚠ Click to claim!";
        } else {
            material = Material.RED_STAINED_GLASS_PANE;
            statusColor = "&c";
            statusText = "&c&l✗ Locked";
        }

        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(color(statusColor + "Level " + level));
        List<String> lore = new ArrayList<>();

        double totalReward = plugin.getJobsConfig().getMoneyReward(level, progress.getPrestige());
        lore.add(color("&7Reward: &e₣" + String.format("%,d", (long)totalReward)));
        lore.add("");
        lore.add(color(statusText));

        meta.setLore(lore);
        pane.setItemMeta(meta);
        return pane;
    }

    private ItemStack createJobInfoItem(Job job, JobProgress progress) {
        ItemStack item = new ItemStack(job.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&d&l" + job.getDisplayName()));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Current Level: &f" + progress.getLevel()));
        lore.add(color("&7Prestige: &d" + progress.getPrestige()));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createXPProgressItem(Job job, JobProgress progress) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        double req = plugin.getJobsConfig().getXpRequired(job, progress.getLevel(), progress.getPrestige());
        meta.setDisplayName(color("&b&lXP Progress"));
        List<String> lore = new ArrayList<>();
        lore.add(color("&f" + String.format("%.1f", progress.getXp()) + " &7/ &b" + String.format("%.1f", req)));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRewardsItem(PlayerJobData data, Job job, JobProgress progress) {
        double unclaimed = data.getTotalUnclaimedMoney(job);
        ItemStack item = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&e&lUnclaimed Rewards"));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Total: &a₣" + String.format("%,.2f", unclaimed)));
        lore.add("");
        lore.add(color("&eClick to claim all!"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMultiplierItem(JobProgress progress) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&6&lActive Multipliers"));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Prestige XP: &a+" + (progress.getPrestige() * 2) + "%"));
        lore.add(color("&7Prestige Money: &a+" + (progress.getPrestige() * 8) + "%"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPrestigeItem(JobProgress progress) {
        ItemStack item = new ItemStack(Material.DRAGON_BREATH);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&5&lPrestige"));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Current: &d" + progress.getPrestige()));
        lore.add("");
        lore.add(color("&eClick to prestige!"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createColoredPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNextPageButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&aNext Page →"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPreviousPageButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&c← Previous Page"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&cBack to Jobs"));
        item.setItemMeta(meta);
        return item;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}