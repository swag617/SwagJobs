package com.swag.swagjobs.model;

import org.bukkit.Material;

/**
 * Represents all available jobs in SwagJobs
 */
public enum Job {
    MINER("Miner", "&7&lMiner", Material.DIAMOND_PICKAXE, "&7Mine blocks to earn XP and money"),
    LUMBERJACK("Lumberjack", "&2&lLumberjack", Material.DIAMOND_AXE, "&7Chop trees to earn XP and money"),
    FARMER("Farmer", "&e&lFarmer", Material.DIAMOND_HOE, "&7Harvest crops to earn XP and money"),
    FISHER("Fisher", "&b&lFisher", Material.FISHING_ROD, "&7Catch fish to earn XP and money"),
    HUNTER("Hunter", "&c&lHunter", Material.DIAMOND_SWORD, "&7Hunt mobs to earn XP and money"),
    BUILDER("Builder", "&6&lBuilder", Material.BRICKS, "&7Place blocks to earn XP and money"),
    ENCHANTER("Enchanter", "&d&lEnchanter", Material.ENCHANTING_TABLE, "&7Enchant items to earn XP and money"),
    SMELTER("Smelter", "&e&lSmelter", Material.FURNACE, "&7Smelt items to earn XP and money"),
    BREWER("Brewer", "&5&lBrewer", Material.BREWING_STAND, "&7Brew potions to earn XP and money"),
    CRAFTER("Crafter", "&a&lCrafter", Material.CRAFTING_TABLE, "&7Craft items to earn XP and money");

    private final String name;
    private final String displayName;
    private final Material icon;
    private final String description;

    Job(String name, String displayName, Material icon, String description) {
        this.name = name;
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    /**
     * Returns a stable key used for lookups (lowercase)
     */
    public String getKey() {
        return name.toLowerCase();
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get job by name or key (case-insensitive)
     */
    public static Job fromString(String text) {
        if (text == null) return null;
        for (Job job : values()) {
            if (job.name.equalsIgnoreCase(text) || job.getKey().equalsIgnoreCase(text)
                    || job.displayName.equalsIgnoreCase(text)) {
                return job;
            }
        }
        return null;
    }
}