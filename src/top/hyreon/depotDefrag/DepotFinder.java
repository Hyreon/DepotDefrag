package top.hyreon.depotDefrag;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class DepotFinder {

    private static final List<DepotFinder> activeSeeks = new ArrayList<>();
    private final DepotDefragPlugin plugin;

    public static List<DepotFinder> getActiveSeeks() {
        return activeSeeks;
    }

    private static final Vector CENTER_VECTOR = new Vector(0.5f, 0.5f, 0.5f);
    UUID seeker;
    Material materialSought;
    List<Location> locations = new ArrayList<>();

    int particleCreatorTask = -1;

    public DepotFinder(UUID seeker, Material materialSought, DepotDefragPlugin plugin) {
        this.seeker = seeker;
        this.materialSought  = materialSought;
        this.plugin = plugin;
    }

    public static boolean cancel(CommandSender sender) {
        boolean anythingToRemove = false;
        if (sender instanceof Player) {
            UUID uuid = ((Player) sender).getUniqueId();
            for (int i = 0; i < activeSeeks.size(); i++) {
                DepotFinder activeSeek = activeSeeks.get(i);
                if (activeSeek.seeker.equals(uuid)) {
                    activeSeek.stop(null);
                    anythingToRemove = true;
                    i--;
                }
            }
        }
        return anythingToRemove;
    }

    public static boolean isSearching(CommandSender sender) {
        if (sender instanceof Player) {
            UUID uuid = ((Player) sender).getUniqueId();
            for (DepotFinder activeSeek : activeSeeks) {
                if (activeSeek.seeker.equals(uuid)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void start() {

        if (particleCreatorTask != -1) stop(null);

        particleCreatorTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {

            Player player = Bukkit.getPlayer(seeker);
            if (player == null) {
                stop(null);
                return;
            }

            Location src;
            Location dest;
            int currentChest = 0;
            while (currentChest < locations.size()) {

                dest = locations.get(currentChest);
                src = player.getLocation();

                if (dest.getWorld() == null) {
                    locations.remove(currentChest);
                    continue;
                }

                if (src.getWorld() != dest.getWorld()) {
                    locations.remove(currentChest);
                    continue;
                }

                player.spawnParticle(Particle.VILLAGER_HAPPY, dest.clone().add(CENTER_VECTOR), 5, 0.3, 0.3, 0.3, 0.0);

                currentChest++;

            }

            if (locations.isEmpty()) {
                stop(plugin.getLanguageLoader().get("find_interrupted"));
            }

            //player.spawnParticle(Particle.END_ROD, dest.clone().add(CENTER_VECTOR), 4, 0.4, 0.4, 0.4, 0.0);
            //player.spawnParticle(Particle.CRIT, dest.clone().add(CENTER_VECTOR), 20);
            //player.spawnParticle(Particle.ENCHANTMENT_TABLE, dest.clone().add(CENTER_VECTOR), 60);
            //player.spawnParticle(Particle.PORTAL, dest.clone().add(CENTER_VECTOR), 60);

        }, 0L, 5L);

        activeSeeks.add(this);

    }

    void stop(String response) {
        Bukkit.getScheduler().cancelTask(particleCreatorTask);
        particleCreatorTask = -1;
        if (response != null) {
            Bukkit.getPlayer(seeker).sendMessage(response);
        }
        activeSeeks.remove(this);
    }

    public void addLocation(Location chest) {
        if (!plugin.addDoubleChestLocations(locations, chest)) locations.add(chest);
    }

}
