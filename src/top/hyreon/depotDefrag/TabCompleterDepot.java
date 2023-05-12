package top.hyreon.depotDefrag;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TabCompleterDepot implements TabCompleter {

    DepotDefragPlugin plugin;

    public TabCompleterDepot(DepotDefragPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String lbl, @NotNull String[] args) {

        boolean isPlayer = commandSender instanceof Player;

        boolean depotExists = !plugin.depots.isEmpty();
        boolean ownsDepot = depotExists && !isPlayer || plugin.depots.values().stream().anyMatch(x -> x.owners.contains(((Player)commandSender).getUniqueId()));

        if (args.length == 1) {
            List<String> outputs = new ArrayList<>();
            if (DepotFinder.isSearching(commandSender)) {
                outputs.add("cancel");
            }
            outputs.add("help");
            outputs.add("new");
            if (depotExists) {
                outputs.add("list");
                outputs.add("owner");
            }
            if (ownsDepot) {
                outputs.add("update");
                outputs.add("count");
                outputs.add("find");
                outputs.add("defrag");
                outputs.add("delete");
                outputs.add("share");
                outputs.add("evac");
                outputs.add("verify");
                outputs.add("sync");
            }
            return outputs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equals("update") || args[0].equals("count") || args[0].equals("find") || args[0].equals("defrag")
                || args[0].equals("delete") || args[0].equals("verify") || args[0].equals("share") || args[0].equals("evac"))) {
            if (isPlayer) {
                return plugin.depots.keySet().stream().filter(s -> s.startsWith(args[1]))
                        .filter(x -> plugin.depots.get(x).owners.contains(((Player)commandSender).getUniqueId()))
                        .collect(Collectors.toList());
            } else {
                return plugin.depots.keySet().stream().filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equals("new")) {
            if (plugin.getSecure()) {
                return IntStream.rangeClosed(0, plugin.getSafeDistance()).mapToObj(Integer::toString).filter(s -> s.startsWith(args[2])).collect(Collectors.toList());
            } else {
                return IntStream.rangeClosed(0, plugin.getMaxDistance() - 1).mapToObj(Integer::toString).collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equals("find")) {
            return Arrays.stream(Material.values()).map(Enum::name).filter(s -> s.startsWith(args[2].toUpperCase())).collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equals("defrag")) {
            List<String> outputs = new ArrayList<>();
            outputs.add("correct");
            outputs.add("group");
            outputs.add("precisegroup");
            outputs.add("combine");
            outputs.add("sort");
            return outputs.stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equals("sync")) {
            List<String> outputs = new ArrayList<>();
            outputs.add("container");
            outputs.add("distance");
            outputs.add("both");
            return outputs.stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 3 && (args[0].equals("share") || args[0].equals("evac"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.startsWith(args[2])).collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
