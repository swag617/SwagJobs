package com.swag.swagjobs.util;

import org.bukkit.entity.Player;

public class PermissionUtil {

    public static String getPlayerRank(Player player) {
        if (player.hasPermission("SwagJobs.cap.flea")) return "flea";
        if (player.hasPermission("SwagJobs.cap.lizard")) return "lizard";
        if (player.hasPermission("SwagJobs.cap.axolotl")) return "axolotl";
        return "member";
    }

    public static int getSmelterCap(Player player) {
        if (player.hasPermission("SwagJobs.cap.flea")) return 25;
        if (player.hasPermission("SwagJobs.cap.lizard")) return 20;
        if (player.hasPermission("SwagJobs.cap.axolotl")) return 15;
        return 10; // Default Member cap
    }

    public static double getRankXPMultiplier(Player player) {
        if (player.hasPermission("SwagJobs.multiplier.flea")) return 1.20;
        if (player.hasPermission("SwagJobs.multiplier.lizard")) return 1.15;
        if (player.hasPermission("SwagJobs.multiplier.axolotl")) return 1.10;
        return 1.0; // Default Member multiplier
    }
}