package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import org.bukkit.Material;
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

        if (plugin.getJobsConfig().getActionXP(Job.CRAFTER, actionKey) <= 0) return;

        int amount = result.getAmount();
        if (event.isShiftClick()) {
            amount = calculateMaxCraft(event);
        }

        for (int i = 0; i < amount; i++) {
            plugin.getJobManager().processAction(player, Job.CRAFTER, actionKey);
        }
    }

    private int calculateMaxCraft(CraftItemEvent event) {
        if (event.getAction() != org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return event.getRecipe().getResult().getAmount();
        }
        // For shift-click, find the minimum ingredient stack size to determine
        // how many times the recipe completes
        org.bukkit.inventory.CraftingInventory inv = event.getInventory();
        int minIngredient = Integer.MAX_VALUE;
        for (int i = 1; i < inv.getSize(); i++) {
            ItemStack ingredient = inv.getItem(i);
            if (ingredient != null && ingredient.getType() != Material.AIR) {
                minIngredient = Math.min(minIngredient, ingredient.getAmount());
            }
        }
        if (minIngredient == Integer.MAX_VALUE) minIngredient = 1;
        return minIngredient * event.getRecipe().getResult().getAmount();
    }
}