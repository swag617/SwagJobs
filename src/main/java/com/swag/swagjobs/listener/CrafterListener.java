package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

public class CrafterListener implements Listener {
    private final SwagJobsPlugin plugin;

    public CrafterListener(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getRecipe().getResult();
        String actionKey = result.getType().name();

        // Only continue if the action has XP configured
        if (plugin.getJobsConfig().getActionXP(Job.CRAFTER, actionKey) <= 0) return;

        // Per-item crafting: Award XP for each item crafted
        int amount = result.getAmount();
        if (event.isShiftClick()) {
            // Simplified: assume recipe result amount (you can improve this later)
            amount = calculateMaxCraft(event);
        }

        for (int i = 0; i < amount; i++) {
            // Pass the action KEY (String), not the double XP
            plugin.getJobManager().processAction(player, Job.CRAFTER, actionKey);
        }
    }

    private int calculateMaxCraft(CraftItemEvent event) {
        // Simplified: return the result amount
        return event.getRecipe().getResult().getAmount();
    }
}