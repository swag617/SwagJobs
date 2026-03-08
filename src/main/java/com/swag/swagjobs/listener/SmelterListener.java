package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;

import java.util.UUID;

public class SmelterListener implements Listener {
    private final SwagJobsPlugin plugin;

    public SmelterListener(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        // Get the owner of this furnace from the database
        UUID ownerUUID = plugin.getDatabaseManager().getSmelterOwnerUUID(
                event.getBlock().getWorld().getName(),
                event.getBlock().getX(),
                event.getBlock().getY(),
                event.getBlock().getZ()
        );

        if (ownerUUID == null) return;

        Player player = plugin.getServer().getPlayer(ownerUUID);
        if (player == null) return; // Player must be online to get XP

        // CAP CHECK: Only award XP if this furnace is within their allowed credited cap
        if (!plugin.getSmelterCapManager().isAuthorized(player, event.getBlock())) {
            return;
        }

        // Anti-exploit: check place-break cycles for this furnace location
        if (!plugin.getCheatDetectionManager().canGainXp(player, event.getBlock().getLocation())) {
            return;
        }

        String actionKey = event.getResult().getType().name();

        // Only continue if the action has XP configured
        if (plugin.getJobsConfig().getActionXP(Job.SMELTER, actionKey) <= 0) return;

        // Award XP for the smelted item (fires when smelting completes, works with hoppers!)
        plugin.getJobManager().processAction(player, Job.SMELTER, actionKey);
    }
}