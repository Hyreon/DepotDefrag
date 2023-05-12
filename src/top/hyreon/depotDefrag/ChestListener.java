package top.hyreon.depotDefrag;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

public class ChestListener implements Listener {

    @EventHandler
    public static void onChestEntityExplode(EntityExplodeEvent e) {

        for (int i = 0; i < e.blockList().size(); i++) {
            Block block = e.blockList().get(i);
            if (!DepotDefragPlugin.getChestMaterials().contains(block.getType())) continue;

            if (DepotDefragPlugin.lockedBlocks.stream().anyMatch(l -> l.contains(block.getLocation()))) {
                e.blockList().remove(i);
                i--;
            }
        }


    }

    @EventHandler
    public static void onChestBlockExplode(BlockExplodeEvent e) {

        for (int i = 0; i < e.blockList().size(); i++) {
            Block block = e.blockList().get(i);
            if (!DepotDefragPlugin.getChestMaterials().contains(block.getType())) continue;

            if (DepotDefragPlugin.lockedBlocks.stream().anyMatch(l -> l.contains(block.getLocation()))) {
                e.blockList().remove(i);
                i--;
            }
        }


    }

    @EventHandler
    public static void onChestDestroyEvent(BlockBreakEvent e) {
        if (!DepotDefragPlugin.getChestMaterials().contains(e.getBlock().getType())) return;

        e.getPlayer();

        if (DepotDefragPlugin.lockedBlocks.stream().anyMatch(l -> l.contains(e.getBlock().getLocation()))) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(DepotDefragPlugin.getLanguageLoader().get("chest_locked"));
        }

    }

    @EventHandler
    public static void onChestOpenEvent(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null) return;
        if (!DepotDefragPlugin.getChestMaterials().contains(block.getType())) return;

        if (DepotDefragPlugin.lockedBlocks.stream().anyMatch(l -> l.contains(e.getClickedBlock().getLocation()))) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(DepotDefragPlugin.getLanguageLoader().get("chest_locked"));
        }

        //check if clicked block is one of the active seeking depots
        List<DepotFinder> seeks = DepotFinder.getActiveSeeks();
        for (int i = 0; i < seeks.size(); i++) {
            DepotFinder finder = seeks.get(i);
            if (finder.locations.contains(block.getLocation())) {
                finder.stop(null); //successfully found, stop blasting the particles
                i--;
            }
        }

    }

}
