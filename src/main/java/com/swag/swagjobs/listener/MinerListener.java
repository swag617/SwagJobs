package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class MinerListener implements Listener {
    private final SwagJobsPlugin plugin;

    public MinerListener(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();

        String name = type.name();
        boolean isMiningBlock = name.contains("ORE") || name.contains("STONE") ||
                name.contains("DEEPSLATE") || name.contains("NETHERRACK") ||
                name.contains("OBSIDIAN") || name.contains("BASALT") ||
                name.contains("DIRT") || name.contains("GRAVEL") ||
                name.contains("SAND") || name.contains("CLAY") ||
                name.contains("COBBLESTONE") || name.contains("END_STONE") ||
                name.contains("GRANITE") || name.contains("ANDESITE") ||
                name.contains("DIORITE") || name.contains("CALCITE") ||
                name.contains("TUFF") || name.contains("TERRACOTTA") ||
                name.contains("ANCIENT_DEBRIS");

        if (!isMiningBlock) return;
        if (block.hasMetadata("placed")) return;

        Player player = event.getPlayer();
        String actionKey = type.name();

        if (plugin.getJobsConfig().getActionXP(Job.MINER, actionKey) <= 0) return;

        if (!plugin.getPlaceBreakManager().canGainXp(player, block.getLocation())) return;

        plugin.getJobManager().processAction(player, Job.MINER, actionKey);
    }
}