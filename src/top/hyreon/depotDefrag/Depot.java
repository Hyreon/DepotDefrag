package top.hyreon.depotDefrag;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Depot {

    DepotDefragPlugin plugin;

    List<Location> chestLocations;
    List<Inventory> cache;
    List<String> manifest = null;
    Set<UUID> owners = new HashSet<>();

    public Depot(Player player, Block targetBlock, int distance, DepotDefragPlugin plugin) {

        this.plugin = plugin;

        owners.add(player.getUniqueId());
        chestLocations = produceChests(targetBlock, distance, player);

    }

    public Depot(Set<UUID> owners, List<Location> locations, DepotDefragPlugin plugin) {

        this.plugin = plugin;

        chestLocations = locations;
        this.owners = owners;

    }

    //A sample defrag to get the feet wet.
    public DefragReport simpleDefrag() {

        DefragReport report = new DefragReport();

        updateCache();
        List<ItemStack> items = getItemsFromCache();
        List<Material> materialsToSearch = getMaterialPriorities(items);
        Map<Material, List<Inventory>> materialInventoryOrder = getChestPrioritiesByMaterial(materialsToSearch, cache);

        //perform the sort. if the sort completes, we need to sort again - there may be more progress to be made.
        //limited number of cycles, to avoid infinite loops of back-and-forth 'sorting'.
        int cycles;
        for (cycles = 0; simpleDefragCycle(report, materialInventoryOrder) && cycles < 10; cycles++) {
            report.addCycle();
        }
        if (cycles < 10) report.markComplete(); //last cycle had no changes

        return report;

    }

    public DefragReport lightDefrag(CommandSender sender, boolean skipUpdate) {

        DefragReport report = new DefragReport();

        if (!skipUpdate) updateCache(sender);
        List<ItemStack> items = getItemsFromCache();
        List<Material> materialsToSearch = getMaterialPriorities(items);
        Map<Material, List<Inventory>> materialInventoryOrder = getChestPrioritiesByMaterial(materialsToSearch, cache);

        int chestsPerCycle = materialInventoryOrder.values().stream().mapToInt(List::size).sum();
        ProgressTracker tracker = new ProgressTracker("task_light", sender, plugin.getLanguageLoader(), plugin.getLoadTime(), plugin.getCancelTime(), chestsPerCycle);

        //perform the sort. if the sort completes, we need to sort again - there may be more progress to be made.
        //limited number of cycles, to avoid infinite loops of back-and-forth 'sorting'.
        int cycles;
        for (cycles = 0; lightDefragCycle(report, tracker, materialInventoryOrder) && cycles < 10; cycles++) {
            report.addCycle();
        }
        if (cycles < 10) report.markComplete(); //last cycle had no changes

        return report;

    }

    //probably the only one that should be called 'defrag'
    public DefragReport groupDefrag(CommandSender sender, boolean skipUpdate) {

        DefragReport report = new DefragReport();
        report.markComplete();

        if (!skipUpdate) updateCache(sender);

        ProgressTracker tracker = new ProgressTracker("task_order", sender, plugin.getLanguageLoader(), plugin.getLoadTime(), plugin.getCancelTime(), cache.size());

        //perform the sort. if the sort completes, we need to sort again - there may be more progress to be made.
        //limited number of cycles, to avoid infinite loops of back-and-forth 'sorting'.
        for (Inventory inv : cache) {

            tracker.markProgress(1);

            Set<Material> materialsCached = new HashSet<>();
            materialsCached.add(null);

            int cycles;
            for (cycles = 0; materialsCached.add(orderDefragCycle(report, inv, materialsCached)) && cycles < 54; cycles++) {
                report.addCycle();
            }

            if (cycles >= 54) report.lockFromCompletion();

        }

        return report;

    }

    //probably the only one that should be called 'defrag'
    public DefragReport preciseGroupDefrag(CommandSender sender, boolean skipUpdate) {

        DefragReport report = new DefragReport();

        if (!skipUpdate) updateCache(sender);

        ProgressTracker tracker = new ProgressTracker("task_order_precise", sender, plugin.getLanguageLoader(), plugin.getLoadTime(), plugin.getCancelTime(), cache.size());

        //perform the sort. if the sort completes, we need to sort again - there may be more progress to be made.
        //limited number of cycles, to avoid infinite loops of back-and-forth 'sorting'.
        for (Inventory inv : cache) {

            tracker.markProgress(1);

            Set<ItemStack> unitsCached = new HashSet<>();
            unitsCached.add(null);

            int secondCycles;
            for (secondCycles = 0; unitsCached.add(superOrderDefragCycle(report, inv, unitsCached)) && secondCycles < 54; secondCycles++) {
                report.addCycle();
            }

            if (secondCycles < 54) report.markComplete();

        }

        if (cache.isEmpty()) report.markComplete();

        return report;

    }

    public DefragReport combineDefrag(CommandSender sender, boolean skipUpdate) {

        DefragReport report = new DefragReport();

        if (!skipUpdate) updateCache(sender);

        ProgressTracker tracker = new ProgressTracker("task_combine", sender, plugin.getLanguageLoader(), plugin.getLoadTime(), plugin.getCancelTime(), cache.size());

        //perform the sort. if the sort completes, we need to sort again - there may be more progress to be made.
        //limited number of cycles, to avoid infinite loops of back-and-forth 'sorting'.
        for (Inventory inv : cache) {

            tracker.markProgress(1);

            Map<ItemStack, List<Integer>> unitDestinations = new HashMap<>();

            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);

                if (item == null) continue; //no point
                if (item.getMaxStackSize() == 1) continue; //no room for progress here

                ItemStack unit = getUnit(item);
                if (item.getAmount() == item.getMaxStackSize()
                        && unitDestinations.get(unit) == null) continue; //no room for progress here either

                unitDestinations.putIfAbsent(unit, new ArrayList<>());
                List<Integer> slots = unitDestinations.get(unit);
                slots.add(i);

                while (inv.getItem(i) != null //still stuff to put away
                        && !slots.isEmpty() //still a place to put it away
                        && slots.get(0) != i) { //that place is not its original location
                    ItemStack destinationOfItem = inv.getItem(slots.get(0)); //the itemstack to combine with
                    if (destinationOfItem == null) {
                        inv.clear(i);
                        inv.setItem(slots.get(0), item);
                        report.addTransaction(inv.getLocation(), inv.getLocation(), false);
                    } else {
                        assert destinationOfItem.isSimilar(item);
                        int spaceLeft = destinationOfItem.getMaxStackSize() - destinationOfItem.getAmount();

                        if (spaceLeft >= item.getAmount()) { //plenty of space to add to the stack
                            destinationOfItem.setAmount(destinationOfItem.getAmount() + item.getAmount());
                            inv.clear(i);
                            report.addTransaction(inv.getLocation(), inv.getLocation(), true);
                        } else {
                            destinationOfItem.setAmount(destinationOfItem.getMaxStackSize());
                            item.setAmount(item.getAmount() - spaceLeft);
                            inv.setItem(i, item);
                            report.addTransaction(inv.getLocation(), inv.getLocation(), false);
                        }
                        inv.setItem(slots.get(0), destinationOfItem);
                        if (destinationOfItem.getAmount() == destinationOfItem.getMaxStackSize()) {
                            slots.remove(0); //full, doesn't count anymore
                        }
                    }
                }
            }

        }

        report.markComplete(); //never fails

        return report;

    }

    public DefragReport safeDefrag(CommandSender sender) {

        DefragReport report = combineDefrag(sender, false);
        report.append(lightDefrag(sender, true));
        report.append(groupDefrag(sender, true));
        report.append(preciseGroupDefrag(sender, true));
        return report;

    }

    private ItemStack getUnit(@Nullable ItemStack item) {
        if (item == null) return null;
        ItemStack unit = item.clone();
        unit.setAmount(1);
        return unit;
    }

    //performs one cycle of the fake defrag.
    //returns whether the cycle was complete.
    private boolean simpleDefragCycle(DefragReport report, Map<Material, List<Inventory>> materialInventoryOrder) {

        boolean changeMade = false;

        for (Material mat : materialInventoryOrder.keySet()) {

            List<Inventory> queue = materialInventoryOrder.get(mat);

            Inventory first;
            Inventory last;
            int internalCycles = 0;
            while (internalCycles++ < 100) {
                first = queue.get(0);
                last = queue.get(queue.size() - 1);
                if (first.equals(last)) break;
                int slotTo = first.firstEmpty();
                if (slotTo == -1) {
                    queue.remove(0);
                    continue;
                }
                int slotFrom = last.first(mat);
                if (slotFrom == -1) {
                    queue.remove(queue.size() - 1);
                    continue;
                }
                first.setItem(slotTo, last.getItem(slotFrom));
                last.clear(slotFrom);
                report.addTransaction(first.getLocation(), last.getLocation(), false);
                changeMade = true;
            }

        }

        return changeMade;

    }

    private boolean lightDefragCycle(DefragReport report, ProgressTracker tracker, Map<Material, List<Inventory>> materialInventoryOrder) {

        boolean changeMade = false;

        for (Material mat : materialInventoryOrder.keySet()) {

            List<Inventory> queue = materialInventoryOrder.get(mat);

            Inventory first;
            Inventory last;
            int internalCycles = 0;
            while (internalCycles++ < 100) {
                first = queue.get(0);
                last = queue.get(queue.size() - 1);
                if (first.equals(last)) {
                    tracker.markProgress(1);
                    break;
                }

                int slotFrom = last.first(mat);
                if (slotFrom == -1) {
                    tracker.markProgress(1);
                    queue.remove(queue.size() - 1);
                    continue;
                }
                ItemStack item = last.getItem(slotFrom);
                assert item != null;

                //find first item stack to combine with
                Map<Integer, ? extends ItemStack> candidateSlots = first.all(mat);
                int slotTo = candidateSlots.keySet().stream().filter(i -> candidateSlots.get(i).getAmount() < mat.getMaxStackSize()).findFirst().orElse(-1);
                int slotToSecondary = first.firstEmpty(); //find first empty slot
                if (slotTo == -1) slotTo = slotToSecondary;
                if (slotTo == -1) { //still nothing
                    tracker.markProgress(1);
                    queue.remove(0);
                    continue;
                }
                boolean success = first.addItem(last.getItem(slotFrom)).isEmpty();
                if (success) last.clear(slotFrom); //clear the item if it was fully drained
                boolean noNewSlot = success && first.firstEmpty() == slotToSecondary; //if the first empty slot is the same, then no new slot was used
                report.addTransaction(first.getLocation(), last.getLocation(), noNewSlot);
                if (!changeMade) {
                    tracker.extendGoalpost();
                    changeMade = true;
                }
            }

        }

        return changeMade;


    }

    private Material orderDefragCycle(DefragReport report, Inventory inv, Collection<Material> materialsCached) {

        Material newMaterial = null;
        List<Integer> incongruities = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null) {
                if (newMaterial != null) incongruities.add(i);
                continue;
            }

            Material thisMaterial = item.getType();
            if (newMaterial == null) {
                if (materialsCached.contains(thisMaterial)) {
                    continue;
                } else {
                    newMaterial = thisMaterial;
                }
            }
            if (thisMaterial != newMaterial) {
                incongruities.add(i);
            } else if (!incongruities.isEmpty()) {
                int destination = incongruities.remove(0); //pop the gap
                inv.setItem(i, inv.getItem(destination)); //swap the items
                inv.setItem(destination, item);
                incongruities.add(i); //if it was an incongruity before, it still is
                report.addTransaction(inv.getLocation(), inv.getLocation(), false);
            }
        }

        return newMaterial;

    }

    private ItemStack superOrderDefragCycle(DefragReport report, Inventory inv, Collection<ItemStack> unitsCached) {

        ItemStack newUnit = null;
        List<Integer> incongruities = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null) {
                if (newUnit != null) incongruities.add(i);
                continue;
            }

            ItemStack unit = getUnit(item);
            if (newUnit == null) {
                if (unitsCached.contains(unit)) {
                    continue;
                } else {
                    newUnit = unit;
                }
            }
            if (!unit.equals(newUnit)) {
                incongruities.add(i);
            } else if (!incongruities.isEmpty()) {
                int destination = incongruities.remove(0); //pop the gap
                inv.setItem(i, inv.getItem(destination)); //swap the items
                inv.setItem(destination, item);
                incongruities.add(i); //if it was an incongruity before, it still is
                report.addTransaction(inv.getLocation(), inv.getLocation(), false);
            }
        }

        return newUnit;

    }

    private List<ItemStack> getItemsFromCache() {
        List<ItemStack> items = new ArrayList<>();
        for (Inventory inv : cache) {
            for (ItemStack item : inv) {
                if (item != null) items.add(item);
            }
        }
        return items;
    }

    private List<Material> getMaterialPriorities(List<ItemStack> items) {
        //unsorted list
        List<Material> materials = items.stream().map(ItemStack::getType).distinct().collect(Collectors.toList());

        List<ValuedObject<Material>> sortedMaterials = new SortedList<>();
        for (Material mat : materials) {
            sortedMaterials.add(new ValuedObject<>(mat, -items.stream().mapToInt(ItemStack::getAmount).sum()));
        }

        //sorted list
        return sortedMaterials.stream().map(i -> i.object).collect(Collectors.toList());
    }

    private Map<Material, List<Inventory>> getChestPrioritiesByMaterial(Collection<Material> materials, Collection<Inventory> cache) {
        Map<Material, List<Inventory>> materialInventoryOrder = new HashMap<>();
        for (Material mat : materials) {

            List<ValuedObject<Inventory>> inventories = new SortedList<>();

            for (Inventory inv : cache) {
                int value = inv.all(mat).values().stream().mapToInt(ItemStack::getAmount).sum();
                if (value == 0) continue; //ignore chests that do not have this material
                ValuedObject<Inventory> valuedInv = new ValuedObject<>(inv, -value);
                inventories.add(valuedInv);
            }

            materialInventoryOrder.put(mat, inventories.stream().map(i -> i.object).collect(Collectors.toList()));

        }
        return materialInventoryOrder;
    }

    public int update(Block targetBlock, int distance, boolean subtracting, CommandSender sender) {

        List<Location> otherSet = produceChests(targetBlock, distance, sender);
        if (!verifyOtherDistance(otherSet)) {
            return 0;
        }

        int changes = 0;

        if (subtracting) {
            for (Location loc : otherSet) {
                if (chestLocations.remove(loc)) changes++;
            }
        } else {
            for (Location loc : otherSet) {
                if (!chestLocations.contains(loc)) {
                    chestLocations.add(loc);
                    changes++;
                }
            }
        }

        return changes;

    }

    private List<Location> produceChests(Block targetBlock, int distance, CommandSender sender) throws CancellationException {

        ProgressTracker tracker = new ProgressTracker("get_chests", sender, plugin.langLoader, plugin.getLoadTime(), plugin.getCancelTime(), 0);

        List<Location> locations = new ArrayList<>();
        List<Location> failedLocations = new ArrayList<>();
        List<Location> targetLocations = new ArrayList<>();

        targetLocations.add(targetBlock.getLocation());
        tracker.extendGoalpost(1);

        while (!targetLocations.isEmpty()) {
            Location targetLocation = targetLocations.remove(0);

            if (failedLocations.contains(targetLocation)) {
                tracker.markProgress(1);
                continue;
            }
            if (locations.contains(targetLocation)) {
                tracker.markProgress(1);
                continue;
            }

            if (plugin.getChestMaterials().contains(targetLocation.getBlock().getType())) {
                tracker.extendGoalpost((int) Math.pow(2 * distance + 1, 3));
                tracker.markProgress(1);
                if (distance > 0 || !plugin.addDoubleChestLocations(locations, targetLocation)) locations.add(targetLocation);
                targetLocations.addAll(adjacentLocations(targetLocation, distance));
            } else {
                tracker.markProgress(1);
                failedLocations.add(targetLocation);
            }
        }

        return locations;

    }

    private List<Location> adjacentLocations(Location location, int distance) {

        List<Location> aLocations = new ArrayList<>();

        for (int x = -distance; x <= distance; x++) {
            for (int y = -distance; y <= distance; y++) {
                for (int z = -distance; z <= distance; z++) {
                    Location newLocation = location.clone().add(x, y, z);
                    aLocations.add(newLocation);
                }
            }
        }

        return aLocations;

    }

    //Is mutable.
    public List<String> getManifest(CommandSender sender, DepotDefragPlugin plugin, boolean forceUpdate) {

        if (!forceUpdate && manifest != null) return manifest;

        List<DepotManifestEntry> depotManifestEntries = new ArrayList<>();

        updateCache(sender);
        for (Inventory inventory : cache) {
            Set<Material> materialsInInventory = new HashSet<>();
            for (ItemStack item : inventory.getStorageContents()) {
                if (item == null) continue;
                DepotManifestEntry existingEntry = depotManifestEntries.stream().filter(d -> d.material == item.getType())
                    .findFirst().orElse(null);

                if (existingEntry == null) {
                    existingEntry = new DepotManifestEntry(item.getType(), 0, 0, 0);
                    depotManifestEntries.add(existingEntry);
                }

                existingEntry.amount += item.getAmount();
                if (item.getAmount() > 0) existingEntry.stacks++;
                if (!materialsInInventory.contains(item.getType())) {
                    materialsInInventory.add(item.getType());
                    existingEntry.chests++;
                }
            }
        }

        depotManifestEntries.sort(Comparator.comparing(DepotManifestEntry::getAmount).reversed());

        manifest = new ArrayList<>();
        for (DepotManifestEntry entry : depotManifestEntries) {
            manifest.add(entry.printForPlugin(plugin));
        }

        return manifest;

    }

    private void updateCache() {
        updateCache(null);
    }

    private void updateCache(CommandSender sender) {
        if (cache == null) cache = new ArrayList<>();
        cache.clear();

        ProgressTracker tracker = new ProgressTracker("update_cache", sender, plugin.getLanguageLoader(), plugin.getLoadTime(), plugin.getCancelTime(), chestLocations.size());

        List<Location> invalidLocations = verifyDistance();
        List<Location> leftChestLocations = new ArrayList<>();
        final int chunkSize = plugin.getUpdateCacheChunkSize();
        int chunks = chestLocations.size() / chunkSize;
        for (int chunk = 0; chunk <= chunks; chunk++) { //allowed to overflow, the sync method will stop at the right time
            final int finalChunk = chunk;
            try {
                Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                    int i;
                    for (i = 0; i < chunkSize && finalChunk * chunkSize + i < chestLocations.size(); i++) {
                        Location location = chestLocations.get(finalChunk * chunkSize + i);
                        if (invalidLocations.contains(location)) {
                            tracker.markProgress(1);
                            continue;
                        }
                        Location leftChestLocation = location;
                        BlockState state = location.getBlock().getState();
                        if (state instanceof Chest) {
                            Chest chest = ((Chest) state);
                            Inventory inv = chest.getInventory();
                            if (inv instanceof DoubleChestInventory) {
                                DoubleChest doubleChest = ((DoubleChest) inv.getHolder());
                                inv = doubleChest.getInventory();
                                leftChestLocation = doubleChest.getLeftSide().getInventory().getLocation();
                            }

                            tracker.markProgress(1);
                            if (!leftChestLocations.contains(leftChestLocation)) {
                                cache.add(inv);
                                leftChestLocations.add(leftChestLocation);
                            }
                        }
                    }
                    return true;
                }).get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    public int numChests() {
        return chestLocations.size();
    }

    //Return chests containing these items
    public List<Location> find(CommandSender sender, Material material) {

        List<Location> locations = new ArrayList<>();

        updateCache(sender);
        for (Inventory inv : cache) {
            if (inv.contains(material)) locations.add(inv.getLocation());
        }

        return locations;

    }

    public String track(Location chest, Material mat) {

        BlockState state;
        Future<BlockState> future = Bukkit.getScheduler().callSyncMethod(plugin, () -> chest.getBlock().getState());
        try {
            state = future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return "//";
        }
        if (state instanceof Chest) {
            Inventory contents = ((Chest) state).getBlockInventory();
            int quantity = 0;
            int stacks = 0;
            for (ItemStack stack : contents.getContents()) {
                if (stack == null) continue;
                if (stack.getType() == mat) {
                    quantity += stack.getAmount();
                    stacks++;
                }
            }
            return String.format("%d, %d, %d : %s x%d in %d stacks", chest.getBlockX(), chest.getBlockY(), chest.getBlockZ(), mat.name(), quantity, stacks);
        } else return "//";
    }

    public World getWorld() {
        if (chestLocations.isEmpty()) return Bukkit.getWorlds().get(0);
        else return chestLocations.get(0).getWorld();
    }

    //returns a list of all invalid distances
    public List<Location> verifyDistance() {

        List<LocationCluster> locationClusters = generateLocationClusters(plugin.getUpdateDistance());

        if (locationClusters.size() < 2) return new ArrayList<>();
        else {
            return locationClusters.stream()
                    .sorted(Comparator.comparingInt(o -> -o.locations.size())) //largest first
                    .skip(1) //skip the largest
                    .flatMap(o -> o.locations.stream()) //map the remaining into a single list
                    .collect(Collectors.toList());
        }

    }

    private boolean verifyOtherDistance(List<Location> otherSet) {

        int maxDistance = plugin.getUpdateDistance();

        LocationCluster baseCluster = generateLocationClusters(maxDistance).stream().findFirst().orElse(null);
        if (baseCluster == null) return true;

        for (Location loc : otherSet) {
            if (baseCluster.include(loc, maxDistance)) {
                return true; //it's already a single cluster
            }
        }

        return false;

    }

    private List<LocationCluster> generateLocationClusters(int updateDistance) {

        List<LocationCluster> locationClusters = new ArrayList<>();
        for (Location location : chestLocations) {
            boolean clustered = false;
            for (int i = 0; i < locationClusters.size(); i++) {
                LocationCluster cluster = locationClusters.get(i);
                if (cluster.include(location, updateDistance)) {
                    if (clustered) {
                        locationClusters.remove(i);
                        i--;
                    } else {
                        clustered = true;
                    }
                }
            }
            if (!clustered) {
                locationClusters.add(new LocationCluster(location));
            }
        }

        return locationClusters;

    }

    //returns a list of all non-container locations
    public List<Location> verifyChests() {

        return chestLocations.stream().filter(location -> plugin.getChestMaterials().contains(location.getBlock().getType())).collect(Collectors.toList());

    }

    public void remove(List<Location> list) {
        chestLocations.removeAll(list);
    }

    public boolean within(Player player, int range) {

        LocationCluster baseCluster = generateLocationClusters(plugin.getUpdateDistance()).stream().findFirst().orElse(null);
        if (baseCluster == null) return false;

        return baseCluster.include(player.getEyeLocation(), range);
    }

    private static class DepotManifestEntry {

        Material material;
        int amount;
        int stacks;
        int chests;

        DepotManifestEntry(Material material, int amount, int stacks, int chests) {
            this.material = material;
            this.amount = amount;
            this.stacks = stacks;
            this.chests = chests;
        }

        public int getAmount() {
            return amount;
        }

        public String fragmentationLevel(DepotDefragPlugin plugin) {
            //MAROON: Severe fragmentation - item could be in fewer stacks and chests
            //RED: Major fragmentation, item could be in fewer stacks
            //YELLOW: Minor fragmentation, item could be in fewer chests
            //GREEN: No fragmentation :)

            boolean freeStacks = Math.ceil((double) amount / (double) material.getMaxStackSize()) < stacks;
            boolean theoreticalFreeChests = Math.ceil(((double) amount / (double) material.getMaxStackSize() / 27.00)) < chests;
            if (freeStacks && theoreticalFreeChests) return plugin.getLanguageLoader().get("manifest_tag_both");
            else if (freeStacks) return plugin.getLanguageLoader().get("manifest_tag_wasteful");
            else if (theoreticalFreeChests) return plugin.getLanguageLoader().get("manifest_tag_scattered");
            else return plugin.getLanguageLoader().get("manifest_tag_clean");
        }

        public String printForPlugin(DepotDefragPlugin plugin) {
            String format;
            if (amount == 1) {
                return String.format(plugin.getLanguageLoader().get("manifest_entry_single"), fragmentationLevel(plugin), material.name(), amount);
            }
            if (material.getMaxStackSize() == 1) {
                format = "manifest_entry_unstackable";
                return String.format(plugin.getLanguageLoader().get(format), fragmentationLevel(plugin), material.name(), amount, chests);
            } else if (material.getMaxStackSize() == 64) {
                format = "manifest_entry_default";
                return String.format(plugin.getLanguageLoader().get(format), fragmentationLevel(plugin), material.name(), amount, stacks, chests);
            } else {
                format = "manifest_entry";
                return String.format(plugin.getLanguageLoader().get(format), fragmentationLevel(plugin), material.name(), amount, material.getMaxStackSize(), stacks, chests);
            }
        }
    }
}
