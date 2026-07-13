package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class BrewerListener implements Listener {
    private final SwagJobsPlugin plugin;

    public BrewerListener(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        Block block = event.getBlock();

        UUID owner = plugin.getDatabaseManager().getSmelterOwnerUUID(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()
        );

        if (owner == null) return;

        Player player = plugin.getServer().getPlayer(owner);
        if (player == null) return;

        if (!plugin.getSmelterCapManager().isAuthorized(player, block)) return;

        if (!plugin.getPlaceBreakManager().canGainXp(player, block.getLocation())) return;

        BrewingStand stand = (BrewingStand) block.getState();
        int potionCount = 0;

        for (int i = 0; i < 3; i++) {
            ItemStack item = stand.getInventory().getItem(i);
            if (item != null && item.getType().name().contains("POTION")) {
                potionCount++;
            }
        }

        String actionKey = "regular_potion";
        if (plugin.getJobsConfig().getActionXP(Job.BREWER, actionKey) <= 0) return;

        for (int i = 0; i < potionCount; i++) {
            plugin.getJobManager().processAction(player, Job.BREWER, actionKey);
        }
    }

}