package com.swag.swagjobs.manager;

import com.swag.swagjobs.SwagJobsPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks place-break exploit cycles per player per block location.
 *
 * When a player places a tracked block type and then breaks it themselves,
 * a cycle is counted for that location. Once the configured cycle limit is
 * exceeded, canGainXp() returns false and a console warning is emitted.
 * No admin messages or player messages are sent.
 *
 * Config keys (with defaults):
 *   anticheat.place-break-max-cycles: 3
 *   anticheat.place-break-timeout-seconds: 120
 *   anticheat.track-exploit-blocks: [PUMPKIN, MELON]
 */
public class PlaceBreakManager implements Listener {

    /** Metadata key stamped on placed blocks to record the placer's UUID. */
    private static final String META_PLACED_BY = "swagjobs_placed_by";

    private final SwagJobsPlugin plugin;

    /** Runtime-configurable max cycles before XP is suppressed. */
    private int maxAllowedCycles;

    /** Seconds before a location's cycle record expires. */
    private final int timeoutSeconds;

    /**
     * Tracks how many place-break cycles each player has performed at each location.
     * Key: UUID of the player. Value: map of encoded location string -> CycleRecord.
     * ConcurrentHashMap used so the periodic cleanup task can safely iterate
     * while main-thread events may be active.
     */
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, CycleRecord>> cycleData = new ConcurrentHashMap<>();

    /** Players currently within their admin-alert cooldown window (to avoid alert spam). */
    private final Set<UUID> recentlyFlagged = ConcurrentHashMap.newKeySet();

    /**
     * Materials for which place-break cycling is tracked.
     * Modifications happen only on the main thread via API methods.
     */
    private final Set<Material> trackedMaterials = new HashSet<>();

    private final BukkitTask cleanupTask;

    // -------------------------------------------------------------------------
    // Inner record
    // -------------------------------------------------------------------------

    private static class CycleRecord {
        int count;
        long lastUpdatedMs;

        CycleRecord() {
            this.count = 0;
            this.lastUpdatedMs = System.currentTimeMillis();
        }

        void increment() {
            count++;
            lastUpdatedMs = System.currentTimeMillis();
        }
    }

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public PlaceBreakManager(SwagJobsPlugin plugin) {
        this.plugin = plugin;

        this.maxAllowedCycles = plugin.getConfig().getInt("anticheat.place-break-max-cycles", 3);
        this.timeoutSeconds   = plugin.getConfig().getInt("anticheat.place-break-timeout-seconds", 120);

        // Load tracked materials from config; fall back to sensible defaults.
        List<String> configList = plugin.getConfig().getStringList("anticheat.track-exploit-blocks");
        if (configList.isEmpty()) {
            configList = Arrays.asList("PUMPKIN", "MELON");
        }
        for (String name : configList) {
            try {
                Material mat = Material.valueOf(name.toUpperCase(Locale.ROOT));
                trackedMaterials.add(mat);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[PlaceBreakManager] Unknown material in track-exploit-blocks: " + name);
            }
        }

        // Self-register as a listener.
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Periodic async cleanup — expires old location records.
        // Runs every 60 seconds (1200 ticks). Actual map removal is
        // posted back to the main thread to avoid CME on event handlers.
        this.cleanupTask = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this::purgeExpiredEntries, 1200L, 1200L);

