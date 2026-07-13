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

import java.util.Arrays;
import java.util.List;

public class PrestigeShopEditGUI {
    private final SwagJobsPlugin plugin;

    public PrestigeShopEditGUI(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§c§l⚙ §4§lShop Editor §c§l⚙");

        ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§c§l← Back to Shop");
        back.setItemMeta(backMeta);
        inv.setItem(0, back);

        ItemStack save = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta saveMeta = save.getItemMeta();
        saveMeta.setDisplayName("§a§lSave & Close");
        save.setItemMeta(saveMeta);
        inv.setItem(8, save);

        for (PrestigeShopItem shopItem : plugin.getPrestigeShopManager().getItems()) {
            // Show the actual stored item icon when available (ITEM type)
            ItemStack stack;
            if (shopItem.getType() == PrestigeShopItem.ShopItemType.ITEM && shopItem.getItemData() != null) {
                stack = shopItem.getItemData().clone();
                stack.setAmount(1);
            } else {
                stack = new ItemStack(shopItem.getMaterial());
            }

            ItemMeta meta = stack.getItemMeta();
            meta.setDisplayName(shopItem.getFormattedName());

            List<String> lore = new java.util.ArrayList<>();
            String rarityLine = PrestigeShopItem.getRarityLoreLine(shopItem.getRarity());
            if (rarityLine != null) lore.add(rarityLine);
            for (String line : shopItem.getLore()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            lore.add("");
            lore.add("§7Cost: §f" + shopItem.getCost() + " §5Job Points");
            lore.add("§7ID: §f" + shopItem.getId());
            if (shopItem.getType() == PrestigeShopItem.ShopItemType.COMMAND
                    && shopItem.getCommands() != null && !shopItem.getCommands().isEmpty()) {
                lore.add("§8Commands:");
                for (String cmd : shopItem.getCommands()) {
                    lore.add("§8  /" + cmd);
                }
            }
            lore.add("");
            lore.add("§c§lLeft-click: §7Remove  §e§lRight-click: §7Edit cost");
            meta.setLore(lore);
            stack.setItemMeta(meta);
            inv.setItem(shopItem.getSlot(), stack);
        }

        ItemStack empty = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta emptyMeta = empty.getItemMeta();
        emptyMeta.setDisplayName("§a§lClick to add item");
        empty.setItemMeta(emptyMeta);

        for (int i = 9; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, empty);
            }
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 1; i <= 7; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }

        player.openInventory(inv);
    }
}
