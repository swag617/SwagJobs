package com.swag.swagjobs.util;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;

public class XPCalculator {
    private final SwagJobsPlugin plugin;

    public XPCalculator(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    public double getRequiredXP(Job job, int level, int prestige) {
        return plugin.getJobsConfig().getXpRequired(job, level, prestige);
    }
}