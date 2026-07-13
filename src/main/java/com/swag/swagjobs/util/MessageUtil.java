package com.swag.swagjobs.util;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class MessageUtil {
    private static final String PREFIX = ChatColor.translateAlternateColorCodes('&', "&8[&dSwagJobs&8] &r");

    public static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static void sendLevelUp(Player player, String jobName, int newLevel) {
        String title = color("&a&lLEVEL UP!");
        String subtitle = color("&7" + jobName + " Level &f" + newLevel);
        player.sendTitle(title, subtitle, 10, 40, 10);
        player.sendMessage(PREFIX + color("&7You reached &d" + jobName + " Level " + newLevel + "&7!"));
    }
}