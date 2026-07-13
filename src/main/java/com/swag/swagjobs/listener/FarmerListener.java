package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class FarmerListener implements Listener {
    private final SwagJobsPlugin plugin;

    public FarmerListener(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Material type = event.getBlock().getType();

        if (isFarmingBlock(type)) {
            Player player = event.getPlayer();
            if (!plugin.getPlaceBreakManager().canGainXp(player, event.getBlock().getLocation())) return;
            plugin.getJobManager().processAction(player, Job.FARMER, type.name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();

        if (!isFarmingBlock(type)) return;

        Player player = event.getPlayer();

        if (!plugin.getPlaceBreakManager().canGainXp(player, block.getLocation())) return;

        // Sugar cane: only award XP when breaking a non-base piece (the block below is also sugar cane)
        if (type == Material.SUGAR_CANE) {
            Block below = block.getRelative(0, -1, 0);
            if (below.getType() == Material.SUGAR_CANE) {
                plugin.getJobManager().processAction(player, Job.FARMER, type.name());
            }
            return;
        }

        if (block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) return;
        }

        plugin.getJobManager().processAction(player, Job.FARMER, type.name());
    }

    private boolean isFarmingBlock(Material type) {
        return type == Material.WHEAT || type == Material.CARROTS ||
                type == Material.POTATOES || type == Material.BEETROOTS ||
                type == Material.NETHER_WART || type == Material.SUGAR_CANE ||
                type == Material.PUMPKIN || type == Material.MELON ||
                type == Material.COCOA || type == Material.SWEET_BERRY_BUSH ||
                type == Material.HAY_BLOCK;
    }
}