        plugin.getLogger().info("[PlaceBreakManager] Loaded. maxCycles=" + maxAllowedCycles
                + " timeout=" + timeoutSeconds + "s tracked=" + trackedMaterials);
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!trackedMaterials.contains(block.getType())) return;

        // Stamp the placer's UUID onto the block so we can verify ownership on break.
        block.setMetadata(META_PLACED_BY,
                new FixedMetadataValue(plugin, event.getPlayer().getUniqueId().toString()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!trackedMaterials.contains(block.getType())) return;

        // Only count cycles when the breaker is the same player who placed the block.
        if (!block.hasMetadata(META_PLACED_BY)) return;

        String storedUuid = block.getMetadata(META_PLACED_BY).get(0).asString();
        UUID breakerUuid = event.getPlayer().getUniqueId();
        if (!breakerUuid.toString().equals(storedUuid)) return;

        // Increment the cycle count for this player at this location.
        String locKey = encodeLocation(block.getLocation());
        ConcurrentHashMap<String, CycleRecord> playerMap =
                cycleData.computeIfAbsent(breakerUuid, k -> new ConcurrentHashMap<>());
        CycleRecord record = playerMap.computeIfAbsent(locKey, k -> new CycleRecord());
        record.increment();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns true if the player is allowed to gain XP for breaking the block
     * at the given location. Returns false if they have exceeded the configured
     * cycle limit for that location, and emits a console warning.
     *
     * Call this before processAction() in any block-break job listener.
     */
    public boolean canGainXp(Player player, Location loc) {
        UUID uuid = player.getUniqueId();
        ConcurrentHashMap<String, CycleRecord> playerMap = cycleData.get(uuid);
        if (playerMap == null) return true;

        String locKey = encodeLocation(loc);
        CycleRecord record = playerMap.get(locKey);
        if (record == null) return true;

        if (record.count > maxAllowedCycles) {
            Block block = loc.getBlock();
            String blockType  = block.getType().name();
            String worldName  = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
            int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
            plugin.getLogger().warning(String.format(
                    "[Anti-Exploit] %s exceeded place-break cycles on %s at %s (%d,%d,%d)",
                    player.getName(), blockType, worldName, x, y, z));
            alertAdmins(player, blockType);
            return false;
        }
        return true;
    }

    /**
     * Notifies online staff with the configured permission, throttled per-player by
     * anticheat.flag-cooldown-ms so a single farming session doesn't spam the same alert.
     */
    private void alertAdmins(Player player, String blockType) {
        if (!plugin.getConfig().getBoolean("anticheat.alert-admins", false)) return;

        UUID uuid = player.getUniqueId();
        if (!recentlyFlagged.add(uuid)) return;

        long cooldownMs = Math.max(1, plugin.getConfig().getLong("anticheat.flag-cooldown-ms", 60000L));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> recentlyFlagged.remove(uuid), cooldownMs / 50L);

        String adminPerm = plugin.getConfig().getString("anticheat.admin-permission", "SwagJobs.admin.alerts");
        String alertMsg = org.bukkit.ChatColor.DARK_RED + "[Anti-Exploit] " + org.bukkit.ChatColor.GRAY +
                player.getName() + org.bukkit.ChatColor.RED + " exceeded place-break on " +
                org.bukkit.ChatColor.YELLOW + blockType;

        for (Player admin : plugin.getServer().getOnlinePlayers()) {
            if (admin.hasPermission(adminPerm)) {
                admin.sendMessage(alertMsg);
            }
        }
    }

    /** Removes all tracking data for a player (call on player quit). */
    public void cleanupPlayer(UUID uuid) {
        cycleData.remove(uuid);
        recentlyFlagged.remove(uuid);
    }

    /** Manually clears a player's cycle record at a specific location (e.g. an admin override). */
    public void resetPlaceBreakFor(Player player, Location loc) {
        ConcurrentHashMap<String, CycleRecord> playerMap = cycleData.get(player.getUniqueId());
        if (playerMap == null) return;
        playerMap.remove(encodeLocation(loc));
        if (playerMap.isEmpty()) {
            cycleData.remove(player.getUniqueId());
        }
    }

    /** Cancels the background cleanup task (call on plugin disable). */
    public void stopCleanupTask() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
    }

    // -------------------------------------------------------------------------
    // Runtime configuration API
    // -------------------------------------------------------------------------

    public int getMaxAllowedCycles() {
        return maxAllowedCycles;
    }

    public void setMaxAllowedCycles(int maxAllowedCycles) {
        this.maxAllowedCycles = Math.max(0, maxAllowedCycles);
        plugin.getConfig().set("anticheat.place-break-max-cycles", this.maxAllowedCycles);
        plugin.saveConfig();
    }

    public void addTrackedMaterial(String name) {
        try {
            Material mat = Material.valueOf(name.toUpperCase(Locale.ROOT));
            trackedMaterials.add(mat);
            persistTrackedMaterials();
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[PlaceBreakManager] Cannot track unknown material: " + name);
        }
    }

    public void removeTrackedMaterial(String name) {
        try {
            Material mat = Material.valueOf(name.toUpperCase(Locale.ROOT));
            trackedMaterials.remove(mat);
            persistTrackedMaterials();
        } catch (IllegalArgumentException ignored) {
        }
    }

    public Set<String> listTrackedMaterials() {
        Set<String> names = new LinkedHashSet<>();
        for (Material mat : trackedMaterials) {
            names.add(mat.name());
        }
        return Collections.unmodifiableSet(names);
    }

    /** Writes the current tracked-material set back to config.yml so runtime changes survive a restart. */
    private void persistTrackedMaterials() {
        List<String> list = new ArrayList<>();
        for (Material mat : trackedMaterials) {
            list.add(mat.name());
        }
        plugin.getConfig().set("anticheat.track-exploit-blocks", list);
        plugin.saveConfig();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Encodes a block location as a compact string key.
     * World name is included so entries from different worlds never collide.
     */
    private String encodeLocation(Location loc) {
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "null";
        return world + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    /**
     * Runs asynchronously every 60 seconds. Identifies expired location records
     * and schedules their removal on the main thread.
     */
    private void purgeExpiredEntries() {
        long nowMs = System.currentTimeMillis();
        long timeoutMs = (long) timeoutSeconds * 1000L;

        // Collect UUIDs and location keys that have expired.
        // We only read here; actual removal is done on the main thread.
        Map<UUID, List<String>> toRemove = new HashMap<>();

        for (Map.Entry<UUID, ConcurrentHashMap<String, CycleRecord>> playerEntry : cycleData.entrySet()) {
            List<String> expiredKeys = new ArrayList<>();
            for (Map.Entry<String, CycleRecord> locEntry : playerEntry.getValue().entrySet()) {
                if (nowMs - locEntry.getValue().lastUpdatedMs > timeoutMs) {
                    expiredKeys.add(locEntry.getKey());
                }
            }
            if (!expiredKeys.isEmpty()) {
                toRemove.put(playerEntry.getKey(), expiredKeys);
            }
        }

        if (toRemove.isEmpty()) return;

        // Post removal back to the main thread.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Map.Entry<UUID, List<String>> entry : toRemove.entrySet()) {
                ConcurrentHashMap<String, CycleRecord> playerMap = cycleData.get(entry.getKey());
                if (playerMap == null) continue;
                for (String key : entry.getValue()) {
                    playerMap.remove(key);
                }
                if (playerMap.isEmpty()) {
                    cycleData.remove(entry.getKey());
                }
            }
        });
    }
}
