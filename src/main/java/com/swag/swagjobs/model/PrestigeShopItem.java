package com.swag.swagjobs.model;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PrestigeShopItem {

    public enum ShopItemType { ITEM, COMMAND }

    /**
     * Apply the rarity color prefix to a base name string.
     * If rarity is null or unrecognised, translates & codes and returns as-is.
     */
    public static String applyRarityPrefix(String rarity, String baseName) {
        String translated = ChatColor.translateAlternateColorCodes('&', baseName);
        if (rarity == null || rarity.isBlank()) return translated;
        return switch (rarity.toUpperCase()) {
            case "COMMON"    -> "§b§l✦ " + translated;
            case "UNCOMMON"  -> "§d§l✦ " + translated;
            case "RARE"      -> "§6§l✦ " + translated;
            case "EPIC"      -> "§c§l✦ " + translated;
            case "LEGENDARY" -> "§e§l✦ " + translated;
            default -> translated;
        };
    }

    /**
     * Returns a coloured rarity label for the lore, or null if no rarity is set.
     * Examples: "§b§lCommon §7Reward", "§e§lLegendary §7Reward"
     */
    public static String getRarityLoreLine(String rarity) {
        if (rarity == null || rarity.isBlank()) return null;
        return switch (rarity.toUpperCase()) {
            case "COMMON"    -> "§b§lCommon §7Reward";
            case "UNCOMMON"  -> "§d§lUncommon §7Reward";
            case "RARE"      -> "§6§lRare §7Reward";
            case "EPIC"      -> "§c§lEpic §7Reward";
            case "LEGENDARY" -> "§e§lLegendary §7Reward";
            default -> null;
        };
    }

    private final String id;
    private int slot;
    private Material material;
    private String name;
    private String rarity;        // optional: COMMON / UNCOMMON / RARE / EPIC / LEGENDARY
    private List<String> lore;
    private int cost;
    private ShopItemType type;
    private List<String> commands;
    private ItemStack itemData;   // full item to deliver (ITEM type only); null = use material
    private int maxPurchasesPerPlayer;
    private boolean enabled;

    private PrestigeShopItem(String id) {
        this.id = id;
        this.commands = new ArrayList<>();
        this.lore = new ArrayList<>();
    }

    public static PrestigeShopItem fromSection(String id, ConfigurationSection section) {
        PrestigeShopItem item = new PrestigeShopItem(id);
        item.slot = section.getInt("slot", 22);

        String matName = section.getString("material", "PAPER").toUpperCase();
        try {
            item.material = Material.valueOf(matName);
        } catch (IllegalArgumentException e) {
            item.material = Material.PAPER;
        }

        item.name = section.getString("name", id);

        String rawRarity = section.getString("rarity", null);
        item.rarity = (rawRarity != null && !rawRarity.isBlank()) ? rawRarity.toUpperCase().trim() : null;

        item.lore = section.getStringList("lore");
        item.cost = section.getInt("cost", 0);

        String typeName = section.getString("type", "ITEM").toUpperCase();
        try {
            item.type = ShopItemType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            item.type = ShopItemType.ITEM;
        }

        item.commands = section.getStringList("commands");

        // Deserialise the stored ItemStack (Bukkit handles this natively)
        Object stored = section.get("item-data");
        if (stored instanceof ItemStack) {
            item.itemData = (ItemStack) stored;
        }

        item.maxPurchasesPerPlayer = section.getInt("max-purchases", -1);
        item.enabled = section.getBoolean("enabled", true);
        return item;
    }

    public void toSection(ConfigurationSection section) {
        section.set("slot", slot);
        section.set("material", material.name());
        section.set("name", name);
        section.set("rarity", rarity);   // null removes the key — fine
        section.set("lore", lore);
        section.set("cost", cost);
        section.set("type", type.name());
        section.set("commands", commands.isEmpty() ? null : commands);
        // Serialise full ItemStack when present (Bukkit writes all NBT/meta)
        section.set("item-data", itemData);
        section.set("max-purchases", maxPurchasesPerPlayer);
        section.set("enabled", enabled);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final PrestigeShopItem item;

        public Builder(String id) {
            this.item = new PrestigeShopItem(id);
            this.item.material = Material.PAPER;
            this.item.name = id;
            this.item.lore = new ArrayList<>();
            this.item.commands = new ArrayList<>();
            this.item.type = ShopItemType.ITEM;
            this.item.maxPurchasesPerPlayer = -1;
            this.item.enabled = true;
        }

        public Builder slot(int slot)            { item.slot = slot; return this; }
        public Builder material(Material m)      { item.material = m; return this; }
        public Builder name(String name)         { item.name = name; return this; }
        public Builder rarity(String rarity)     { item.rarity = rarity; return this; }
        public Builder lore(List<String> lore)   { item.lore = new ArrayList<>(lore); return this; }
        public Builder cost(int cost)            { item.cost = cost; return this; }
        public Builder type(ShopItemType t)      { item.type = t; return this; }
        public Builder commands(List<String> c)  { item.commands = new ArrayList<>(c); return this; }
        public Builder itemData(ItemStack data)  { item.itemData = data != null ? data.clone() : null; return this; }
        public Builder maxPurchases(int max)     { item.maxPurchasesPerPlayer = max; return this; }
        public Builder enabled(boolean e)        { item.enabled = e; return this; }

        public PrestigeShopItem build() { return item; }
    }

    /** Returns the display name with rarity prefix applied (& codes translated). */
    public String getFormattedName() {
        return applyRarityPrefix(rarity, name);
    }

    public String getId()   { return id; }

    public int getSlot()    { return slot; }
    public void setSlot(int slot) { this.slot = slot; }

    public Material getMaterial() { return material; }
    public void setMaterial(Material material) { this.material = material; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }

    public List<String> getLore() { return lore; }
    public void setLore(List<String> lore) { this.lore = lore; }

    public int getCost() { return cost; }
    public void setCost(int cost) { this.cost = cost; }

    public ShopItemType getType() { return type; }
    public void setType(ShopItemType type) { this.type = type; }

    public List<String> getCommands() { return commands; }
    public void setCommands(List<String> commands) { this.commands = commands; }

    public ItemStack getItemData() { return itemData; }
    public void setItemData(ItemStack data) { this.itemData = data != null ? data.clone() : null; }

    public int getMaxPurchasesPerPlayer() { return maxPurchasesPerPlayer; }
    public void setMaxPurchasesPerPlayer(int max) { this.maxPurchasesPerPlayer = max; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
