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

        // Get owner UUID from database instead of in-memory map
        UUID owner = plugin.getDatabaseManager().getSmelterOwnerUUID(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()
        );

        if (owner == null) return;

        Player player = plugin.getServer().getPlayer(owner);
        if (player == null) return;

        // CAP CHECK: Only award XP if this stand is within their allowed credited cap
        if (!plugin.getSmelterCapManager().isAuthorized(player, block)) {
            return;
        }

        // Anti-exploit: check place-break cycles for this brewing-stand location
        if (!plugin.getCheatDetectionManager().canGainXp(player, block.getLocation())) {
            return;
        }

        // Count potions brewed (3 slots)
        BrewingStand stand = (BrewingStand) block.getState();
        int potionCount = 0;

        for (int i = 0; i < 3; i++) {
            ItemStack item = stand.getInventory().getItem(i);
            if (item != null && item.getType().name().contains("POTION")) {
                potionCount++;
            }
        }

        String actionKey = "regular_potion";

        // Only continue if this action has XP configured
        if (plugin.getJobsConfig().getActionXP(Job.BREWER, actionKey) <= 0) return;

        for (int i = 0; i < potionCount; i++) {
            // Pass the action KEY (String)
            plugin.getJobManager().processAction(player, Job.BREWER, actionKey);
        }
    }

    // Track who placed the brewing stand using DATABASE instead of HashMap
    @EventHandler
    public void onBrewingStandPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        if (event.getBlock().getType().name().contains("BREWING_STAND")) {
            // Register with cap manager (will be recorded in DATABASE for credit ordering)
            plugin.getSmelterCapManager().registerSmelterBlock(
                    event.getPlayer(),
                    event.getBlock().getLocation(),
                    event.getBlock().getType()
            );
        }
    }

    @EventHandler
    public void onBrewingStandBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (event.getBlock().getType().name().contains("BREWING_STAND")) {
            // Unregister from cap manager / DB
            plugin.getSmelterCapManager().unregisterSmelterBlock(event.getBlock().getLocation());
        }
    }
}