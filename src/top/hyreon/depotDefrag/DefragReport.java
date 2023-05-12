package top.hyreon.depotDefrag;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;

public class DefragReport {

    int stacksSwappedCount = 0;
    int stacksFreedCount = 0; //moves that removed the 'from' stack entirely
    Set<Location> chestsModified = new HashSet<>();
    private int cycles;
    private boolean complete = false;
    private boolean locked = false;

    public void addTransaction(Location from, Location to, boolean freedStack) {
        chestsModified.add(from);
        chestsModified.add(to);
        if (freedStack) stacksFreedCount++;
        stacksSwappedCount++;
    }

    public void markComplete() {
        if (!locked) complete = true;
    }

    public boolean isComplete() { return complete && !locked; }

    public void addCycle() {
        cycles++;
    }

    public void append(DefragReport report) {
        stacksSwappedCount += report.stacksSwappedCount;
        stacksFreedCount += report.stacksFreedCount;
        cycles += report.cycles;
        complete &= report.complete;
        chestsModified.addAll(report.chestsModified);
    }

    public void lockFromCompletion() {
        locked = true;
    }
}
