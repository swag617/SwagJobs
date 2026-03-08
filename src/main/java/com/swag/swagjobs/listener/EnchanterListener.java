package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;

public class EnchanterListener implements Listener {
    private final SwagJobsPlugin plugin;

    public EnchanterListener(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        int expLevel = event.getExpLevelCost();

        // Map enchant level to config tier
        String actionKey;
        if (expLevel >= 30) actionKey = "level_30";
        else if (expLevel >= 15) actionKey = "level_15";
        else actionKey = "level_1";

        // Only continue if the action has XP configured
        if (plugin.getJobsConfig().getActionXP(Job.ENCHANTER, actionKey) <= 0) return;

        // Pass the action KEY (String)
        plugin.getJobManager().processAction(player, Job.ENCHANTER, actionKey);
    }
}