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

        // STRICT CHECK: Use Bukkit Tags for Logs
        if (!Tag.LOGS.isTagged(type)) return;
        if (block.hasMetadata("placed")) return;

        Player player = event.getPlayer();

        // Anti-exploit: check place-break cycles before awarding XP
        if (!plugin.getCheatDetectionManager().canGainXp(player, block.getLocation())) return;

        // use lowercase action keys to match config parsing
        String actionName = type.name().toLowerCase();

        // Let JobManager handle XP/money/streaks/cheat detection
        plugin.getJobManager().processAction(player, Job.LUMBERJACK, actionName);
    }
}