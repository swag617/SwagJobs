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
 * Integrates with SwagTags plugin via reflection (no compile-time dependency,
 * since SwagTags is still in active development)
 * Uses purple gradients with hex colors
 */
public class TagManager {

    private final SwagJobsPlugin plugin;
    private Object swagTagsPlugin; // Will hold TagPlugin instance via reflection
    private boolean swagTagsAvailable = false;
    private Class<?> tagTypeEnum; // Store the TagType enum class

    private final Map<Job, Map<Integer, String>> prestigeTagMap = new HashMap<>();

    public TagManager(SwagJobsPlugin plugin) {
        this.plugin = plugin;
        initializeSwagTags();
        setupPrestigeTagMappings();
    }

    private void initializeSwagTags() {
        try {
            Plugin swagTagsPlugin = Bukkit.getPluginManager().getPlugin("SwagTags");
            if (swagTagsPlugin != null && swagTagsPlugin.isEnabled()) {
                this.swagTagsPlugin = swagTagsPlugin;
                this.tagTypeEnum = Class.forName("com.swag.swagtags.models.Tag$TagType");

                this.swagTagsAvailable = true;
                plugin.getLogger().info("SwagTags plugin hooked successfully for tag management!");
            } else {
                plugin.getLogger().warning("SwagTags plugin not found! Tag system will not work.");
                plugin.getLogger().warning("Make sure SwagTags is installed and loaded before SwagJobs.");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to hook into SwagTags: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupPrestigeTagMappings() {
        for (Job job : Job.values()) {
            Map<Integer, String> prestigeMap = new HashMap<>();

            String emoji = getJobEmoji(job);
            String jobName = job.getName();

            // Prestige 1-4: Novice (blue → aqua)
            for (int i = 1; i <= 4; i++) {
                prestigeMap.put(i, "&#5DADE2" + emoji + " <blue>" + jobName + "</blue> <aqua>Novice</aqua>");
            }

            // Prestige 5-9: Expert (purple → pink)
            for (int i = 5; i <= 9; i++) {
                prestigeMap.put(i, "&#A855F7" + emoji + " <light_purple>" + jobName + "</light_purple> &#EC4899Expert");
            }

            // Prestige 10: Master (deep purple → magenta)
            prestigeMap.put(10, "&#7C3AED" + emoji + " <dark_purple>" + jobName + "</dark_purple> &#E879F9Master");

            prestigeTagMap.put(job, prestigeMap);
        }
    }

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

    public void handlePrestigeTag(Player player, Job job, int newPrestige) {
        if (!swagTagsAvailable) {
            plugin.getLogger().warning("Cannot grant tag - SwagTags not available!");
            return;
        }

        // Tags only granted at milestone prestiges: 1, 5, 10
        if (newPrestige != 1 && newPrestige != 5 && newPrestige != 10) {
            return;
        }

        Map<Integer, String> jobTags = prestigeTagMap.get(job);
        if (jobTags == null) {
            plugin.getLogger().warning("No prestige tags configured for job: " + job.name());
            return;
        }
        String tagSuffix = jobTags.get(newPrestige);
        if (tagSuffix == null) {
            plugin.getLogger().warning("No tag suffix found for " + job + " prestige " + newPrestige);
            return;
        }

        try {
            UUID playerUUID = player.getUniqueId();

            if (playerOwnsTagWithSuffix(playerUUID, tagSuffix)) {
                plugin.getLogger().info(player.getName() + " already owns this tag, skipping creation.");
                return;
            }

            removeOldJobTags(player, job, newPrestige);

            Object newTag = createTagForPlayer(playerUUID, tagSuffix);

            if (newTag != null) {
                plugin.getLogger().info("Created tag '" + tagSuffix + "' for " + player.getName());

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

    @SuppressWarnings("unchecked")
    private Object createTagForPlayer(UUID playerUUID, String suffix) {
        try {
            Class<?> tagPluginClass = swagTagsPlugin.getClass();

            if (tagTypeEnum == null || !tagTypeEnum.isEnum()) {
                plugin.getLogger().warning("TagType is not an enum — skipping PRESET check");
                return null;
            }
            Object presetType = Enum.valueOf((Class<Enum>) tagTypeEnum, "PRESET");

            Method createMethod = null;
            try {
                createMethod = tagPluginClass.getDeclaredMethod("createTagForPlayer", UUID.class, String.class, tagTypeEnum);
            } catch (NoSuchMethodException e) {
                // Fall back to scanning declared methods for a matching signature
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

            return createMethod.invoke(swagTagsPlugin, playerUUID, suffix, presetType);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create tag via reflection: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private boolean playerOwnsTagWithSuffix(UUID playerUUID, String suffix) {
        try {
            Class<?> tagPluginClass = swagTagsPlugin.getClass();
            Method checkMethod = tagPluginClass.getMethod("playerOwnsTagWithSuffix", UUID.class, String.class);
            Object result = checkMethod.invoke(swagTagsPlugin, playerUUID, suffix);
            return (Boolean) result;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check tag ownership: " + e.getMessage());
            return false;
        }
    }

    private void deletePlayerTagBySuffix(UUID playerUUID, String suffix) {
        try {
            Class<?> tagPluginClass = swagTagsPlugin.getClass();
            Method getTagsMethod = tagPluginClass.getMethod("getPlayerTags", UUID.class);
            Object tagsListObj = getTagsMethod.invoke(swagTagsPlugin, playerUUID);

            if (tagsListObj instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> tagsList = (java.util.List<Object>) tagsListObj;

                for (Object tag : tagsList) {
                    Method getSuffixMethod = tag.getClass().getMethod("getSuffix");
                    String tagSuffix = (String) getSuffixMethod.invoke(tag);

                    if (tagSuffix.equals(suffix)) {
                        Method getIdMethod = tag.getClass().getMethod("getId");
                        String tagId = (String) getIdMethod.invoke(tag);

                        Method deleteMethod = tagPluginClass.getMethod("deleteTag", UUID.class, String.class, boolean.class);
                        deleteMethod.invoke(swagTagsPlugin, playerUUID, tagId, false);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to delete tag: " + e.getMessage());
        }
    }

    private void removeOldJobTags(Player player, Job job, int currentPrestige) {
        UUID playerUUID = player.getUniqueId();
        Map<Integer, String> jobTags = prestigeTagMap.get(job);

        // Remove the lower-tier tag so the player only holds their current tier
        if (currentPrestige == 5) {
            String noviceTag = jobTags.get(1);
            if (noviceTag != null) {
                deletePlayerTagBySuffix(playerUUID, noviceTag);
            }
        }

        if (currentPrestige == 10) {
            String expertTag = jobTags.get(5);
            if (expertTag != null) {
                deletePlayerTagBySuffix(playerUUID, expertTag);
            }
        }
    }

    public boolean grantPresetTag(UUID targetUuid, Job job, String tier) {
        if (!swagTagsAvailable) {
            return false;
        }

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
            if (playerOwnsTagWithSuffix(targetUuid, tagSuffix)) {
                return false;
            }

            Object newTag = createTagForPlayer(targetUuid, tagSuffix);
            return newTag != null;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to grant preset tag: " + e.getMessage());
            return false;
        }
    }

    private String getTierName(int prestige) {
        if (prestige >= 1 && prestige <= 4) return "Novice";
        if (prestige >= 5 && prestige <= 9) return "Expert";
        if (prestige == 10) return "Master";
        return "Unknown";
    }

    public String[] getJobNames() {
        Job[] jobs = Job.values();
        String[] names = new String[jobs.length];
        for (int i = 0; i < jobs.length; i++) {
            names[i] = jobs[i].name().toLowerCase();
        }
        return names;
    }

    public String[] getTierNames() {
        return new String[]{"novice", "expert", "master"};
    }

    public Job parseJob(String jobName) {
        try {
            return Job.valueOf(jobName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}