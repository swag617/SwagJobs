package com.swag.swagjobs.manager;

import com.swag.swagjobs.SwagJobsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VanishManager {
    private final SwagJobsPlugin plugin;
    private final Set<UUID> vanishedAdmins = new HashSet<>();

    public VanishManager(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    public void setVanished(Player admin, boolean vanish) {
        UUID uuid = admin.getUniqueId();

        if (vanish) {
            if (vanishedAdmins.contains(uuid)) return;

            vanishedAdmins.add(uuid);
            boolean executed = false;
            try {
                executed = admin.performCommand("vanish");
            } catch (Exception ignored) {}

            if (!executed) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.hasPermission("SwagJobs.admin")) {
                        p.hidePlayer(plugin, admin);
                    }
                }
            }
        } else {
            if (!vanishedAdmins.contains(uuid)) return;

            vanishedAdmins.remove(uuid);
            boolean executed = false;
            try {
                executed = admin.performCommand("vanish");
            } catch (Exception ignored) {}

            if (!executed) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showPlayer(plugin, admin);
                }
            }
        }
    }

    public boolean isVanished(UUID uuid) {
        return vanishedAdmins.contains(uuid);
    }
}