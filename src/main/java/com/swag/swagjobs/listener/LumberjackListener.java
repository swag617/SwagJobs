package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class LumberjackListener implements Listener {
    private final SwagJobsPlugin plugin;

    public LumberjackListener(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();

        if (!Tag.LOGS.isTagged(type)) return;
        if (block.hasMetadata("placed")) return;

        Player player = event.getPlayer();

        String actionName = type.name().toLowerCase();

        if (!plugin.getPlaceBreakManager().canGainXp(player, block.getLocation())) return;

        plugin.getJobManager().processAction(player, Job.LUMBERJACK, actionName);
    }
}