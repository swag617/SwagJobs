package com.swag.swagjobs.command;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class JobsTabCompleter implements TabCompleter {

    private final SwagJobsPlugin plugin;

    private static final List<String> JOBS_SUBCOMMANDS = Arrays.asList(
            "select",
            "progress",
            "prestige",
            "shop",
            "shopedit",
            "top",
            "help",
            "reload",
            "debug",
            "investigate"
    );

    private static final List<String> DEV_SUBCOMMANDS = Arrays.asList(
            "addpoints",
            "setpoints",
            "addxp",
            "setxp",
            "giveprestige",
            "setprestige",
            "checkcurve",
            "clearlevels",
            "setlevel",
            "addlevel",
            "investigate"
    );

    public JobsTabCompleter(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args == null) return Collections.emptyList();

        if (command.getName().equalsIgnoreCase("jobs")) {
            return completeJobsCommand(args);
        }
        return completeDevCommand(args);
    }

    private List<String> completeJobsCommand(String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return JOBS_SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            if (sub.equals("select") || sub.equals("progress") || sub.equals("top")) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                return Arrays.stream(Job.values())
                        .map(j -> j.getKey().toLowerCase(Locale.ROOT))
                        .filter(name -> name.startsWith(partial))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }
            if (sub.equals("investigate")) {
                return getPlayerNames(args[1]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> completeDevCommand(String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : DEV_SUBCOMMANDS) {
                if (s.startsWith(partial)) out.add(s);
            }
            return out;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            String partialPlayer = args[1].toLowerCase(Locale.ROOT);
            List<String> players = getPlayerNames(args[1]);

            if (sub.equals("clearlevels")) {
                if ("all".startsWith(partialPlayer)) players.add(0, "all");
            }

            return players;
        }

        if (args.length == 3) {
            if (sub.equals("clearlevels")) {
                String partial = args[2].toLowerCase(Locale.ROOT);
                return Arrays.asList("--reset-prestige", "resetprestige").stream()
                        .filter(f -> f.startsWith(partial))
                        .collect(Collectors.toList());
            }

            if (sub.equals("addxp") || sub.equals("setxp") || sub.equals("giveprestige")
                    || sub.equals("setprestige") || sub.equals("setlevel") || sub.equals("addlevel")
                    || sub.equals("checkcurve")) {

                String partialJob = args[2].toLowerCase(Locale.ROOT);
                return Arrays.stream(Job.values())
                        .flatMap(j -> Arrays.stream(new String[]{
                                j.name().toLowerCase(Locale.ROOT),
                                j.getKey().toLowerCase(Locale.ROOT)
                        }))
                        .distinct()
                        .filter(n -> n.startsWith(partialJob))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 4) {
            if (sub.equals("addpoints") || sub.equals("setpoints")
                    || sub.equals("addxp") || sub.equals("setxp")) {
                return Arrays.asList("1", "10", "50", "100", "500", "1000");
            }

            if (sub.equals("setlevel") || sub.equals("addlevel")) {
                int maxLevel = 100;
                try { maxLevel = plugin.getJobsConfig().getMaxLevel(); } catch (Exception ignored) { }
                List<String> nums = new ArrayList<>();
                nums.add("1");
                nums.add(String.valueOf(Math.max(1, maxLevel / 4)));
                nums.add(String.valueOf(Math.max(1, maxLevel / 2)));
                nums.add(String.valueOf(Math.max(1, (3 * maxLevel) / 4)));
                nums.add(String.valueOf(maxLevel));
                return nums.stream().distinct().collect(Collectors.toList());
            }

            if (sub.equals("setprestige")) {
                int maxPrestige = 10;
                try { maxPrestige = plugin.getJobsConfig().getMaxPrestige(); } catch (Exception ignored) { }
                List<String> nums = new ArrayList<>();
                for (int i = 0; i <= maxPrestige; i++) nums.add(String.valueOf(i));
                return nums;
            }

            if (sub.equals("checkcurve")) {
                int maxLevel = 100;
                try { maxLevel = plugin.getJobsConfig().getMaxLevel(); } catch (Exception ignored) { }
                return Arrays.asList("1", "10", "25", "50", "75", String.valueOf(maxLevel));
            }

            if (sub.equals("giveprestige")) {
                return Arrays.asList("1", "2", "3", "5");
            }
        }

        if (args.length == 5) {
            if (sub.equals("checkcurve")) {
                int maxPrestige = 10;
                try { maxPrestige = plugin.getJobsConfig().getMaxPrestige(); } catch (Exception ignored) { }
                List<String> nums = new ArrayList<>();
                for (int i = 0; i <= maxPrestige; i++) nums.add(String.valueOf(i));
                return nums;
            }
        }

        return Collections.emptyList();
    }

    private List<String> getPlayerNames(String partial) {
        String lower = partial.toLowerCase(Locale.ROOT);
        List<String> players = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(p -> players.add(p.getName()));
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && !players.contains(op.getName())) {
                players.add(op.getName());
            }
        }
        return players.stream()
                .filter(n -> n != null && n.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }
}
