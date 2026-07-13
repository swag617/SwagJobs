package com.swag.swagjobs.util;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.JobProgress;

public class  MoneyCalculator {
    private final SwagJobsPlugin plugin;

    public MoneyCalculator(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Calculates the money reward for reaching a specific level.
     * Formula: (Level * BaseMoney) * (1 + (Prestige * 0.08))
     */
    public double calculateLevelReward(int level, int prestige) {
        double baseMoneyPerLevel = plugin.getJobsConfig().getBaseMoneyPerLevel();
        double baseReward = level * baseMoneyPerLevel;

        double prestigeMultiplier = 1.0 + (prestige * plugin.getJobsConfig().getPrestigeMoneyMultiplier());
        return baseReward * prestigeMultiplier;
    }
}