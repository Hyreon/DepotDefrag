package top.hyreon.depotDefrag;

import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.concurrent.CancellationException;

public class ProgressTracker {

    private static final Map<CommandSender, List<ProgressTracker>> cancelable = new HashMap<>();
    public static List<ProgressTracker> cancel(CommandSender sender) {
        List<ProgressTracker> ret = Objects.requireNonNullElse(cancelable.remove(sender), new ArrayList<>());
        for (ProgressTracker tracker : ret) {
            tracker.canceled = true;
        }
        return ret;
    }


    boolean canceled = false;
    boolean cancellable = false;

    int progressMade = 0;

    long startTime;
    long cancelTime;
    long lastTime;
    long currentTime;
    long interval;

    int initialAmountRequired;
    int amountRequired;

    final String taskName;
    final CommandSender sender;
    private final LanguageLoader lloader;

    public ProgressTracker(String taskName, CommandSender sender, LanguageLoader lloader, long interval, long cancelTime, int amountRequired) {
        this.taskName = taskName;
        this.lloader = lloader;
        this.sender = sender;

        this.interval = interval;
        this.initialAmountRequired = amountRequired;
        this.amountRequired = amountRequired;

        startTime = System.currentTimeMillis();
        this.cancelTime = startTime + cancelTime;
        this.lastTime = startTime;

    }

    //should be ran immediately before making progress.
    public void markProgress(int amount) throws CancellationException {

        if (canceled) throw new CancellationException();

        currentTime = System.currentTimeMillis();
        long nextThresh = lastTime / interval;
        progressMade += amount;
        if (currentTime / interval > nextThresh) {
            lastTime = currentTime;
            if (sender != null) sender.sendMessage(String.format(lloader.get("progress"), lloader.get(taskName), progressMade, amountRequired));
        }
        if (!cancellable && currentTime > cancelTime) {
            cancelable.putIfAbsent(sender, new ArrayList<>());
            cancelable.get(sender).add(this);
            cancellable = true;
            if (sender != null) sender.sendMessage(lloader.get("search_long"));
        }
    }

    public void extendGoalpost(int amountRequired) {
        this.amountRequired += amountRequired;
    }

    public void extendGoalpost() {
        this.amountRequired += initialAmountRequired;
    }

}
