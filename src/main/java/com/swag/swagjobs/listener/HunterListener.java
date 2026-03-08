package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.inventory.EntityEquipment;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * HunterListener
 *
 * - Processes Hunter XP for entity kills.
 * - Handles stacked mobs so the stack "counts down" instead of all dying at once.
 *
 * Behavior:
 * - If entity has stack metadata (common keys checked), and stackSize > 1:
 *     - Give XP for one kill (processAction as normal)
 *     - Clear drops and droppedExp for this death to avoid duplicate loot
 *     - Spawn a replacement entity with stackSize-1 (metadata key "SwagJobs_stack")
 * - Otherwise behave normally.
 */
public class HunterListener implements Listener {
    private final SwagJobsPlugin plugin;

    // Common metadata keys used by various stacking solutions; we check these to find the stack size.
    private static final List<String> COMMON_STACK_KEYS = Arrays.asList(
            "SwagJobs_stack",   // our plugin-specific key (preferred)
            "stack",            // common generic key
            "stack-size",
            "stack_size",
            "stackAmount",
            "stackAmountInt",
            "stackCount",
            "STACK_SIZE"
    );

    public HunterListener(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer == null) return;

        // FORCE LOWERCASE HERE
        String actionKey = entity.getType().name().toLowerCase();

        // If mob was from a spawner, try special config key "spawner_<type>"
        if (isFromSpawner(entity)) {
            String spawnerKey = "spawner_" + actionKey; // actionKey is already lowercase
            if (plugin.getJobsConfig().getActionXP(Job.HUNTER, spawnerKey) > 0) {
                actionKey = spawnerKey;
            }
        }

        // Now it will correctly find "iron_golem" or "wither_skeleton" in the config
        if (plugin.getJobsConfig().getActionXP(Job.HUNTER, actionKey) <= 0) return;

        plugin.getJobManager().processAction(killer, Job.HUNTER, actionKey);

        int stackSize = readStackSize(entity);

        if (stackSize > 1) {
            // This death belonged to a stack. We should:
            //  - prevent duplicate drops/XP (already given for one)
            //  - spawn a replacement entity with stackSize-1
            event.getDrops().clear();
            event.setDroppedExp(0);

            int newSize = stackSize - 1;
            Location spawnLocation = entity.getLocation();

            // Spawn a replacement entity next tick (safe timing)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    LivingEntity replacement = (LivingEntity) spawnLocation.getWorld().spawnEntity(spawnLocation, entity.getType());

                    // Preserve simple visual state
                    if (entity.getCustomName() != null) {
                        replacement.setCustomName(entity.getCustomName());
                        replacement.setCustomNameVisible(entity.isCustomNameVisible());
                    }

                    // Try to preserve equipment where sensible (armor/weapons)
                    try {
                        EntityEquipment eqSrc = entity.getEquipment();
                        EntityEquipment eqDst = replacement.getEquipment();
                        if (eqSrc != null && eqDst != null) {
                            eqDst.setArmorContents(eqSrc.getArmorContents());
                            eqDst.setItemInMainHand(eqSrc.getItemInMainHand());
                            eqDst.setItemInOffHand(eqSrc.getItemInOffHand());
                        }
                    } catch (Throwable ignored) {
                        // Don't break if equipment can't be copied
                    }

                    // Mark the replacement with our stack metadata so future deaths keep counting down
                    replacement.setMetadata("SwagJobs_stack", new FixedMetadataValue(plugin, newSize));
                } catch (Throwable t) {
                    plugin.getLogger().warning("Failed to spawn replacement stacked mob: " + t.getMessage());
                    t.printStackTrace();
                }
            });
        }

        // If stackSize <= 1 or no stack metadata, nothing extra to do.
    }

    /**
     * Reads stack size from entity metadata - checks several common metadata keys.
     * Returns 0 if no stack metadata found, otherwise returns the integer stack size (>=1).
     */
    private int readStackSize(Entity entity) {
        // First prefer our own metadata key
        Optional<Integer> ourKey = readIntMetadata(entity, "SwagJobs_stack");
        if (ourKey.isPresent()) return ourKey.get();

        // Next try common keys
        for (String key : COMMON_STACK_KEYS) {
            Optional<Integer> val = readIntMetadata(entity, key);
            if (val.isPresent()) return val.get();
        }

        // No metadata found
        return 0;
    }

    /**
     * Helper: read integer metadata value if present and parsable
     */
    private Optional<Integer> readIntMetadata(Entity entity, String key) {
        if (!entity.hasMetadata(key)) return Optional.empty();
        try {
            List<MetadataValue> vals = entity.getMetadata(key);
            if (vals == null || vals.isEmpty()) return Optional.empty();
            for (MetadataValue mv : vals) {
                Object v = mv.value();
                if (v instanceof Number) {
                    return Optional.of(((Number) v).intValue());
                }
                if (v instanceof String) {
                    try {
                        return Optional.of(Integer.parseInt((String) v));
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return Optional.empty();
    }

    /**
     * Detects if a mob is from a spawner
     */
    private boolean isFromSpawner(Entity entity) {
        return entity.hasMetadata("spawner") ||
                entity.getEntitySpawnReason() == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER;
    }
}