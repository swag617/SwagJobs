package com.swag.swagjobs.manager;

import com.swag.swagjobs.SwagJobsPlugin;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * CheatDetectionManager
 *
 * - Existing macro/consistency detection preserved.
 * - Adds place-break cycle tracking with a configurable grace (default = 3 cycles).
 * - Exposes canGainXp(player, location) for other systems to check before awarding XP.
 * - Provides runtime setters to update the max cycles and tracked materials (persisted to config).
 */
public class CheatDetectionManager implements Listener {
    private final SwagJobsPlugin plugin;
    private final Map<UUID, List<Long>> actionIntervals = new HashMap<>();
    private final Map<UUID, Long> lastActionTime = new HashMap<>();
    private final Set<UUID> recentlyFlagged = new HashSet<>();

    private int sampleSize;
    private double minVariance;
    private int maxActionsPerSecond;
    private long flagCooldownMs;

    // New config-backed controls (existing)
    private boolean enabled;
    private double minCps;
    private double maxCps;
    private long alertCooldownMs;

    private boolean aePresent = false;
    private Object aeApiInstance = null; // Will hold AEAPI instance via reflection

    // --- Place-break exploit tracking ---
    private final Map<UUID, Map<String, PlaceBreakInfo>> exploitMap = new HashMap<>();
    private final Set<Material> trackedMaterials = new HashSet<>();
    private int maxAllowedCycles; // e.g., 3
    private long placeBreakTimeoutMs; // e.g., 120000 (2 minutes)

    // Scheduled cleanup task id
    private int cleanupTaskId = -1;

    public CheatDetectionManager(SwagJobsPlugin plugin) {
        this.plugin = plugin;

        // Old settings
        this.sampleSize = plugin.getConfig().getInt("anticheat.sample-size", 20);
        this.minVariance = plugin.getConfig().getDouble("anticheat.min-variance", 25.0);
        this.maxActionsPerSecond = plugin.getConfig().getInt("anticheat.max-actions-per-second", 15);
        this.flagCooldownMs = plugin.getConfig().getLong("anticheat.flag-cooldown-ms", 60000L);

        // New anti-cheat settings
        this.enabled = plugin.getConfig().getBoolean("anticheat.enabled", true);
        this.minCps = plugin.getConfig().getDouble("anticheat.min-cps", 18.0);
        this.maxCps = plugin.getConfig().getDouble("anticheat.max-cps", 100.0);
        this.alertCooldownMs = plugin.getConfig().getLong("anticheat.alert-cooldown-seconds", 5) * 1000L;

        // Place/break specific settings
        this.maxAllowedCycles = plugin.getConfig().getInt("anticheat.place-break-max-cycles", 3);
        int timeoutSeconds = plugin.getConfig().getInt("anticheat.place-break-timeout-seconds", 120);
        this.placeBreakTimeoutMs = Math.max(5, timeoutSeconds) * 1000L;

        List<String> configured = plugin.getConfig().getStringList("anticheat.track-exploit-blocks");
        if (configured == null || configured.isEmpty()) {
            // sensible defaults: melons and pumpkins (you can change in config)
            configured = Arrays.asList("MELON_BLOCK", "PUMPKIN", "MELON");
        }
        for (String s : configured) {
            if (s == null) continue;
            Material m = Material.matchMaterial(s.trim().toUpperCase());
            if (m != null) trackedMaterials.add(m);
        }

        // Detect AdvancedEnchantments plugin
        Plugin aePlugin = plugin.getServer().getPluginManager().getPlugin("AdvancedEnchantments");
        if (aePlugin != null && aePlugin.isEnabled()) {
            try {
                Class<?> aeApiClass = Class.forName("net.advancedplugins.ae.api.AEAPI");
                aeApiInstance = aeApiClass.getMethod("getInstance").invoke(null);
                aePresent = true;
                plugin.getLogger().info("AdvancedEnchantments detected: AEAPI integration enabled.");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to hook into AdvancedEnchantments AEAPI: " + e.getMessage());
                aePresent = false;
            }
        }

        // Register event listeners
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Periodic cleanup to avoid memory leaks
        startCleanupTask();
    }

