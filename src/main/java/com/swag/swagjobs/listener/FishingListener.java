package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
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
 * 1. SwagFishing (if installed) - detects custom fish rarity via item metadata
 * 2. Vanilla Fishing (fallback) - Uses actual fish types (cod, salmon, etc.)
 *
 * SwagFishing doesn't fire its own catch event - it awards XP by calling into
 * SwagJobsAPI directly and expects SwagJobs to detect its own catches off the
 * vanilla PlayerFishEvent, so both modes listen to the same event.
 */
public class FishingListener implements Listener {
    private final SwagJobsPlugin plugin;

    public FishingListener(SwagJobsPlugin plugin) {
        this.plugin = plugin;

        boolean swagFishingPresent = plugin.getServer().getPluginManager().getPlugin("SwagFishing") != null;
        if (swagFishingPresent) {
            plugin.getLogger().info("SwagFishing detected - using custom fish rarities");
        } else {
            plugin.getLogger().info("SwagFishing not found - using vanilla fishing");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Player player = event.getPlayer();
        if (player == null) return;

        if (!(event.getCaught() instanceof Item)) return;

        Item caughtItem = (Item) event.getCaught();
        ItemStack itemStack = caughtItem.getItemStack();

        String rarity = detectFishRarity(itemStack);
        String actionKey = rarity != null ? rarity : getVanillaFishType(itemStack);

        plugin.getJobManager().processAction(player, Job.FISHER, actionKey);
    }

    private String getVanillaFishType(ItemStack item) {
        if (item == null) return "fish";

        Material type = item.getType();
        return switch (type) {
            case COD -> "cod";
            case SALMON -> "salmon";
            case TROPICAL_FISH -> "tropical_fish";
            case PUFFERFISH -> "pufferfish";
            default -> "fish"; // Generic fallback
        };
    }

    /**
     * SwagFishing tags its custom catches with a rarity name in the item's lore/display
     * name (quartz/emerald/sapphire/ruby/amethyst/prismatic). Returns null if no rarity
     * is detected so callers fall back to vanilla fish-type XP.
     */
    private String detectFishRarity(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                String cleanLine = ChatColor.stripColor(line).toLowerCase();
                String rarity = matchRarity(cleanLine);
                if (rarity != null) return rarity;
            }
        }

        String displayName = meta.hasDisplayName() ?
                ChatColor.stripColor(meta.getDisplayName()).toLowerCase() : "";

        return matchRarity(displayName);
    }

    private String matchRarity(String text) {
        if (text.contains("prismatic")) return "prismatic";
        if (text.contains("amethyst")) return "amethyst";
        if (text.contains("ruby")) return "ruby";
        if (text.contains("sapphire")) return "sapphire";
        if (text.contains("emerald")) return "emerald";
        if (text.contains("quartz")) return "quartz";
        return null;
    }
}
