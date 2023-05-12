package top.hyreon.depotDefrag;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

//a class to simplify tracking validity of a depot chest.
public class LocationCluster {

    public @NotNull Location greaterEdge;
    public @NotNull Location lesserEdge;
    public List<Location> locations = new ArrayList<>();

    public LocationCluster(Location loc) {
        locations.add(loc);
        greaterEdge = loc;
        lesserEdge = loc;
    }

    public boolean include(@NotNull Location loc, int range) {
        if (loc.getBlockX() - range > greaterEdge.getBlockX()) return false;
        if (loc.getBlockY() - range > greaterEdge.getBlockY()) return false;
        if (loc.getBlockZ() - range > greaterEdge.getBlockZ()) return false;
        if (loc.getBlockX() + range < lesserEdge.getBlockX()) return false;
        if (loc.getBlockY() + range < lesserEdge.getBlockY()) return false;
        if (loc.getBlockZ() + range < lesserEdge.getBlockZ()) return false;
        return include(loc);
    }

    public boolean include(@NotNull Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().equals(greaterEdge.getWorld())) return false;
        greaterEdge = new Location(greaterEdge.getWorld(),
                Math.max(greaterEdge.getBlockX(), loc.getBlockX()),
                Math.max(greaterEdge.getBlockY(), loc.getBlockY()),
                Math.max(greaterEdge.getBlockZ(), loc.getBlockZ()));
        lesserEdge = new Location(lesserEdge.getWorld(),
                Math.min(lesserEdge.getBlockX(), loc.getBlockX()),
                Math.min(lesserEdge.getBlockY(), loc.getBlockY()),
                Math.min(lesserEdge.getBlockZ(), loc.getBlockZ()));
        locations.add(loc);
        return true;
    }

}
