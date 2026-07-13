package com.swag.swagjobs.gui;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import com.swag.swagjobs.model.PlayerJobData;
import com.swag.swagjobs.model.JobProgress;
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

public class JobSelectionGUI {
    private final SwagJobsPlugin plugin;

    public JobSelectionGUI(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&5&l✦ &d&lYour Jobs &5&l✦"));

        PlayerJobData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        fillBorders(inv);
        fillInnerSpacing(inv);

        int[] jobSlots = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
        int jobIndex = 0;

        for (Job job : Job.values()) {
            if (jobIndex < jobSlots.length) {
                JobProgress progress = playerData.getJobProgress(job);
                inv.setItem(jobSlots[jobIndex], createJobItem(job, progress));
                jobIndex++;
            }
        }

        inv.setItem(53, createInfoItem());

        player.openInventory(inv);
    }

    private void fillBorders(Inventory inv) {
        ItemStack purplePane = createGlassPane("&5");
        ItemStack magentaPane = createGlassPane("&d");

        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, i % 2 == 0 ? purplePane : magentaPane);
            }
        }
    }

    private void fillInnerSpacing(Inventory inv) {
        ItemStack lightPurplePane = createGlassPane("&8");

        for (int i = 9; i < 18; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, lightPurplePane);
            }
        }

        for (int i = 36; i < 45; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, lightPurplePane);
            }
        }

        for (int i = 18; i < 36; i++) {
            if (i % 9 == 1 || i % 9 == 2 || i % 9 == 6 || i % 9 == 7) {
                if (inv.getItem(i) == null) {
                    inv.setItem(i, lightPurplePane);
                }
            }
        }
    }

    private ItemStack createGlassPane(String colorCode) {
        ItemStack pane = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(color(colorCode + " "));
        pane.setItemMeta(meta);
        return pane;
    }

    private ItemStack createJobItem(Job job, JobProgress progress) {
        ItemStack item = new ItemStack(job.getIcon());
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(color(job.getDisplayName()));

        List<String> lore = new ArrayList<>();
        lore.add(color(job.getDescription()));
        lore.add("");
        lore.add(color("&5Level: &f" + progress.getLevel() + " &7/ &f100"));
        lore.add(color("&5Prestige: &d" + progress.getPrestige() + " &7/ &d10"));

        double requiredXP = plugin.getJobsConfig().getXpRequired(job, progress.getLevel(), progress.getPrestige());
        lore.add(color("&5XP: &e" + String.format("%.1f", progress.getXp()) + " &7/ &e" + String.format("%.1f", requiredXP)));
        lore.add("");
        lore.add(color("&e▶ Click to view Skill Tree & Rewards"));

        meta.setLore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem() {
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(color("&d&l✦ Global Progression ✦"));

        List<String> lore = new ArrayList<>();
        lore.add(color("&7You are enrolled in &fALL &7jobs!"));
        lore.add(color("&7Perform any action to earn XP."));
        lore.add(color("&7Click a job icon to view rewards."));
        lore.add("");
        lore.add(color("&5Prestige System:"));
        lore.add(color("&7Reach Level 100 to prestige"));
        lore.add(color("&7and earn bonus multipliers!"));

        infoMeta.setLore(lore);
        infoItem.setItemMeta(infoMeta);
        return infoItem;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}