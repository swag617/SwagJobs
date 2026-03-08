package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import me.arsmagica.API.PyroFishCatchEvent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * FishingListener
 *
 * DUAL-MODE SUPPORT:
 * 1. PyroFishing (if installed) - Uses custom fish tiers for varying XP
 * 2. Vanilla Fishing (fallback) - Uses actual fish types (cod, salmon, etc.)
 *
 * This prevents the "no XP" bug when PyroFishing is not installed.
 */
public class FishingListener implements Listener {
    private final SwagJobsPlugin plugin;
    private final boolean pyroFishingPresent;

    public FishingListener(SwagJobsPlugin plugin) {
        this.plugin = plugin;

        // Check if PyroFishing is installed
        this.pyroFishingPresent = plugin.getServer().getPluginManager().getPlugin("PyroFishing") != null;

        if (pyroFishingPresent) {
            plugin.getLogger().info("PyroFishing detected - using custom fish tiers");
        } else {
            plugin.getLogger().info("PyroFishing not found - using vanilla fishing");
        }
    }

    /**
     * PRIMARY: PyroFishing custom event
     * Only fires if PyroFishing plugin is installed
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPyroFish(PyroFishCatchEvent event) {
        if (event == null) return;

        Player player = event.getPlayer();
        if (player == null) return;

        ItemStack caught = event.getItemStack();
        String pyroTier = null;

        try {
            // Pyro provides the tier directly (e.g., "Silver", "Gold")
            pyroTier = event.getTier();
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
            // Fallback if the API version changes
        }

        String actionKey;
        if (pyroTier != null && !pyroTier.isEmpty()) {
            actionKey = pyroTier.toLowerCase();
        } else {
            // Manual fallback detection if Pyro doesn't provide a tier string
            actionKey = detectFishTier(caught);
        }

        // Process the action through the JobManager
        plugin.getJobManager().processAction(player, Job.FISHER, actionKey);
    }

    /**
     * FALLBACK: Vanilla PlayerFishEvent
     * Only used if PyroFishing is NOT installed
     * Uses actual vanilla fish types (cod, salmon, pufferfish, tropical_fish)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVanillaFish(PlayerFishEvent event) {
        // Skip if PyroFishing is present (let their event handle it)
        if (pyroFishingPresent) {
            return;
        }

        // Only care about successful catches
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) return;

        // Check if actually caught an item
        if (!(event.getCaught() instanceof Item)) {
            return;
        }

        Item caughtItem = (Item) event.getCaught();
        ItemStack itemStack = caughtItem.getItemStack();

        // Get the actual fish type caught
        String fishType = getVanillaFishType(itemStack);

        // Process vanilla fishing XP using the actual fish type
        // Config paths: jobs.fisher.vanilla-fishing.fish.cod.xp, etc.
        plugin.getJobManager().processAction(player, Job.FISHER, fishType);
    }

    /**
     * Get vanilla fish type from caught item
     * Returns: cod, salmon, tropical_fish, pufferfish, or "fish" as fallback
     */
    private String getVanillaFishType(ItemStack item) {
        if (item == null) return "fish";

        Material type = item.getType();

        // Map Material to config key
        return switch (type) {
            case COD -> "cod";
            case SALMON -> "salmon";
            case TROPICAL_FISH -> "tropical_fish";
            case PUFFERFISH -> "pufferfish";
            default -> "fish"; // Generic fallback
        };
    }

    /**
     * Fallback detection logic using Lore and Display Name.
     * Used for PyroFishing API compatibility when tier is not provided.
     */
    private String detectFishTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "common";

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "common";

        // Check Lore for "Tier:"
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                String cleanLine = ChatColor.stripColor(line).toLowerCase();
                if (cleanLine.contains("tier:")) {
                    if (cleanLine.contains("mythical")) return "mythical";
                    if (cleanLine.contains("platinum")) return "platinum";
                    if (cleanLine.contains("diamond")) return "diamond";
                    if (cleanLine.contains("gold")) return "gold";
                    if (cleanLine.contains("silver")) return "silver";
                    if (cleanLine.contains("bronze")) return "bronze";
                }
            }
        }

        // Check Display Name as a secondary backup
        String displayName = meta.hasDisplayName() ?
                ChatColor.stripColor(meta.getDisplayName()).toLowerCase() : "";

        if (displayName.contains("mythical")) return "mythical";
        if (displayName.contains("platinum")) return "platinum";
        if (displayName.contains("diamond")) return "diamond";
        if (displayName.contains("gold")) return "gold";
        if (displayName.contains("silver")) return "silver";
        if (displayName.contains("bronze")) return "bronze";

        return "common";
    }
}