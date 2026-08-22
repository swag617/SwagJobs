package com.swag.swagjobs.util;

import com.swag.swagjobs.SwagJobsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class MessageUtil {
    // Default self-identifying prefix, used as the fallback for SwagAPI's IPrefixService.
    // An admin can override this per-plugin (or globally, for every plugin) from the
    // SwagAPI web panel; with nothing configured, this literal fallback is used unchanged.
    private static final String DEFAULT_PREFIX = "&8[&dSwagJobs&8] &r";
    // Bare bracket-tag fallback (no surrounding color) for call sites that already supply
    // their own leading legacy color code before the tag, e.g. "§c" + getBracketTag() + " ...".
    private static final String DEFAULT_BRACKET_TAG = "[SwagJobs]";

    private static final java.util.regex.Pattern LEGACY_HEX =
            java.util.regex.Pattern.compile("(?i)[&§]#([0-9a-f]{6})");
    private static final java.util.regex.Pattern LEGACY_CODE =
            java.util.regex.Pattern.compile("(?i)[&§]([0-9a-fk-or])");
    private static final java.util.Map<Character, String> LEGACY_TAGS = java.util.Map.ofEntries(
            java.util.Map.entry('0', "<black>"), java.util.Map.entry('1', "<dark_blue>"), java.util.Map.entry('2', "<dark_green>"),
            java.util.Map.entry('3', "<dark_aqua>"), java.util.Map.entry('4', "<dark_red>"), java.util.Map.entry('5', "<dark_purple>"),
            java.util.Map.entry('6', "<gold>"), java.util.Map.entry('7', "<gray>"), java.util.Map.entry('8', "<dark_gray>"),
            java.util.Map.entry('9', "<blue>"), java.util.Map.entry('a', "<green>"), java.util.Map.entry('b', "<aqua>"),
            java.util.Map.entry('c', "<red>"), java.util.Map.entry('d', "<light_purple>"), java.util.Map.entry('e', "<yellow>"),
            java.util.Map.entry('f', "<white>"), java.util.Map.entry('k', "<obfuscated>"), java.util.Map.entry('l', "<bold>"),
            java.util.Map.entry('m', "<strikethrough>"), java.util.Map.entry('n', "<underlined>"), java.util.Map.entry('o', "<italic>"),
            java.util.Map.entry('r', "<reset>"));

    public static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Renders a stored prefix value — which, since it comes from SwagAPI's IPrefixService, may
     * be MiniMessage tags (admin-typed via the web panel), legacy {@code &}/{@code §} codes (this
     * plugin's own fallback constants), or a mix — into a legacy §-coded string safe for this
     * plugin's {@code ChatColor}/{@code sendMessage(String)} pipeline. Without this, a
     * MiniMessage-tag override (e.g. {@code <gold>[FleaMC] </gold>}) would show its literal tag
     * text instead of rendering, since {@link #color} only understands {@code &} codes.
     */
    private static String toLegacy(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        var hex = LEGACY_HEX.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (hex.find()) hex.appendReplacement(sb, "<#" + hex.group(1) + ">");
        hex.appendTail(sb);
        raw = sb.toString();

        var code = LEGACY_CODE.matcher(raw);
        StringBuilder sb2 = new StringBuilder();
        while (code.find()) {
            String tag = LEGACY_TAGS.get(Character.toLowerCase(code.group(1).charAt(0)));
            code.appendReplacement(sb2, tag != null ? java.util.regex.Matcher.quoteReplacement(tag) : "");
        }
        code.appendTail(sb2);

        var component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(sb2.toString());
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(component);
    }

    /**
     * Resolves this plugin's self-identifying chat prefix, honoring a SwagAPI
     * IPrefixService override (per-plugin or global) if one is hooked and configured.
     * Resolved fresh on every call so an admin's live panel change takes effect
     * immediately, without a plugin restart.
     */
    private static String getPrefix() {
        SwagJobsPlugin plugin = SwagJobsPlugin.getInstance();
        com.SwagDev.SwagAPI.api.IPrefixService prefixService =
                (plugin != null) ? plugin.getPrefixService() : null;
        String prefix = (prefixService != null)
                ? prefixService.getPrefix("SwagJobs", DEFAULT_PREFIX)
                : DEFAULT_PREFIX;
        return toLegacy(prefix);
    }

    /**
     * Resolves just the bare "[SwagJobs]" bracket tag (no surrounding color), honoring the
     * same SwagAPI IPrefixService override as {@link #getPrefix()}. Intended for call sites
     * that supply their own leading legacy color code immediately before the tag, e.g.
     * {@code "§c" + MessageUtil.getBracketTag() + " Invalid rarity..."}.
     */
    public static String getBracketTag() {
        SwagJobsPlugin plugin = SwagJobsPlugin.getInstance();
        com.SwagDev.SwagAPI.api.IPrefixService prefixService =
                (plugin != null) ? plugin.getPrefixService() : null;
        String tag = (prefixService != null)
                ? prefixService.getPrefix("SwagJobs", DEFAULT_BRACKET_TAG)
                : DEFAULT_BRACKET_TAG;
        return toLegacy(tag);
    }

    public static void sendLevelUp(Player player, String jobName, int newLevel) {
        String title = color("&a&lLEVEL UP!");
        String subtitle = color("&7" + jobName + " Level &f" + newLevel);
        player.sendTitle(title, subtitle, 10, 40, 10);
        player.sendMessage(getPrefix() + color("&7You reached &d" + jobName + " Level " + newLevel + "&7!"));
    }
}