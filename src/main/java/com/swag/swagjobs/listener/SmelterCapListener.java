package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class SmelterCapListener implements Listener {
    private final SwagJobsPlugin plugin;

    public SmelterCapListener(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSmelterPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();

        if (!isSmelter(block.getType())) return;

        plugin.getSmelterCapManager().registerSmelterBlock(player, block.getLocation(), block.getType());

        // First-N-registered policy: blocks placed after the cap is full are not credited for XP.
        boolean credited = plugin.getSmelterCapManager().isAuthorized(player, block);

        int remaining = plugin.getSmelterCapManager().getRemainingCapacity(player);
        String blockName = getFriendlyBlockName(block.getType());

        if (credited) {
            player.sendMessage("§a§l" + blockName + " Placed and credited for XP! §7Remaining credited slots: §f" + remaining);
        } else {
            player.sendMessage("§e§l" + blockName + " Placed (not credited for XP — cap reached). §7Credited slots: §f" + Math.max(0, plugin.getCapForPlayer(player) - remaining));
        }
    }

    private String getFriendlyBlockName(Material material) {
        return switch (material) {
            case FURNACE -> "Furnace";
            case BLAST_FURNACE -> "Blast Furnace";
            case SMOKER -> "Smoker";
            case BREWING_STAND -> "Brewing Stand";
            default -> "Smelter";
        };
    }

    @EventHandler(ignoreCancelled = true)
    public void onSmelterBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isSmelter(block.getType())) return;

        plugin.getSmelterCapManager().unregisterSmelterBlock(block.getLocation());
    }

    private boolean isSmelter(Material m) {
        return m == Material.FURNACE
                || m == Material.BLAST_FURNACE
                || m == Material.SMOKER
                || m == Material.BREWING_STAND;
    }
}