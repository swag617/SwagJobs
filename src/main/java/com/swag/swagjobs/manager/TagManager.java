package com.swag.swagjobs.manager;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages automatic tag granting for job prestiges
 * Integrates with FleaTags plugin via reflection (no compile-time dependency)
 * Uses creative FleaMC purple gradients with hex colors
 */
public class TagManager {

    private final SwagJobsPlugin plugin;
    private Object fleaTagsPlugin; // Will hold TagPlugin instance via reflection
    private boolean fleaTagsAvailable = false;
    private Class<?> tagTypeEnum; // Store the TagType enum class

    // Map of Job -> Prestige Level -> Tag Suffix
    private final Map<Job, Map<Integer, String>> prestigeTagMap = new HashMap<>();

    public TagManager(SwagJobsPlugin plugin) {
        this.plugin = plugin;
        initializeFleaTags();
        setupPrestigeTagMappings();
    }

    /**
     * Initialize FleaTags plugin hook via reflection
     */
    private void initializeFleaTags() {
        try {
            Plugin fleaTagsPlugin = Bukkit.getPluginManager().getPlugin("FleaTags");
            if (fleaTagsPlugin != null && fleaTagsPlugin.isEnabled()) {
                this.fleaTagsPlugin = fleaTagsPlugin;

                // Load the TagType enum class
                this.tagTypeEnum = Class.forName("com.swag.fleatags.models.Tag$TagType");

                this.fleaTagsAvailable = true;
                plugin.getLogger().info("FleaTags plugin hooked successfully for tag management!");
            } else {
                plugin.getLogger().warning("FleaTags plugin not found! Tag system will not work.");
                plugin.getLogger().warning("Make sure FleaTags is installed and loaded before SwagJobs.");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to hook into FleaTags: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Setup tag mappings for each job and prestige milestone
     * Uses creative FleaMC purple/blue gradients with hex colors
     */
    private void setupPrestigeTagMappings() {
        for (Job job : Job.values()) {
            Map<Integer, String> prestigeMap = new HashMap<>();

            String emoji = getJobEmoji(job);
            String jobName = job.getName();

            // Prestige 1-4 = Novice - Blue to Aqua gradient
            // Creates a cool → warm blue progression
            for (int i = 1; i <= 4; i++) {
                prestigeMap.put(i, "&#5DADE2" + emoji + " <blue>" + jobName + "</blue> <aqua>Novice</aqua>");
            }

            // Prestige 5-9 = Expert - Purple to Pink gradient
            // Signature FleaMC purple with pink accent
            for (int i = 5; i <= 9; i++) {
                prestigeMap.put(i, "&#A855F7" + emoji + " <light_purple>" + jobName + "</light_purple> &#EC4899Expert");
            }

            // Prestige 10 = Master - Deep Purple to Bright Magenta gradient
            // Royal deep purple with vibrant magenta for legendary prestige
            prestigeMap.put(10, "&#7C3AED" + emoji + " <dark_purple>" + jobName + "</dark_purple> &#E879F9Master");

            prestigeTagMap.put(job, prestigeMap);
        }
    }

    /**
     * Get emoji for each job
     */
    private String getJobEmoji(Job job) {
        switch (job) {
            case MINER: return "⛏";
            case LUMBERJACK: return "🪓";
            case FARMER: return "🌾";
            case FISHER: return "🎣";
            case HUNTER: return "🏹";
            case BUILDER: return "🏗";
            case ENCHANTER: return "✨";
            case SMELTER: return "🔥";
            case BREWER: return "🧪";
            case CRAFTER: return "🛠";
            default: return "✦";
        }
    }

    /**
     * Called when a player prestiges - grants appropriate tag
     *
     * @param player The player who prestiged
     * @param job The job they prestiged in
     * @param newPrestige The new prestige level (1-10)
     */
    public void handlePrestigeTag(Player player, Job job, int newPrestige) {
        if (!fleaTagsAvailable) {
            plugin.getLogger().warning("Cannot grant tag - FleaTags not available!");
            return;
        }

        // Only grant tags at milestones: 1, 5, 10
        if (newPrestige != 1 && newPrestige != 5 && newPrestige != 10) {
            return;
        }

        String tagSuffix = prestigeTagMap.get(job).get(newPrestige);
        if (tagSuffix == null) {
            plugin.getLogger().warning("No tag suffix found for " + job + " prestige " + newPrestige);
            return;
        }

        try {
            UUID playerUUID = player.getUniqueId();

            // Check if player already owns this exact tag
            if (playerOwnsTagWithSuffix(playerUUID, tagSuffix)) {
                plugin.getLogger().info(player.getName() + " already owns this tag, skipping creation.");
                return;
            }

            // Remove old tier tags for this job (so they only have current tier)
            removeOldJobTags(player, job, newPrestige);

            // Create and give the new tag using reflection
            Object newTag = createTagForPlayer(playerUUID, tagSuffix);

            if (newTag != null) {
                plugin.getLogger().info("Created tag '" + tagSuffix + "' for " + player.getName());

                // Notify player
                String tagTier = getTierName(newPrestige);
                String jobName = ChatColor.translateAlternateColorCodes('&', job.getDisplayName());

                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
                player.sendMessage("");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&d&l✦ NEW TAG UNLOCKED! &d&l✦"));
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&7You unlocked the &d" + jobName + " " + tagTier + " &7tag!"));
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&7Use &e/tag &7to equip your new tag!"));
                player.sendMessage("");
            } else {
                plugin.getLogger().warning("Failed to create tag for " + player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error granting tag: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create a tag for a player using FleaTags API via reflection
     * FIXED: Better handling of TagType enum
     */
    private Object createTagForPlayer(UUID playerUUID, String suffix) {
        try {
            Class<?> tagPluginClass = fleaTagsPlugin.getClass();

            // Get the PRESET enum value
            Object presetType = Enum.valueOf((Class<Enum>) tagTypeEnum, "PRESET");

            // Try to find the method with the correct signature
            Method createMethod = null;
            try {
                // Try direct method lookup first
                createMethod = tagPluginClass.getDeclaredMethod("createTagForPlayer", UUID.class, String.class, tagTypeEnum);
            } catch (NoSuchMethodException e) {
                // If that fails, iterate through all methods to find it
                for (Method method : tagPluginClass.getDeclaredMethods()) {
                    if (method.getName().equals("createTagForPlayer") && method.getParameterCount() == 3) {
                        Class<?>[] paramTypes = method.getParameterTypes();
                        if (paramTypes[0] == UUID.class &&
                                paramTypes[1] == String.class &&
                                paramTypes[2].isEnum()) {
                            createMethod = method;
                            break;
                        }
                    }
                }
            }

            if (createMethod == null) {
                plugin.getLogger().severe("Could not find createTagForPlayer method!");
                return null;
            }

            return createMethod.invoke(fleaTagsPlugin, playerUUID, suffix, presetType);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create tag via reflection: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Check if player owns a tag with specific suffix
     */
    private boolean playerOwnsTagWithSuffix(UUID playerUUID, String suffix) {
        try {
            Class<?> tagPluginClass = fleaTagsPlugin.getClass();
            Method checkMethod = tagPluginClass.getMethod("playerOwnsTagWithSuffix", UUID.class, String.class);
            Object result = checkMethod.invoke(fleaTagsPlugin, playerUUID, suffix);
            return (Boolean) result;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check tag ownership: " + e.getMessage());
            return false;
        }
    }

    /**
     * Delete a player's tag by suffix
     */
    private void deletePlayerTagBySuffix(UUID playerUUID, String suffix) {
        try {
            // Get player tags
            Class<?> tagPluginClass = fleaTagsPlugin.getClass();
            Method getTagsMethod = tagPluginClass.getMethod("getPlayerTags", UUID.class);
            Object tagsListObj = getTagsMethod.invoke(fleaTagsPlugin, playerUUID);

            if (tagsListObj instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> tagsList = (java.util.List<Object>) tagsListObj;

                for (Object tag : tagsList) {
                    // Get suffix from tag
                    Method getSuffixMethod = tag.getClass().getMethod("getSuffix");
                    String tagSuffix = (String) getSuffixMethod.invoke(tag);

                    if (tagSuffix.equals(suffix)) {
                        // Get tag ID
                        Method getIdMethod = tag.getClass().getMethod("getId");
                        String tagId = (String) getIdMethod.invoke(tag);

                        // Delete tag
                        Method deleteMethod = tagPluginClass.getMethod("deleteTag", UUID.class, String.class, boolean.class);
                        deleteMethod.invoke(fleaTagsPlugin, playerUUID, tagId, false);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to delete tag: " + e.getMessage());
        }
    }

    /**
     * Remove old tag tiers for a specific job
     * (e.g., when getting Expert, remove Novice)
     */
    private void removeOldJobTags(Player player, Job job, int currentPrestige) {
        UUID playerUUID = player.getUniqueId();

        // Get the old tags to remove
        Map<Integer, String> jobTags = prestigeTagMap.get(job);

        // If they're getting Expert (P5), remove Novice (P1)
        if (currentPrestige == 5) {
            String noviceTag = jobTags.get(1);
            if (noviceTag != null) {
                deletePlayerTagBySuffix(playerUUID, noviceTag);
            }
        }

        // If they're getting Master (P10), remove Expert (P5)
        if (currentPrestige == 10) {
            String expertTag = jobTags.get(5);
            if (expertTag != null) {
                deletePlayerTagBySuffix(playerUUID, expertTag);
            }
        }
    }

    /**
     * Manually grant a preset tag to a player (for admin command)
     *
     * @param targetUuid Target player's UUID
     * @param job The job tag to grant
     * @param tier The tier (novice, expert, master)
     * @return true if successful
     */
    public boolean grantPresetTag(UUID targetUuid, Job job, String tier) {
        if (!fleaTagsAvailable) {
            return false;
        }

        // Determine prestige level from tier
        int prestigeLevel;
        switch (tier.toLowerCase()) {
            case "novice": prestigeLevel = 1; break;
            case "expert": prestigeLevel = 5; break;
            case "master": prestigeLevel = 10; break;
            default: return false;
        }

        String tagSuffix = prestigeTagMap.get(job).get(prestigeLevel);
        if (tagSuffix == null) return false;

        try {
            // Check if player already owns this tag
            if (playerOwnsTagWithSuffix(targetUuid, tagSuffix)) {
                return false;  // Already owns it
            }

            // Create and give the tag
            Object newTag = createTagForPlayer(targetUuid, tagSuffix);
            return newTag != null;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to grant preset tag: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get tier name from prestige number
     */
    private String getTierName(int prestige) {
        if (prestige >= 1 && prestige <= 4) return "Novice";
        if (prestige >= 5 && prestige <= 9) return "Expert";
        if (prestige == 10) return "Master";
        return "Unknown";
    }

    /**
     * Get all available job names for tab completion
     */
    public String[] getJobNames() {
        Job[] jobs = Job.values();
        String[] names = new String[jobs.length];
        for (int i = 0; i < jobs.length; i++) {
            names[i] = jobs[i].name().toLowerCase();
        }
        return names;
    }

    /**
     * Get available tiers
     */
    public String[] getTierNames() {
        return new String[]{"novice", "expert", "master"};
    }

    /**
     * Parse job from string
     */
    public Job parseJob(String jobName) {
        try {
            return Job.valueOf(jobName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}