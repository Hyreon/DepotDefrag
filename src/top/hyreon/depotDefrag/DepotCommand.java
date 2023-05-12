package top.hyreon.depotDefrag;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

public class DepotCommand implements CommandExecutor {

    DepotDefragPlugin plugin;
    LanguageLoader lloader;
    Set<Material> transparentBlocks;

    public DepotCommand(DepotDefragPlugin depotDefragPlugin) {

        this.plugin = depotDefragPlugin;
        lloader = plugin.getLanguageLoader();

        transparentBlocks = Arrays.stream(Material.values())
                .filter(o -> !o.isOccluding() && !plugin.getChestMaterials().contains(o))
                .collect(Collectors.toSet());

    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String lbl, String[] args) {

        if (args.length == 0) subCmdHelp(sender, command, lbl, args);
        else {
            String[] newArgs = Arrays.copyOfRange(args, 1, args.length);
            switch (args[0]) {
                case "help":
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> subCmdHelp(sender, command, lbl, newArgs));
                    return true;
                case "new":
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> subCmdMakeNew(sender, command, lbl, newArgs));
                    return true;
                case "count":
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> subCmdGetCount(sender, command, lbl, newArgs));
                    return true;
                case "find":
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> subCmdFindItem(sender, command, lbl, newArgs));
                    return true;
                case "cancel":
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> subCmdCancel(sender, command, lbl, newArgs));
                    return true;
                case "defrag":
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> subCmdDefrag(sender, command, lbl, newArgs));

                    return true;
                case "delete":
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> subCmdDeleteDepot(sender, command, lbl, newArgs));
                    return true;
                case "list":
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> subCmdListDepot(sender, command, lbl, newArgs));
                    return true;
                case "update":
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> subCmdUpdateDepot(sender, command, lbl, newArgs));
                    return true;
                case "owner":
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> subCmdOwnerDepot(sender, command, lbl, newArgs));
                    return true;
                case "share":
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> subCmdShareDepot(sender, command, lbl, newArgs));
                    return true;
                case "evac":
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> subCmdEvacDepot(sender, command, lbl, newArgs));
                    return true;
                case "verify":
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> subCmdVerifyDepot(sender, command, lbl, newArgs));
                    return true;
                case "sync":
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> subCmdSyncDepot(sender, command, lbl, newArgs));
                    return true;
                default:
                    sender.sendMessage(lloader.get("bad_command"));
                    return true;
            }
        }

        return true;
    }

    private boolean subCmdOwnerDepot(CommandSender sender, Command command, String lbl, String[] args) {

        if (!sender.hasPermission("depot.owner")) {
            sender.sendMessage(lloader.get("need_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(lloader.get("need_name"));
            return true;
        }
        String id = args[0];
        Depot depot = plugin.depots.get(id);
        if (depot == null) {
            sender.sendMessage(lloader.get("no_depot"));
            return true;
        }

        for (UUID uuid : depot.owners) {
            sender.sendMessage(String.format(lloader.get("string"), Bukkit.getOfflinePlayer(uuid).getName()));
        }
        return true;
    }

    private boolean subCmdEvacDepot(CommandSender sender, Command command, String lbl, String[] args) {

        if (!sender.hasPermission("depot.evac.owner")) {
            sender.sendMessage(lloader.get("need_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(lloader.get("need_name"));
            return true;
        }
        String id = args[0];
        Depot depot = plugin.depots.get(id);
        if (depot == null) {
            sender.sendMessage(lloader.get("no_depot"));
            return true;
        }

        if (sender instanceof Player && !sender.hasPermission("depot.evac.any") && !depot.owners.contains(((Player) sender).getUniqueId())) {
            sender.sendMessage(lloader.get("need_ownership"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(lloader.get("need_player"));
            return true;
        }
        String playerName = args[1];
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        if (!player.hasPlayedBefore()) {
            sender.sendMessage(String.format(lloader.get("invalid_player"), playerName));
            return true;
        }

        if (depot.owners.size() == 1 && depot.owners.contains(player.getUniqueId()) && !sender.hasPermission("depot.evac.self")) {
            sender.sendMessage(lloader.get("evac_last"));
            return true;
        }

        if (depot.owners.remove(player.getUniqueId())) {
            sender.sendMessage(String.format(lloader.get("evac_success"), playerName, id));
            if (player.isOnline()) {
                player.getPlayer().sendMessage(String.format(lloader.get("evac_you"), sender.getName(), id));
            }
        } else {
            sender.sendMessage(String.format(lloader.get("evac_fail"), playerName, id));
        }
        return true;
    }

    private boolean subCmdShareDepot(CommandSender sender, Command command, String lbl, String[] args) {

        if (!sender.hasPermission("depot.share.owner")) {
            sender.sendMessage(lloader.get("need_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(lloader.get("need_name"));
            return true;
        }
        String id = args[0];
        Depot depot = plugin.depots.get(id);
        if (depot == null) {
            sender.sendMessage(lloader.get("no_depot"));
            return true;
        }

        if (sender instanceof Player && !sender.hasPermission("depot.share.any") && !depot.owners.contains(((Player) sender).getUniqueId())) {
            sender.sendMessage(lloader.get("need_ownership"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(lloader.get("need_player"));
            return true;
        }
        String playerName = args[1];
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        if (!player.hasPlayedBefore()) {
            sender.sendMessage(String.format(lloader.get("invalid_player"), playerName));
            return true;
        }

        if (depot.owners.add(player.getUniqueId())) {
            sender.sendMessage(String.format(lloader.get("share_success"), playerName, id));
            if (player.isOnline()) {
                player.getPlayer().sendMessage(String.format(lloader.get("share_you"), sender.getName(), id));
            }
        } else {
            sender.sendMessage(String.format(lloader.get("share_fail"), playerName, id));
        }
        return true;

    }

    private boolean subCmdListDepot(CommandSender sender, Command command, String lbl, String[] args) {

        if (!sender.hasPermission("depot.list")) {
            sender.sendMessage(lloader.get("need_permission"));
            return true;
        }

        if (plugin.depots.keySet().isEmpty()) {
            sender.sendMessage(lloader.get("no_depots"));
        } else {
            boolean ownedOnly = args.length > 0 && args[0].equals("owned");
            for (String id : plugin.depots.keySet()) {
                int numChests = plugin.depots.get(id).numChests();
                if (sender instanceof Player && plugin.depots.get(id).owners.contains(((Player) sender).getUniqueId())) {
                    sender.sendMessage(String.format(lloader.get("list_depot"), lloader.get("depot_owned"), id, numChests));
                } else {
                    if (ownedOnly) continue;
                    sender.sendMessage(String.format(lloader.get("list_depot"), lloader.get("depot_unowned"), id, numChests));
                }
            }
        }
        return true;
    }

    private boolean subCmdHelp(CommandSender sender, Command command, String lbl, String[] args) {
        sender.sendMessage(lloader.get("help_commands"));
        return true;
    }

    private boolean subCmdCancel(CommandSender sender, Command command, String lbl, String[] args) {

        boolean searchCanceled = DepotFinder.cancel(sender);
        List<ProgressTracker> tasksCanceled = ProgressTracker.cancel(sender);
        if (searchCanceled || !tasksCanceled.isEmpty()) {
            sender.sendMessage(lloader.get("find_canceled"));
        } else {
            sender.sendMessage(lloader.get("no_finds"));
        }
        return true;
    }

    private boolean subCmdDeleteDepot(CommandSender sender, Command command, String lbl, String[] args) {

        if (!sender.hasPermission("depot.delete.owner")) {
            sender.sendMessage(lloader.get("need_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(lloader.get("need_name"));
            return true;
        }
        String id = args[0];

        Depot depot = plugin.depots.get(id);
        if (depot == null) {
            sender.sendMessage(lloader.get("no_depot"));
            return true;
        }

        if (sender instanceof Player && !sender.hasPermission("depot.delete.any") && !depot.owners.contains(((Player) sender).getUniqueId())) {
            sender.sendMessage(lloader.get("need_ownership"));
            return true;
        }

        plugin.removeDepot(id);
        sender.sendMessage(lloader.get("depot_deleted"));
        return true;
    }

    private boolean subCmdDefrag(CommandSender sender, Command command, String lbl, String[] args) {

        if (!sender.hasPermission("depot.defrag.owner")) {
            sender.sendMessage(lloader.get("need_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(lloader.get("need_name"));
            return true;
        }
        String id = args[0];

        Depot depot = plugin.depots.get(id);
        if (depot == null) {
            sender.sendMessage(lloader.get("no_depot"));
            return true;
        }

        if (sender instanceof Player && !sender.hasPermission("depot.defrag.any") && !depot.owners.contains(((Player) sender).getUniqueId())) {
            sender.sendMessage(lloader.get("need_ownership"));
            return true;
        }

        if (!sender.hasPermission("depot.defrag.any_range") && sender instanceof Player && !depot.within((Player) sender, plugin.getDefragDistance())) {
            sender.sendMessage(String.format(lloader.get("too_far"), plugin.getDefragDistance()));
            return true;
        }

        String mode;
        if (args.length < 2) mode = "default";
        else mode = args[1];

        Set<Location> blocksAffected = new HashSet<>(depot.chestLocations);
        Bukkit.getScheduler().callSyncMethod(plugin, () -> plugin.lock(blocksAffected));
        try {

            DefragReport report = null;
            if (mode.equals("simple")) {
                report = depot.simpleDefrag();
                if (report.chestsModified.isEmpty()) {
                    sender.sendMessage(lloader.get("defrag_no_change"));
                } else {
                    sender.sendMessage(String.format(lloader.get("fake_defrag_report"), report.stacksSwappedCount, report.chestsModified.size()));
                }
            } else if (mode.equals("correct")) { //simple, but combines stacks
                report = depot.lightDefrag(sender, false);
                if (report.chestsModified.isEmpty()) {
                    sender.sendMessage(lloader.get("defrag_no_change"));
                } else {
                    sender.sendMessage(String.format(lloader.get("light_defrag_report"), report.stacksSwappedCount, report.stacksFreedCount, report.chestsModified.size()));
                }
            } else if (mode.equals("group")) { //just orders items in each chest
                report = depot.groupDefrag(sender, false);
                if (report.chestsModified.isEmpty()) {
                    sender.sendMessage(lloader.get("defrag_no_change"));
                } else {
                    sender.sendMessage(String.format(lloader.get("order_defrag_report"), report.stacksSwappedCount, report.chestsModified.size()));
                }
            } else if (mode.equals("precisegroup")) { //just orders items in each chest
                report = depot.preciseGroupDefrag(sender, false);
                if (report.chestsModified.isEmpty()) {
                    sender.sendMessage(lloader.get("defrag_no_change"));
                } else {
                    sender.sendMessage(String.format(lloader.get("order_defrag_report"), report.stacksSwappedCount, report.chestsModified.size()));
                }
            } else if (mode.equals("combine")) { //just combines items within each chest
                report = depot.combineDefrag(sender, false);
                if (report.chestsModified.isEmpty()) {
                    sender.sendMessage(lloader.get("defrag_no_change"));
                } else {
                    sender.sendMessage(String.format(lloader.get("combine_defrag_report"), report.stacksSwappedCount, report.stacksFreedCount, report.chestsModified.size()));
                }
            } else if (mode.equals("default") || mode.equals("sort")) { //all of the above at once: combine, correct, group.
                report = depot.safeDefrag(sender);
                if (report.chestsModified.isEmpty()) {
                    sender.sendMessage(lloader.get("defrag_no_change"));
                } else {
                    sender.sendMessage(String.format(lloader.get("default_defrag_report"), report.stacksSwappedCount, report.stacksFreedCount, report.chestsModified.size()));
                }
            } else {
                sender.sendMessage(lloader.get("unsupported_defrag"));
            }
            if (report != null && !report.isComplete()) {
                sender.sendMessage(lloader.get("defrag_incomplete"));
            }
        } catch (CancellationException e) {
            return true;
        } finally {
            plugin.lockedBlocks.remove(blocksAffected);
        }

        //TODO full defrag - safe defrag, and then items swap location to ENSURE they are in the same chest
        // - for every material:
        // - rank chests by how many of the material are there, with top having the most.
        // - move items from bottom into items in the top, filling stacks where possible and the nearest empty slot where not.
        // - rank remaining stacks by how many of the material are there.
        // - move items from the smallest stacks into the largest stacks, if they can be stacked.
        // - if there are NO stacks or free spaces available, find the stacks that are the closest to fitting in fewer chests (by number, not percent)
        // - find the materials that can move, prioritizing ones with few stacks. if it needs as many (or more) stacks than the destination, and moving it hurts the situation, stop.
        // - swap.
        // - repeat the last 3 steps for ALL MATERIALS that are not in a single chest (or, if abundant, as few chests as possible).

        //TODO clean defrag - simply rearranges each chest according to some rules.
        // style: simple - items should be numerically adjacent to their own material.
        // style: lines - start stacks from the left side of each chest, not the lowest index.
        // style: scrawl - start stacks from the left OR right side of each chest. or simply fill up the line.
        // style: flood - leave 1 stacks behind, proportional to the amount of the item.
        // style: box - create boxes! if possible.
        // style: vertical - vertical lines!
        // style: rain - vertical scrawl!

        //TODO compact defrag - use as few chests as possible
        // - find the theoretical minimum number of chests (n)
        // - find the n most full chests, and move all items outside of these into these randomly
        // - order materials from most common to least common
        // - find the theoretical minimum number of stacks (m) per material, and sort all by m
        // - find the chests in order that have the most or least items (x) per material
        // - take the 1st most spacious material, fill the chest it used to prefer, and continue until all have been placed
        // - repeat for the next most common material; if it cannot fit in its preferred space, add it to a straggler queue
        // - sort chests by how much free space there is, and drop all stragglers from their queue in that order
        // - highly destructive and volatile, probably won't make it at all

        //TODO fat defrag - use as many chests as possible
        // - the EXACT OPPOSITE of the compact. try to use every chest.
        // - skip the step about emptying the empty chests.
        // - if there is ANYTHING in your preferred space, go to the straggler queue
        // - sort chests by how much free space there is, and drop all stragglers from their queue in that order
        // - highly dubious value, probably won't make it at all
        return true;
    }

    private boolean subCmdFindItem(CommandSender sender, Command command, String lbl, String[] args) {

        if (!sender.hasPermission("depot.find.owner")) {
            sender.sendMessage(lloader.get("need_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(lloader.get("need_name"));
            return true;
        }
        String id = args[0];

        Depot depot = plugin.depots.get(id);
        if (depot == null) {
            sender.sendMessage(lloader.get("no_depot"));
            return true;
        }

        if (sender instanceof Player && !sender.hasPermission("depot.find.any") && !depot.owners.contains(((Player) sender).getUniqueId())) {
            sender.sendMessage(lloader.get("need_ownership"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(lloader.get("need_item"));
            return true;
        }
        String item = args[1];

        Material mat = Material.matchMaterial(item);
        if (mat == null) {
            sender.sendMessage(lloader.get("bad_item"));
            return true;
        }

        if (!sender.hasPermission("depot.find.any_range") && sender instanceof Player && !depot.within((Player) sender, plugin.getFindDistance())) {
            sender.sendMessage(String.format(lloader.get("too_far"), plugin.getFindDistance()));
            return true;
        }

        List<Location> chests = depot.find(sender, mat);
        if (chests.isEmpty()) {
            sender.sendMessage(lloader.get("item_empty"));
            return true;
        }
        if (sender instanceof Player) {
            DepotFinder finder = new DepotFinder(((Player) sender).getUniqueId(), mat, plugin);
            for (Location chest : chests) {
                finder.addLocation(chest);
            }
            sender.sendMessage(String.format(lloader.get("found_item"), chests.size()));
            finder.start();
        } else {
            for (Location chest : chests) {
                sender.sendMessage(depot.track(chest, mat));
            }
        }

        return true;
    }

    private boolean subCmdGetCount(CommandSender sender, Command command, String lbl, String[] args) {

        if (!sender.hasPermission("depot.count.owner")) {
            sender.sendMessage(lloader.get("need_permission"));
            return true;
        }
        
        if (args.length == 0) {
            sender.sendMessage(lloader.get("need_name"));
            return true;
        }
        String id = args[0];

        boolean savingFile = false;
        int page = 0;
        if (args.length > 1) {
            if (args[1].equals("log")) savingFile = true;
            else {
                try {
                    page = Integer.parseInt(args[1]) - 1;
                } catch (NumberFormatException e) {
                    sender.sendMessage(String.format(lloader.get("invalid_number"), args[1]));
                    return true;
                }
            }
        }

        Depot depot = plugin.depots.get(id);
        if (depot == null) {
            sender.sendMessage(lloader.get("no_depot"));
            return true;
        }

        if (sender instanceof Player && !sender.hasPermission("depot.count.any") && !depot.owners.contains(((Player) sender).getUniqueId())) {
            sender.sendMessage(lloader.get("need_ownership"));
            return true;
        }

        if (!sender.hasPermission("depot.count.any_range") && sender instanceof Player && !depot.within((Player) sender, plugin.getCountDistance())) {
            sender.sendMessage(String.format(lloader.get("too_far"), plugin.getCountDistance()));
            return true;
        }

        List<String> fullManifest = plugin.depots.get(id).getManifest(sender, plugin, page == 0);
        if (savingFile) {
            File logDirectory = new File(plugin.getDataFolder() + "/logs/");
            logDirectory.mkdir();
            File logFile = new File(String.format("%s/%s-%s.txt", logDirectory, id, System.currentTimeMillis()));
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(logFile));

                for (String entry : fullManifest) {
                    writer.write(ChatColor.stripColor(entry) + "\n");
                }

                writer.close();
                sender.sendMessage(String.format(lloader.get("log_saved"), logFile.getName()));

            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            List<String> manifestPage;
            if (fullManifest.size() <= 10 * page) {
                if (page == 0) {
                    sender.sendMessage(lloader.get("empty_depot_count"));
                } else {
                    sender.sendMessage(lloader.get("no_page"));
                }
                return true;
            }

            if (fullManifest.size() > 10) {
                sender.sendMessage(String.format(lloader.get("page"), page + 1, fullManifest.size() / 10 + 1));
            }
            if (fullManifest.size() < 10 * page + 9) {
                manifestPage = fullManifest.subList(10 * page, fullManifest.size());
            } else {
                manifestPage = fullManifest.subList(10 * page, 10 * page + 9);
            }
            sender.sendMessage(manifestPage.toArray(String[]::new));
        }
        return true;
    }

    private boolean subCmdMakeNew(CommandSender sender, Command command, String lbl, String[] args) {

        if (!sender.hasPermission("depot.new")) {
            sender.sendMessage(lloader.get("need_permission"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(lloader.get("only_player"));
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            sender.sendMessage(lloader.get("need_name"));
            return true;
        }
        String id = args[0];

        if (plugin.depots.containsKey(id)) {
            sender.sendMessage(lloader.get("depot_exists"));
            return true;
        }

        int distance = 1;
        if (args.length > 1) {
            try {
                distance = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(String.format(lloader.get("invalid_number"), args[1]));
                return true;
            }
        }

        if (!sender.hasPermission("depot.update.extended_range") && distance > plugin.getSafeDistance()) {
            sender.sendMessage(String.format(lloader.get("too_long"), plugin.getSafeDistance()));
            return true;
        } else if (distance >= plugin.getMaxDistance()) {
            sender.sendMessage(String.format(lloader.get("too_long"), plugin.getMaxDistance()));
            return true;
        } else if (distance > plugin.getSafeDistance() && plugin.getSecure()) {
            sender.sendMessage(lloader.get("warning_long"));
            plugin.setSecure(false);
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> plugin.setSecure(true), 200L);
            return true;
        }

        Depot depot;
        try {
            depot = new Depot(player, player.getTargetBlock(transparentBlocks, 20), distance, plugin);
        } catch (CancellationException e) {
            return true; //give up quietly
        }

        if (depot.numChests() == 0) {
            sender.sendMessage(lloader.get("empty_depot"));
            return true;
        }

        plugin.depots.put(id, depot);
        sender.sendMessage(String.format(lloader.get("depot_created"), depot.numChests()));
        return true;
    }

    private boolean subCmdUpdateDepot(CommandSender sender, Command command, String lbl, String[] args) {

        if (!sender.hasPermission("depot.update.owner")) {
            sender.sendMessage(lloader.get("need_permission"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(lloader.get("only_player"));
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            sender.sendMessage(lloader.get("need_name"));
            return true;
        }
        String id = args[0];

        if (!plugin.depots.containsKey(id)) {
            sender.sendMessage(lloader.get("no_depot"));
            return true;
        }
        Depot depot = plugin.depots.get(id);

        if (!sender.hasPermission("depot.update.any") && !depot.owners.contains(((Player) sender).getUniqueId())) {
            sender.sendMessage(lloader.get("need_ownership"));
            return true;
        }

        int distance = 1;
        if (args.length > 1) {
            try {
                distance = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(String.format(lloader.get("invalid_number"), args[1]));
                return true;
            }
        }

        if (distance >= plugin.getMaxDistance()) {
            sender.sendMessage(String.format(lloader.get("too_long"), plugin.getMaxDistance()));
            return true;
        } else if (distance > plugin.getSafeDistance() && plugin.getSecure()) {
            sender.sendMessage(lloader.get("warning_long"));
            plugin.setSecure(false);
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> plugin.setSecure(true), 200L);
            return true;
        }

        boolean subtracting = false;
        if (args.length > 2) {
            subtracting = args[2].equals("subtract")
                    || args[2].equals("-")
                    || args[2].equals("minus")
                    || args[2].equals("remove")
                    || args[2].equals("rm")
                    || args[2].equals("sub");
        }

        int changes;
        try {
            changes = depot.update(player.getTargetBlock(transparentBlocks, 20), distance, subtracting, sender);
        } catch (CancellationException e) {
            return true; //give up quietly
        }

        if (changes == 0) {
            sender.sendMessage(lloader.get("no_change"));
            return true;
        } else if (!subtracting) {
            sender.sendMessage(String.format(lloader.get("change_added"), changes));
        } else {
            sender.sendMessage(String.format(lloader.get("change_subtracted"), changes));
        }

        return true;



    }

    private boolean subCmdVerifyDepot(CommandSender sender, Command command, String lbl, String[] args) {

        if (!sender.hasPermission("depot.verify.owner")) {
            sender.sendMessage(lloader.get("need_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(lloader.get("need_name"));
            return true;
        }
        String id = args[0];

        if (!plugin.depots.containsKey(id)) {
            sender.sendMessage(lloader.get("no_depot"));
            return true;
        }

        Depot depot = plugin.depots.get(id);

        if (sender instanceof Player && !sender.hasPermission("depot.verify.any") && !depot.owners.contains(((Player) sender).getUniqueId())) {
            sender.sendMessage(lloader.get("need_ownership"));
            return true;
        }

        List<Location> badLocations = depot.verifyDistance();
        List<Location> notContainers = depot.verifyChests();

        if (badLocations.isEmpty() && notContainers.isEmpty()) {
            sender.sendMessage(lloader.get("valid"));
        } else {
            List<String> fullManifest = new ArrayList<>();
            if (!notContainers.isEmpty()) {
                fullManifest.add(lloader.get("not_valid_block"));
                fullManifest.addAll(notContainers.stream().map(o -> String.format(lloader.get("not_valid_block_entry"), o.getBlockX(), o.getBlockY(), o.getBlockZ(), o.getBlock().getType().name())).collect(Collectors.toList()));
            }
            if (!badLocations.isEmpty()) {
                fullManifest.add(lloader.get("not_valid_distance"));
                fullManifest.addAll(badLocations.stream().map(o -> String.format(lloader.get("not_valid_distance_entry"), o.getBlockX(), o.getBlockY(), o.getBlockZ())).collect(Collectors.toList()));
            }

            int page = 0;
            if (args.length > 1) {
                try {
                    page = Integer.parseInt(args[1]) - 1;
                } catch (NumberFormatException e) {
                    sender.sendMessage(String.format(lloader.get("invalid_number"), args[1]));
                    return true;
                }
            }

            List<String> manifestPage;
            if (fullManifest.size() <= 10 * page) {
                if (page == 0) {
                    sender.sendMessage(lloader.get("empty_depot_count"));
                } else {
                    sender.sendMessage(lloader.get("no_page"));
                }
                return true;
            }

            if (fullManifest.size() > 10) {
                sender.sendMessage(String.format(lloader.get("page"), page + 1, fullManifest.size() / 10 + 1));
            }
            if (fullManifest.size() < 10 * page + 9) {
                manifestPage = fullManifest.subList(10 * page, fullManifest.size());
            } else {
                manifestPage = fullManifest.subList(10 * page, 10 * page + 9);
            }
            sender.sendMessage(manifestPage.toArray(String[]::new));

            sender.sendMessage(lloader.get("not_valid_fix"));
        }

        return true;

    }

    private boolean subCmdSyncDepot(CommandSender sender, Command command, String lbl, String[] args) {

        if (!sender.hasPermission("depot.sync.owner")) {
            sender.sendMessage(lloader.get("need_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(lloader.get("need_name"));
            return true;
        }
        String id = args[0];

        if (!plugin.depots.containsKey(id)) {
            sender.sendMessage(lloader.get("no_depot"));
            return true;
        }

        Depot depot = plugin.depots.get(id);

        if (sender instanceof Player && !sender.hasPermission("depot.sync.any") && !depot.owners.contains(((Player) sender).getUniqueId())) {
            sender.sendMessage(lloader.get("need_ownership"));
            return true;
        }

        List<Location> badLocations = depot.verifyDistance();
        List<Location> notContainers = depot.verifyChests();

        if (badLocations.isEmpty() && notContainers.isEmpty()) {
            sender.sendMessage(lloader.get("valid"));
        } else {

            String mode = "default";
            if (args.length > 1) {
                mode = args[1];
            }

            if (mode.equals("distance")) {
                if (badLocations.isEmpty()) {
                    sender.sendMessage(lloader.get("partially_valid"));
                } else {
                    depot.remove(badLocations);
                    sender.sendMessage(String.format(lloader.get("synced"), badLocations.size()));
                }
            } else if (mode.equals("container")) {
                if (notContainers.isEmpty()) {
                    sender.sendMessage(lloader.get("partially_valid"));
                } else {
                    depot.remove(notContainers);
                    sender.sendMessage(String.format(lloader.get("synced"), notContainers.size()));
                }
            } else {
                depot.remove(badLocations);
                depot.remove(notContainers);
                sender.sendMessage(String.format(lloader.get("synced"), badLocations.size() + notContainers.size()));
            }
        }

        return true;

    }

}
