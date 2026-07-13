package com.swag.swagjobs.manager;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.PlayerJobData;
import com.swag.swagjobs.model.PrestigeShopItem;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Level;

public class PrestigeShopManager {

    public enum PurchaseResult { SUCCESS, NOT_ENOUGH_POINTS, LIMIT_REACHED, DELIVERY_FAILED }

    private static final DateTimeFormatter LOG_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SwagJobsPlugin plugin;
    private final File shopFile;
    private final LinkedHashMap<String, PrestigeShopItem> items = new LinkedHashMap<>();

    public PrestigeShopManager(SwagJobsPlugin plugin) {
        this.plugin = plugin;
        this.shopFile = new File(plugin.getDataFolder(), "prestige_shop.yml");
    }

    public void load() {
        plugin.saveResource("prestige_shop.yml", false);

        items.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(shopFile);
        ConfigurationSection itemsSection = cfg.getConfigurationSection("items");
        if (itemsSection == null) return;

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection sec = itemsSection.getConfigurationSection(key);
            if (sec == null) continue;
            PrestigeShopItem item = PrestigeShopItem.fromSection(key, sec);
            if (item.isEnabled()) {
                items.put(key, item);
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (PrestigeShopItem item : items.values()) {
            ConfigurationSection sec = cfg.createSection("items." + item.getId());
            item.toSection(sec);
        }
        try {
            cfg.save(shopFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save prestige_shop.yml", e);
        }
    }

    public Collection<PrestigeShopItem> getItems() {
        return items.values().stream()
                .sorted(Comparator.comparingInt(PrestigeShopItem::getSlot))
                .toList();
    }

    public PrestigeShopItem getItemBySlot(int slot) {
        for (PrestigeShopItem item : items.values()) {
            if (item.getSlot() == slot) return item;
        }
        return null;
    }

    public PrestigeShopItem getItemById(String id) {
        return items.get(id);
    }

    public void addItem(PrestigeShopItem item) {
        items.put(item.getId(), item);
        save();
    }

    public void removeItem(String id) {
        items.remove(id);
        save();
    }

    public PurchaseResult purchase(Player player, PrestigeShopItem item) {
        PlayerJobData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (data.getJobPoints() < item.getCost()) {
            return PurchaseResult.NOT_ENOUGH_POINTS;
        }

        if (item.getMaxPurchasesPerPlayer() != -1) {
            int count = plugin.getDatabaseManager().getShopPurchaseCount(player.getUniqueId(), item.getId());
            if (count >= item.getMaxPurchasesPerPlayer()) {
                return PurchaseResult.LIMIT_REACHED;
            }
        }

        // Attempt delivery before spending points — if it throws, the player keeps their points
        try {
            if (item.getType() == PrestigeShopItem.ShopItemType.ITEM) {
                deliverItem(player, item);
            } else {
                deliverCommands(player, item);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to deliver shop item '" + item.getId() + "' to " + player.getName(), e);
            return PurchaseResult.DELIVERY_FAILED;
        }

        data.spendJobPoints(item.getCost());
        plugin.getDatabaseManager().savePlayerData(data);

        logPurchase(player, item);
        plugin.getDatabaseManager().insertShopPurchase(
                player.getUniqueId(), player.getName(), item.getId(), item.getCost(), System.currentTimeMillis());

        return PurchaseResult.SUCCESS;
    }

    private void deliverItem(Player player, PrestigeShopItem item) {
        // Fall back to bare material stack if no full item data was stored
        ItemStack stack = (item.getItemData() != null)
                ? item.getItemData().clone()
                : new ItemStack(item.getMaterial(), 1);
        stack.setAmount(1);

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        } else {
            player.getInventory().addItem(stack);
        }
    }

    private void deliverCommands(Player player, PrestigeShopItem item) {
        List<String> cmds = item.getCommands();
        if (cmds == null || cmds.isEmpty()) return;
        for (String cmd : cmds) {
            String resolved = cmd.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
    }

    private void logPurchase(Player player, PrestigeShopItem item) {
        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists()) logsDir.mkdirs();

        String line = "[" + LocalDateTime.now().format(LOG_FMT) + "] "
                + player.getName() + " (" + player.getUniqueId() + ") bought "
                + item.getId() + " for " + item.getCost() + " points\n";

        File logFile = new File(logsDir, "shop_purchases.log");
        try (FileWriter fw = new FileWriter(logFile, true)) {
            fw.write(line);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to write shop purchase log", e);
        }
    }
}
