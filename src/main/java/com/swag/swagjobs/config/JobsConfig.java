package com.swag.swagjobs.config;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class JobsConfig {
    private final SwagJobsPlugin plugin;

    private int maxLevel;
    private int maxPrestige;
    private double baseXPPerLevel;
    private double prestigeXPRequirementPerPrestige;
    private double baseMoneyPerLevel;
    private double moneyPerXP;
    private double prestigeXPGainPerPrestige;
    private double prestigeMoneyPerPrestige;

    private final Map<Job, Double> jobBaseXP = new HashMap<>();
    // New map for smelter caps
    private final Map<String, Integer> smelterCaps = new HashMap<>();

    public JobsConfig(SwagJobsPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.maxLevel = config.getInt("xp.max-level", 100);
        this.maxPrestige = config.getInt("prestige.max-prestige", 10);
        this.baseXPPerLevel = config.getDouble("xp.base-xp-per-level", 250.0);
        this.prestigeXPRequirementPerPrestige = config.getDouble("prestige.xp-requirement-per-prestige", 0.05);
        this.baseMoneyPerLevel = config.getDouble("money.base-money-per-level", 10.0);
        this.moneyPerXP = config.getDouble("money.money-per-xp", 0.05);
        this.prestigeXPGainPerPrestige = config.getDouble("prestige.xp-gain-per-prestige", 0.02);
        this.prestigeMoneyPerPrestige = config.getDouble("prestige.money-per-prestige", 0.08);

        for (Job job : Job.values()) {
            double baseXP = config.getDouble("jobs." + job.name().toLowerCase() + ".base-xp", 250.0);
            jobBaseXP.put(job, baseXP);
        }

        // Load Smelter Caps
        loadSmelterCaps();
    }

    private void loadSmelterCaps() {
        smelterCaps.clear();
        FileConfiguration config = plugin.getConfig();
        // We'll use 'rank-caps' to distinguish from 'rank-multipliers'
        if (!config.contains("rank-caps")) {
            config.set("rank-caps.member", 10);
            config.set("rank-caps.axolotl", 15);
            config.set("rank-caps.lizard", 20);
            config.set("rank-caps.flea", 25);
            plugin.saveConfig();
        }

        var section = config.getConfigurationSection("rank-caps");
        if (section != null) {
            for (String rank : section.getKeys(false)) {
                smelterCaps.put(rank.toLowerCase(), config.getInt("rank-caps." + rank));
            }
        }
    }

    public void saveSmelterCaps() {
        for (Map.Entry<String, Integer> entry : smelterCaps.entrySet()) {
            plugin.getConfig().set("rank-caps." + entry.getKey(), entry.getValue());
        }
        plugin.saveConfig();
    }

    public int getSmelterCap(String rank) {
        return smelterCaps.getOrDefault(rank.toLowerCase(), 10);
    }

    public void setSmelterCap(String rank, int amount) {
        smelterCaps.put(rank.toLowerCase(), Math.max(1, amount));
        saveSmelterCaps();
    }

    public int getPlayerSmelterCap(Player player) {
        if (player.hasPermission("SwagJobs.rank.flea")) return getSmelterCap("flea");
        if (player.hasPermission("SwagJobs.rank.lizard")) return getSmelterCap("lizard");
        if (player.hasPermission("SwagJobs.rank.axolotl")) return getSmelterCap("axolotl");
        return getSmelterCap("member");
    }

    // --- Existing Methods ---

    /**
     * Get XP for a specific job action
     * FIXED: Now checks vanilla-fishing path for Fisher job
     */
    public double getActionXP(Job job, String action) {
        if (action == null) return 0.0;
        String key = action.toLowerCase().replace(" ", "_").replace("-", "_");

        // 1. Check explicit config first: jobs.{job}.actions.{key}.xp
        String path = "jobs." + job.getKey().toLowerCase() + ".actions." + key + ".xp";
        if (plugin.getConfig().contains(path)) {
            return plugin.getConfig().getDouble(path);
        }

        // 2. Fisher-specific config paths
        if (job == Job.FISHER) {
            // Check PyroFishing custom fish: jobs.fisher.pyrofishing.custom-fish.{key}.xp
            String pyroPath = "jobs.fisher.pyrofishing.custom-fish." + key + ".xp";
            if (plugin.getConfig().contains(pyroPath)) {
                return plugin.getConfig().getDouble(pyroPath);
            }

            // Check vanilla fishing: jobs.fisher.vanilla-fishing.fish.{key}.xp
            String vanillaPath = "jobs.fisher.vanilla-fishing.fish." + key + ".xp";
            if (plugin.getConfig().contains(vanillaPath)) {
                return plugin.getConfig().getDouble(vanillaPath);
            }

            // Fallback to fisher default-xp if configured
            double defaultXp = plugin.getConfig().getDouble("jobs.fisher.default-xp", 0.0);
            if (defaultXp > 0.0) {
                return defaultXp;
            }
        }

        // 3. Universal job-level default-xp fallback (works for ALL jobs including FARMER, BREWER, etc.)
        double defaultXp = plugin.getConfig().getDouble("jobs." + job.getKey() + ".default-xp", 0.0);
        if (defaultXp > 0.0) return defaultXp;

        // 4. Fallback to hardcoded tiers for block/mob jobs
        return switch (job) {
            case MINER, LUMBERJACK, BUILDER -> getBlockTierXP(key);
            case HUNTER -> getMobTierXP(key);
            default -> 0.0;
        };
    }

    /**
     * Get Money for a specific job action
     * FIXED: Now checks vanilla-fishing path for Fisher job
     */
    public double getActionMoney(Job job, String action) {
        if (action == null) return 0.0;
        String key = action.toLowerCase().replace(" ", "_").replace("-", "_");

        // 1. Check explicit config first: jobs.{job}.actions.{key}.money
        String path = "jobs." + job.getKey().toLowerCase() + ".actions." + key + ".money";
        if (plugin.getConfig().contains(path)) {
            return plugin.getConfig().getDouble(path);
        }

        // 2. Fisher-specific config paths
        if (job == Job.FISHER) {
            // Check PyroFishing custom fish: jobs.fisher.pyrofishing.custom-fish.{key}.money
            String pyroPath = "jobs.fisher.pyrofishing.custom-fish." + key + ".money";
            if (plugin.getConfig().contains(pyroPath)) {
                return plugin.getConfig().getDouble(pyroPath);
            }

            // Check vanilla fishing: jobs.fisher.vanilla-fishing.fish.{key}.money
            String vanillaPath = "jobs.fisher.vanilla-fishing.fish." + key + ".money";
            if (plugin.getConfig().contains(vanillaPath)) {
                return plugin.getConfig().getDouble(vanillaPath);
            }

            // Fallback to fisher default-money if configured
            double defaultMoney = plugin.getConfig().getDouble("jobs.fisher.default-money", 0.0);
            if (defaultMoney > 0.0) {
                return defaultMoney;
            }
        }

        // 3. Fallback to hardcoded tiers for block/mob jobs
        return switch (job) {
            case MINER, LUMBERJACK, BUILDER -> getBlockMoneyTier(key);
            case HUNTER -> getMobMoneyTier(key);
            default -> 0.0;
        };
    }

    private double getBlockTierXP(String materialName) {
        String n = materialName.toUpperCase();
        Material mat = Material.getMaterial(n);
        if (mat != null && !mat.isBlock()) return 2.05;
        // Tier 1: Soft/common
        if (n.contains("DIRT") || n.contains("SAND") || n.contains("GRAVEL") || n.contains("NETHERRACK")) return 1.03;
        // Tier 3: Deep/hard stone
        if (n.contains("DEEPSLATE") || n.contains("TUFF") || n.contains("END_STONE")) return 3.07;
        // Tier 4: Coal / Copper
        if (n.contains("COAL") || n.contains("COPPER")) return 7.69;
        // Tier 5: Iron / Gold
        if (n.contains("IRON") || n.contains("GOLD")) return 10.25;
        // Tier 6: Lapis / Redstone / Quartz
        if (n.contains("LAPIS") || n.contains("REDSTONE") || n.contains("QUARTZ")) return 12.81;
        // Tier 7: Diamond / Emerald
        if (n.contains("DIAMOND") || n.contains("EMERALD")) return 15.38;
        // Tier 8: Obsidian / Ancient Debris
        if (n.contains("OBSIDIAN") || n.contains("ANCIENT_DEBRIS")) return 20.50;
        // Tier 2: Generic stone-like (GRANITE, ANDESITE, DIORITE, TERRACOTTA, BASALT, CALCITE, etc.)
        return 2.05;
    }

    private double getBlockMoneyTier(String materialName) {
        String n = materialName.toUpperCase();
        Material mat = Material.getMaterial(n);
        if (mat != null && !mat.isBlock()) return 0.010;
        // Tier 1: Soft/common
        if (n.contains("DIRT") || n.contains("SAND") || n.contains("GRAVEL") || n.contains("NETHERRACK")) return 0.005;
        // Tier 3: Deep/hard stone
        if (n.contains("DEEPSLATE") || n.contains("TUFF") || n.contains("END_STONE")) return 0.015;
        // Tier 4: Coal / Copper
        if (n.contains("COAL") || n.contains("COPPER")) return 0.037;
        // Tier 5: Iron / Gold
        if (n.contains("IRON") || n.contains("GOLD")) return 0.050;
        // Tier 6: Lapis / Redstone / Quartz
        if (n.contains("LAPIS") || n.contains("REDSTONE") || n.contains("QUARTZ")) return 0.063;
        // Tier 7: Diamond / Emerald
        if (n.contains("DIAMOND") || n.contains("EMERALD")) return 0.075;
        // Tier 8: Obsidian / Ancient Debris
        if (n.contains("OBSIDIAN") || n.contains("ANCIENT_DEBRIS")) return 0.100;
        // Tier 2: Generic stone-like (default)
        return 0.010;
    }

    private double getMobMoneyTier(String entityName) {
        try {
            EntityType type = EntityType.valueOf(entityName.toUpperCase());
            return switch (type) {
                case CHICKEN, COW, PIG, SHEEP, BAT -> 0.025;
                case ZOMBIE, SKELETON, SPIDER, CREEPER -> 0.125;
                case ENDERMAN, BLAZE, WITCH -> 0.150;
                case RAVAGER, IRON_GOLEM, HOGLIN -> 0.400;
                case WITHER, ELDER_GUARDIAN, ENDER_DRAGON -> 2.000;
                case WARDEN -> 3.750;
                default -> 0.075;
            };
        } catch (Exception e) { return 0.075; }
    }

    private double getMobTierXP(String entityName) {
        try {
            EntityType type = EntityType.valueOf(entityName.toUpperCase());
            return switch (type) {
                case CHICKEN, COW, PIG, SHEEP, BAT -> 0.02;
                case ZOMBIE, SKELETON, SPIDER, CREEPER -> 0.08;
                case ENDERMAN, BLAZE, WITCH -> 0.15;
                case RAVAGER, IRON_GOLEM, HOGLIN -> 0.50;
                case WITHER, ELDER_GUARDIAN, ENDER_DRAGON -> 2.0;
                case WARDEN -> 5.0;
                default -> 0.05;
            };
        } catch (Exception e) { return 0.05; }
    }

    public double getXpRequired(Job job, int level, int prestige) {
        int effectiveLevel = Math.max(1, level);
        double baseXP = getBaseXPForJob(job) * effectiveLevel;
        double prestigeScaling = 1.0 + (prestige * prestigeXPRequirementPerPrestige);
        return baseXP * prestigeScaling;
    }

    public double getMoneyReward(int level, int prestige) {
        double base = baseMoneyPerLevel * level;
        double mult = 1.0 + (prestige * prestigeMoneyPerPrestige);
        return base * mult;
    }

    public double getBaseXPForJob(Job job) { return jobBaseXP.getOrDefault(job, baseXPPerLevel); }

    // This is your existing XP Multiplier (e.g. 1.1x)
    public double getRankMultiplier(String rank) { return plugin.getConfig().getDouble("rank-multipliers." + rank.toLowerCase(), 1.0); }

    public double getRankXPMultiplier(Player player) {
        if (player.hasPermission("SwagJobs.rank.flea")) return getRankMultiplier("flea");
        if (player.hasPermission("SwagJobs.rank.lizard")) return getRankMultiplier("lizard");
        if (player.hasPermission("SwagJobs.rank.axolotl")) return getRankMultiplier("axolotl");
        return getRankMultiplier("member");
    }

    public int getMaxLevel() { return maxLevel; }
    public int getMaxPrestige() { return maxPrestige; }
    public double getPrestigeXPMultiplier() { return prestigeXPGainPerPrestige; }
    public double getPrestigeMoneyMultiplier() { return prestigeMoneyPerPrestige; }
    public double getMoneyPerXP() { return moneyPerXP; }
    public double getPrestigeXPReqMultiplier() { return prestigeXPRequirementPerPrestige; }
    public double getBaseMoneyPerLevel() { return baseMoneyPerLevel; }
}