package top.hyreon.depotDefrag;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class DepotDefragPlugin extends JavaPlugin {

    PluginConfigCache configCache;

    static LanguageLoader langLoader;
    static List<Set<Location>> lockedBlocks = new ArrayList<>();

    Map<String, Depot> depots = new HashMap<>();

    boolean secure = true;
    private static final Collection<Object> chestMaterials = List.of(Material.CHEST, Material.TRAPPED_CHEST);

    @Override
    public void onEnable() {

        loadDepots();
        saveDefaultConfig();

        langLoader = new LanguageLoader(this);
        configCache = new PluginConfigCache();

        DepotCommand cmd = new DepotCommand(this);
        Bukkit.getPluginCommand("depot").setExecutor(cmd);
        Bukkit.getPluginCommand("depot").setTabCompleter(new TabCompleterDepot(this));

        getServer().getPluginManager().registerEvents(new ChestListener(), this);

    }

    @Override
    public void onDisable() {

        saveDepots();

    }

    private void loadDepots() {

        String depotDirectory = getDataFolder() + "/depots/";
        File directory = new File(depotDirectory);
        File[] depotFiles = directory.listFiles();
        if (depotFiles == null) return;
        for (File depotFile : depotFiles) {

            String fileName = depotFile.getName();
            if (!fileName.endsWith(".dat")) continue;
            String depotName = fileName.substring(0, fileName.length() - 4);
            String[] depotSubstrings = depotName.split(":"); //to reduce depots bleeding across worlds
            if (depotSubstrings.length > 1) depotName = depotSubstrings[1];

            try {
                List<Location> locations = new ArrayList<>();
                String depotWorld;
                int depotX;
                int depotY;
                int depotZ;
                BufferedReader reader = new BufferedReader(new FileReader(depotFile));
                Set<UUID> owners = Arrays.stream(reader.readLine().split(",")).map(UUID::fromString).collect(Collectors.toSet());
                owners.remove(null); //in case it snuck in
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] depotParameters = line.split(" ");
                    depotWorld = depotParameters[0];
                    depotX = Integer.parseInt(depotParameters[1]);
                    depotY = Integer.parseInt(depotParameters[2]);
                    depotZ = Integer.parseInt(depotParameters[3]);

                    World world = Bukkit.getWorld(depotWorld);
                    if (world == null) continue; //skip depots that belong to other servers.
                    Location location = new Location(world, depotX, depotY, depotZ);

                    locations.add(location);
                }

                if (!locations.isEmpty()) {
                    Depot depot = new Depot(owners, locations, this);
                    depots.put(depotName, depot);
                }
            } catch (IOException e) {
                //give up quietly
                Bukkit.getLogger().log(Level.WARNING, "Unable to load file " + fileName + ", skipping this depot.");
            }


        }

    }

    private void saveDepots() {

        File depotDirectory = getDepotDirectory();
        depotDirectory.mkdir();
        for (String id : depots.keySet()) {

            Depot depot = depots.get(id);

            File depotFile = getFileOf(id);
            try {
                depotFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

            int badChests = 0;
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(depotFile));

                writer.write(depot.owners.stream().map(UUID::toString).collect(Collectors.joining(",")) + "\n");
                for (Location location : depot.chestLocations) {
                    try {
                        writer.write(location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ() + "\n");
                    } catch (NullPointerException e) {
                        badChests++;
                    }
                }
                if (badChests > 0) {
                    Bukkit.getLogger().log(Level.WARNING, String.format("%d bad chests in depot %s", badChests, id));
                }

                writer.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private File getDepotDirectory() {
        return new File(getDataFolder() + "/depots/");
    }

    private File getFileOf(String id) {
        return new File(getDepotDirectory() + "/" + depots.get(id).getWorld().getName() + ":" + id + ".dat");
    }

    public static LanguageLoader getLanguageLoader() {
        return langLoader;
    }

    public void setSecure(boolean b) {
        secure = b;
    }

    public boolean getSecure() {
        return secure;
    }

    public int getMaxDistance() {
        return configCache.maxDistance;
    }

    public int getSafeDistance() {
        return configCache.safeDistance;
    }

    public int getUpdateDistance() {
        return configCache.updateDistance;
    }

    public int getCountDistance() {
        return configCache.countDistance;
    }

    public int getFindDistance() {
        return configCache.findDistance;
    }

    public int getDefragDistance() {
        return configCache.defragDistance;
    }

    public int getUpdateCacheChunkSize() {
        return configCache.updateCacheChunkSize;
    }

    public ChatColor getColor() {
        String defaultColor = getConfig().getString("default-color");
        if (defaultColor == null) return ChatColor.WHITE;
        return ChatColor.of(defaultColor);
    }

    public void removeDepot(String id) {
        File file = getFileOf(id);
        file.delete();
        depots.remove(id);
    }

    boolean addDoubleChestLocations(List<Location> list, Location chest) {

        Future<Boolean> future = Bukkit.getScheduler().callSyncMethod(this, () -> {

            Chest state = (Chest) chest.getBlock().getState();
            InventoryHolder holder = (state).getInventory().getHolder();
            if (holder instanceof DoubleChest) {
                DoubleChest dc = (DoubleChest) holder;
                list.add(((BlockInventoryHolder) dc.getLeftSide()).getBlock().getLocation());
                list.add(((BlockInventoryHolder) dc.getRightSide()).getBlock().getLocation());
                return true;
            } else return false;

        });

        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return false;
        }

    }

    public static Collection<Object> getChestMaterials() {
        return chestMaterials;
    }

    public boolean lock(Set<Location> blocksAffected) {

        if (blocksAffected.isEmpty()) return false;

        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory inv = player.getOpenInventory().getTopInventory();
            if (inv instanceof DoubleChestInventory) {
                Inventory other = ((DoubleChestInventory) inv).getRightSide();
                inv = ((DoubleChestInventory) inv).getLeftSide();
                if (blocksAffected.contains(other.getLocation())) {
                    player.closeInventory();
                    player.sendMessage(langLoader.get("chest_locked"));
                    continue;
                }
            }
            if (blocksAffected.contains(inv.getLocation())) {
                player.closeInventory();
                player.sendMessage(langLoader.get("chest_locked"));
            }
        }

        lockedBlocks.add(blocksAffected);

        return true;

    }

    public long getLoadTime() {
        return configCache.loadTime;
    }

    public long getCancelTime() {
        return configCache.cancelTime;
    }

    private class PluginConfigCache {

        int countDistance;
        int findDistance;
        int defragDistance;
        int updateDistance;
        int safeDistance;
        int maxDistance;
        int updateCacheChunkSize;
        long loadTime;
        long cancelTime;

        PluginConfigCache() {
            countDistance = getConfig().getInt("max-count-distance");
            if (countDistance == -1) countDistance = Integer.MAX_VALUE/2;
            findDistance = getConfig().getInt("max-find-distance");
            if (findDistance == -1) findDistance = Integer.MAX_VALUE/2;
            defragDistance = getConfig().getInt("max-defrag-distance");
            if (defragDistance == -1) defragDistance = Integer.MAX_VALUE/2;
            updateDistance = getConfig().getInt("max-update-distance");
            if (updateDistance == -1) updateDistance = Integer.MAX_VALUE/2;
            safeDistance = getConfig().getInt("safe-distance");
            if (safeDistance == -1) safeDistance = Integer.MAX_VALUE/2;
            maxDistance = getConfig().getInt("max-distance");
            if (maxDistance == -1) maxDistance = Integer.MAX_VALUE/2;
            updateCacheChunkSize = getConfig().getInt("update-cache-chunk-size");
            if (updateCacheChunkSize == -1) updateCacheChunkSize = Integer.MAX_VALUE/2;
            loadTime = (long) (1000L * getConfig().getDouble("display-interval-time"));
            cancelTime = (long) (1000L * getConfig().getDouble("cancel-minimum-time"));
        }

    }
}
