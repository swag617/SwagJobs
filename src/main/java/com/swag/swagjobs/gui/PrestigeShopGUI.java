package com.swag.swagjobs.gui;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.PrestigeShopItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PrestigeShopGUI {
    private final SwagJobsPlugin plugin;

    public PrestigeShopGUI(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§d§l✦ §5§lPrestige Shop §d§l✦");

        int totalJobPoints = plugin.getPlayerDataManager().getTotalJobPoints(player.getUniqueId());

        ItemStack pointsDisplay = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta pointsMeta = pointsDisplay.getItemMeta();
        pointsMeta.setDisplayName("§d§l✦ §5§lYour Job Points §d§l✦");
        pointsMeta.setLore(Arrays.asList(
                "§7You have earned:",
                "§f" + totalJobPoints + " §5Job Points",
                "",
                "§7Spend them wisely!"
        ));
        pointsDisplay.setItemMeta(pointsMeta);
        inv.setItem(4, pointsDisplay);

        for (PrestigeShopItem shopItem : plugin.getPrestigeShopManager().getItems()) {
            inv.setItem(shopItem.getSlot(), buildItemStack(player, shopItem));
        }

        ItemStack border = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName("§d§l✦");
        border.setItemMeta(borderMeta);

        ItemStack border2 = new ItemStack(Material.MAGENTA_STAINED_GLASS_PANE);
        ItemMeta border2Meta = border2.getItemMeta();
        border2Meta.setDisplayName("§5§l✦");
        border2.setItemMeta(border2Meta);

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, i % 2 == 0 ? border : border2);
            inv.setItem(45 + i, i % 2 == 0 ? border2 : border);
        }
        for (int i = 9; i < 45; i += 9) {
            inv.setItem(i, border);
            inv.setItem(i + 8, border);
        }

        ItemStack filler = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);

        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }

        player.openInventory(inv);
    }

    private ItemStack buildItemStack(Player player, PrestigeShopItem shopItem) {
        // For ITEM type with stored item data, use the actual item as the display icon
        ItemStack stack;
        if (shopItem.getType() == PrestigeShopItem.ShopItemType.ITEM && shopItem.getItemData() != null) {
            stack = shopItem.getItemData().clone();
            stack.setAmount(1);
        } else {
            stack = new ItemStack(shopItem.getMaterial());
        }

        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(shopItem.getFormattedName());

        List<String> lore = new ArrayList<>();

        String rarityLine = PrestigeShopItem.getRarityLoreLine(shopItem.getRarity());
        if (rarityLine != null) {
            lore.add(rarityLine);
        }

        for (String line : shopItem.getLore()) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        lore.add("");
        lore.add("§7Cost: §f" + shopItem.getCost() + " §5Job Points");

        if (shopItem.getMaxPurchasesPerPlayer() != -1) {
            int bought = plugin.getDatabaseManager()
                    .getShopPurchaseCount(player.getUniqueId(), shopItem.getId());
            lore.add("§7Purchased: §f" + bought + "§7/§f" + shopItem.getMaxPurchasesPerPlayer());
        }

        if (shopItem.getType() == PrestigeShopItem.ShopItemType.COMMAND
                && shopItem.getCommands() != null && !shopItem.getCommands().isEmpty()) {
            lore.add("");
            lore.add("§8Commands run:");
            for (String cmd : shopItem.getCommands()) {
                lore.add("§8  /" + cmd);
            }
        }

        lore.add("");
        lore.add("§a§l✔ Click to purchase!");

        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
