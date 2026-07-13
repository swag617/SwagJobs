package com.swag.swagjobs.manager;

import com.swag.swagjobs.SwagJobsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.UUID;

public class SmelterCapManager {
    private final SwagJobsPlugin plugin;

    private static final int CAP_MEMBER = 10;
    private static final int CAP_AXOLOTL = 15;
    private static final int CAP_LIZARD = 20;
    private static final int CAP_FLEA = 25;

    private static final Material[] SMELTER_BLOCKS = {
            Material.FURNACE,
            Material.BLAST_FURNACE,
            Material.SMOKER,
            Material.BREWING_STAND
    };

    public SmelterCapManager(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    public int getCapForPlayer(Player player) {
        if (player.hasPermission("SwagJobs.rank.flea")) {
            return CAP_FLEA;
        } else if (player.hasPermission("SwagJobs.rank.lizard")) {
            return CAP_LIZARD;
        } else if (player.hasPermission("SwagJobs.rank.axolotl")) {
            return CAP_AXOLOTL;
        } else {
            return CAP_MEMBER;
        }
    }

    public void registerSmelterBlock(Player player, Location location, Material blockType) {
        if (!isSmelterBlock(blockType)) return;

        plugin.getDatabaseManager().addSmelterBlock(
                player.getUniqueId(),
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                blockType.name()
        );
    }

    public void unregisterSmelterBlock(Location location) {
        plugin.getDatabaseManager().removeSmelterBlock(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    public boolean isSmelterBlock(Material material) {
        for (Material smelter : SMELTER_BLOCKS) {
            if (material == smelter) {
                return true;
            }
        }
        return false;
    }

    public int getRemainingCapacity(Player player) {
        int cap = getCapForPlayer(player);
        int currentCount = plugin.getDatabaseManager().getSmelterBlockCount(player.getUniqueId());
        return Math.max(0, cap - currentCount);
    }

    /**
     * Determine whether this block should grant XP for the given player.
     * The policy: only the first `cap` registered blocks (ORDER BY id ASC) for that player grant XP.
     *
     * Returns true if:
     * - the block belongs to that player (owner matches), and
     * - the block is within the first `cap` registered blocks for that player.
     */
    public boolean isAuthorized(Player player, Block block) {
        if (player == null || block == null) return false;

        UUID ownerUuid = plugin.getDatabaseManager().getSmelterOwnerUUID(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()
        );

        if (ownerUuid == null) return false;
        if (!ownerUuid.equals(player.getUniqueId())) return false;

        int cap = getCapForPlayer(player);

        return plugin.getDatabaseManager().isSmelterBlockWithinCap(ownerUuid,
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ(),
                cap);
    }
}