    private void startCleanupTask() {
        if (cleanupTaskId != -1) return;
        cleanupTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            synchronized (exploitMap) {
                Iterator<Map.Entry<UUID, Map<String, PlaceBreakInfo>>> it = exploitMap.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, Map<String, PlaceBreakInfo>> e = it.next();
                    Map<String, PlaceBreakInfo> inner = e.getValue();
                    inner.entrySet().removeIf(ent -> (now - ent.getValue().lastTimestamp) > placeBreakTimeoutMs);
                    if (inner.isEmpty()) it.remove();
                }
            }
        }, 20L * 60L, 20L * 60L).getTaskId();
    }

    // Shutdown cleanup (call when plugin disables)
    public void stopCleanupTask() {
        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId);
            cleanupTaskId = -1;
        }
    }

    // --- Existing macro detection/recording preserved ---
    public void recordAction(Player player) {
        // Respect master toggle
        if (!enabled) return;

        // Early exclusions (enchants/tools that legitimately cause rapid actions)
        if (isActionExcluded(player)) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (lastActionTime.containsKey(uuid)) {
            long interval = now - lastActionTime.get(uuid);
            if (interval <= 0) interval = 1;

            List<Long> intervals = actionIntervals.computeIfAbsent(uuid, k -> new ArrayList<>());
            intervals.add(interval);

            if (intervals.size() >= sampleSize) {
                checkConsistency(player, intervals);
                intervals.clear();
            }
        }

        lastActionTime.put(uuid, now);
    }

    private boolean isActionExcluded(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return false;

        String displayName = item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName().toLowerCase() : "";

        List<String> ignoreEnchants = plugin.getConfig().getStringList("anticheat.ignore-enchants");
        List<String> ignoreAeEnchants = plugin.getConfig().getStringList("anticheat.ignore-ae-enchants");

        // Check display name
        if (displayName != null) {
            for (String term : ignoreEnchants) {
                if (term == null) continue;
                term = term.toLowerCase();
                if (!term.isEmpty() && displayName.contains(term)) return true;
            }
        }

        // Check lore
        if (item.getItemMeta().hasLore()) {
            for (String line : item.getItemMeta().getLore()) {
                String l = ChatColor.stripColor(line).toLowerCase();
                for (String term : ignoreEnchants) {
                    if (term == null) continue;
                    term = term.toLowerCase();
                    if (!term.isEmpty() && l.contains(term)) return true;
                }
            }
        }

        // Check vanilla enchantments (by key/name)
        try {
            for (org.bukkit.enchantments.Enchantment ench : item.getEnchantments().keySet()) {
                String enchName = ench.getKey().getKey().toLowerCase();
                for (String term : ignoreEnchants) {
                    if (term == null) continue;
                    if (!term.isEmpty() && enchName.contains(term.toLowerCase())) return true;
                }
            }
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
            // Older APIs may not expose getKey(); ignore safely
        }

        // Check AdvancedEnchantments enchants if AE is present
        if (aePresent && aeApiInstance != null) {
            try {
                Class<?> aeApiClass = aeApiInstance.getClass();
                java.lang.reflect.Method getEnchantmentsMethod = aeApiClass.getMethod("getEnchantments", ItemStack.class);
                @SuppressWarnings("unchecked")
                Map<String, Integer> aeEnchants = (Map<String, Integer>) getEnchantmentsMethod.invoke(aeApiInstance, item);

                if (aeEnchants != null) {
                    for (String enchKey : aeEnchants.keySet()) {
                        for (String term : ignoreAeEnchants) {
                            if (term == null) continue;
                            term = term.toLowerCase();
                            if (!term.isEmpty() && enchKey.toLowerCase().contains(term)) return true;
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error checking AE enchants: " + e.getMessage());
            }
        }

        return false;
    }

    private void checkConsistency(Player player, List<Long> intervals) {
        if (intervals.size() < sampleSize) return;

        double sum = 0;
        for (long val : intervals) sum += val;
        double mean = sum / intervals.size();

        double sqSum = 0;
        for (long val : intervals) sqSum += Math.pow(val - mean, 2);

        double variance = sqSum / intervals.size();

        if (variance < minVariance) {
            flagPlayer(player, "Macro Detected (Consistency: " + String.format("%.2f", variance) + ")");
        }
    }

    public void flagPlayer(Player player, String reason) {
        UUID uuid = player.getUniqueId();
        if (recentlyFlagged.contains(uuid)) return;

        recentlyFlagged.add(uuid);

        long delay = (alertCooldownMs > 0) ? alertCooldownMs : flagCooldownMs;
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentlyFlagged.remove(uuid), delay / 50);

        // Console Log
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[SwagJobs-AC] " + player.getName() + " flagged: " + reason);

        // Clickable Message for Admins
        TextComponent message = new TextComponent(ChatColor.translateAlternateColorCodes('&',
                "&8[&cSwagJobs-AC&8] &7Player &f" + player.getName() + " &7flagged: &c" + reason + " "));

        TextComponent action = new TextComponent(ChatColor.YELLOW + "[INVESTIGATE]");
        action.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/SwagJobsdev investigate " + player.getName()));
        action.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GRAY + "Teleport and Vanish to check player")));

        message.addExtra(action);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("SwagJobs.admin")) {
                online.spigot().sendMessage(message);
            }
        }
    }

    public void clearData(UUID uuid) {
        actionIntervals.remove(uuid);
        lastActionTime.remove(uuid);
        recentlyFlagged.remove(uuid);
        synchronized (exploitMap) {
            exploitMap.remove(uuid);
        }
    }

    public void cleanupPlayer(UUID uuid) {
        actionIntervals.remove(uuid);
        lastActionTime.remove(uuid);
        recentlyFlagged.remove(uuid);
        synchronized (exploitMap) {
            exploitMap.remove(uuid);
        }
    }

    public void testFlag(Player player) {
        flagPlayer(player, "TEST FLAG (Manual Trigger)");
    }

    // --- Place-break data structure ---
    private static class PlaceBreakInfo {
        long lastTimestamp;
        int cycles;        // number of place->break cycles observed
        boolean lastWasPlace;

        PlaceBreakInfo(long now, boolean lastWasPlace) {
            this.lastTimestamp = now;
            this.cycles = 0;
            this.lastWasPlace = lastWasPlace;
        }
    }

    private String locKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    // Event: Block placed by player -> record as placed
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        Material mat = block.getType();

        if (!shouldTrackMaterial(mat)) return;

        UUID uuid = player.getUniqueId();
        String key = locKey(block.getLocation());
        long now = System.currentTimeMillis();

        synchronized (exploitMap) {
            Map<String, PlaceBreakInfo> playerMap = exploitMap.computeIfAbsent(uuid, k -> new HashMap<>());
            PlaceBreakInfo info = playerMap.get(key);
            if (info == null || now - info.lastTimestamp > placeBreakTimeoutMs) {
                info = new PlaceBreakInfo(now, true);
                playerMap.put(key, info);
            } else {
                info.lastWasPlace = true;
                info.lastTimestamp = now;
                // don't change cycles here; cycles increment on break after place
            }
        }

        // mark block owner with metadata so other players breaking don't increment cycles
        block.setMetadata("SwagJobs_placed_by", new org.bukkit.metadata.FixedMetadataValue(plugin, player.getUniqueId().toString()));
    }

    // Event: Block broken by player -> check if it was recently placed by same player and update cycles
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material mat = block.getType();

        if (!shouldTrackMaterial(mat)) return;

        UUID uuid = player.getUniqueId();
        String key = locKey(block.getLocation());
        long now = System.currentTimeMillis();

        boolean exceeded = false;
        synchronized (exploitMap) {
            Map<String, PlaceBreakInfo> playerMap = exploitMap.computeIfAbsent(uuid, k -> new HashMap<>());
            PlaceBreakInfo info = playerMap.get(key);

            // If there was metadata showing a different player placed it, ignore and remove metadata
            if (block.hasMetadata("SwagJobs_placed_by")) {
                List<org.bukkit.metadata.MetadataValue> vals = block.getMetadata("SwagJobs_placed_by");
                if (vals != null && !vals.isEmpty()) {
                    String owner = vals.get(0).asString();
                    if (!owner.equals(uuid.toString())) {
                        // Not the same player who is breaking it - clear any per-player entry for this loc
                        playerMap.remove(key);
                        block.removeMetadata("SwagJobs_placed_by", plugin);
                        // Natural/broken by other player; treat as natural break (do not increment cycles)
                        return;
                    } else {
                        // Same player broke a block they placed -> increment logic below
                    }
                }
            }

            if (info == null || now - info.lastTimestamp > placeBreakTimeoutMs) {
                // We either never saw a place or timeout expired. Start fresh as a natural break.
                info = new PlaceBreakInfo(now, false);
                playerMap.put(key, info);
            }

            // If last action was a place by this player, then this break is a place->break cycle
            if (info.lastWasPlace) {
                info.cycles++;
            } else {
                // natural break -> reset cycles
                info.cycles = 0;
            }

            info.lastWasPlace = false;
            info.lastTimestamp = now;

            if (info.cycles > maxAllowedCycles) {
                exceeded = true;
            }
        }

        // Remove metadata on break
        if (block.hasMetadata("SwagJobs_placed_by")) {
            block.removeMetadata("SwagJobs_placed_by", plugin);
        }

        // CHANGED: Silent console logging instead of player message
        if (exceeded) {
            if (!recentlyFlagged.contains(uuid)) {
                recentlyFlagged.add(uuid);
                Bukkit.getScheduler().runTaskLater(plugin, () -> recentlyFlagged.remove(uuid), Math.max(1, plugin.getConfig().getLong("anticheat.flag-cooldown-ms", 60000L)) / 50);

                // Console logging with full details (NO player message)
                String blockType = block.getType().name();
                String worldName = block.getWorld().getName();
                int x = block.getX();
                int y = block.getY();
                int z = block.getZ();

                plugin.getLogger().warning(String.format(
                        "[ANTI-EXPLOIT] %s exceeded place-break cycles: %s at %s (%d, %d, %d)",
                        player.getName(), blockType, worldName, x, y, z
                ));

                // Optional: Notify online admins with permission (configurable)
                boolean notifyAdmins = plugin.getConfig().getBoolean("anticheat.alert-admins", false);
                String adminPerm = plugin.getConfig().getString("anticheat.admin-permission", "SwagJobs.admin.alerts");

                if (notifyAdmins) {
                    String alertMsg = ChatColor.DARK_RED + "[Anti-Exploit] " + ChatColor.GRAY +
                            player.getName() + ChatColor.RED + " exceeded place-break on " +
                            ChatColor.YELLOW + blockType;

                    for (Player admin : Bukkit.getOnlinePlayers()) {
                        if (admin.hasPermission(adminPerm)) {
                            admin.sendMessage(alertMsg);
                        }
                    }
                }
            }
        }

        // Note: We do NOT directly block XP here because awarding is done in your job listeners.
        // They must consult canGainXp(player, block.getLocation()) before awarding.
    }

    private boolean shouldTrackMaterial(Material m) {
        if (trackedMaterials.isEmpty()) return false; // explicit opt-in recommended
        return trackedMaterials.contains(m);
    }

    /**
     * Public method for job listeners to check before awarding XP.
     * Returns true if XP should be awarded for this player/location.
     */
    public boolean canGainXp(Player player, Location loc) {
        if (!enabled) return true; // anti-cheat disabled -> allow XP

        UUID uuid = player.getUniqueId();
        String key = locKey(loc);
        synchronized (exploitMap) {
            Map<String, PlaceBreakInfo> playerMap = exploitMap.get(uuid);
            if (playerMap == null) return true;
            PlaceBreakInfo info = playerMap.get(key);
            if (info == null) return true;

            long now = System.currentTimeMillis();
            if (now - info.lastTimestamp > placeBreakTimeoutMs) {
                // expired -> allow and cleanup
                playerMap.remove(key);
                if (playerMap.isEmpty()) exploitMap.remove(uuid);
                return true;
            }

            // If cycles exceed maxAllowedCycles, deny XP
            return info.cycles <= maxAllowedCycles;
        }
    }

    // Optional helper to forcibly reset place-break info for a player/location (admin command)
    public void resetPlaceBreakFor(Player player, Location loc) {
        UUID uuid = player.getUniqueId();
        String key = locKey(loc);
        synchronized (exploitMap) {
            Map<String, PlaceBreakInfo> playerMap = exploitMap.get(uuid);
            if (playerMap != null) {
                playerMap.remove(key);
                if (playerMap.isEmpty()) exploitMap.remove(uuid);
            }
        }
    }

    // ---------- Runtime config mutation API (used by DevCommand) ----------
    public int getMaxAllowedCycles() {
        return maxAllowedCycles;
    }

    public void setMaxAllowedCycles(int maxAllowedCycles) {
        this.maxAllowedCycles = Math.max(0, maxAllowedCycles);
        plugin.getConfig().set("anticheat.place-break-max-cycles", this.maxAllowedCycles);
        plugin.saveConfig();
    }

    public Set<String> listTrackedMaterials() {
        Set<String> names = new HashSet<>();
        synchronized (trackedMaterials) {
            for (Material m : trackedMaterials) {
                if (m != null) names.add(m.name());
            }
        }
        return names;
    }

    public boolean addTrackedMaterial(String materialName) {
        if (materialName == null) return false;
        Material m = Material.matchMaterial(materialName.trim().toUpperCase());
        if (m == null) return false;
        boolean added;
        synchronized (trackedMaterials) {
            added = trackedMaterials.add(m);
        }
        // persist
        persistTrackedMaterials();
        return added;
    }

    public boolean removeTrackedMaterial(String materialName) {
        if (materialName == null) return false;
        Material m = Material.matchMaterial(materialName.trim().toUpperCase());
        if (m == null) return false;
        boolean removed;
        synchronized (trackedMaterials) {
            removed = trackedMaterials.remove(m);
        }
        persistTrackedMaterials();
        return removed;
    }

    private void persistTrackedMaterials() {
        List<String> list = new ArrayList<>();
        synchronized (trackedMaterials) {
            for (Material m : trackedMaterials) {
                list.add(m.name());
            }
        }
        plugin.getConfig().set("anticheat.track-exploit-blocks", list);
        plugin.saveConfig();
    }
}