package com.swag.swagjobs.integrations;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

/**
 * AdvancedEnchantments (AE) soft integration via reflection — no compile-time
 * dependency on AE's jar.
 *
 * <p>Extracted from the old {@code CheatDetectionManager} (removed), which used
 * this purely as an anti-cheat exemption check (a player holding an item with
 * a configured AE enchant was skipped from macro/CPS flagging). That specific
 * anti-cheat system is gone, but the AE detection/query capability itself is
 * reusable, so it lives here as a standalone hook — nothing calls
 * {@link #getEnchantments(ItemStack)} yet; wire it into whatever feature
 * needs to react to AE enchantments.
 */
public class AdvancedEnchantmentsIntegration {

    private boolean enabled = false;
    private Object aeApiInstance = null;
    private final Logger logger;

    public AdvancedEnchantmentsIntegration(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
        var aePlugin = plugin.getServer().getPluginManager().getPlugin("AdvancedEnchantments");
        if (aePlugin == null || !aePlugin.isEnabled()) {
            logger.info("AdvancedEnchantments not found — AE enchant checks disabled.");
            return;
        }
        try {
            Class<?> aeApiClass = Class.forName("net.advancedplugins.ae.api.AEAPI");
            aeApiInstance = aeApiClass.getMethod("getInstance").invoke(null);
            enabled = true;
            logger.info("AdvancedEnchantments detected: AEAPI integration enabled.");
        } catch (Exception e) {
            logger.warning("Failed to hook into AdvancedEnchantments AEAPI: " + e.getMessage());
            enabled = false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the AE enchantments on this item (name -&gt; level), or an empty
     * map if AE isn't installed, the item has none, or the lookup failed.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Integer> getEnchantments(ItemStack item) {
        if (!enabled || item == null) return Collections.emptyMap();
        try {
            Method getEnchantmentsMethod = aeApiInstance.getClass().getMethod("getEnchantments", ItemStack.class);
            Map<String, Integer> result = (Map<String, Integer>) getEnchantmentsMethod.invoke(aeApiInstance, item);
            return result != null ? result : Collections.emptyMap();
        } catch (Exception e) {
            logger.warning("Error checking AE enchants: " + e.getMessage());
            return Collections.emptyMap();
        }
    }
}
