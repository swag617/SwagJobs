package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class BuilderListener implements Listener {
    private final SwagJobsPlugin plugin;

    public BuilderListener(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Material type = event.getBlock().getType();

        if (isFarmingBlock(type)) return;

        Player player = event.getPlayer();

        if (!plugin.getPlaceBreakManager().canGainXp(player, event.getBlock().getLocation())) return;

        String actionKey = type.name();
        if (plugin.getJobsConfig().getActionXP(Job.BUILDER, actionKey) <= 0) return;

        plugin.getJobManager().processAction(player, Job.BUILDER, actionKey);
    }

    private boolean isFarmingBlock(Material type) {
        return type == Material.WHEAT || type == Material.CARROTS ||
                type == Material.POTATOES || type == Material.BEETROOTS ||
                type == Material.NETHER_WART || type == Material.SUGAR_CANE ||
                type == Material.PUMPKIN || type == Material.MELON ||
                type == Material.COCOA || type == Material.SWEET_BERRY_BUSH;
    }
}