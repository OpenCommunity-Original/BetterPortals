package org.envel.betterportals.bukkit.tasks;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.envel.betterportals.shared.logging.Logger;
import org.envel.betterportals.bukkit.util.SchedulerUtil;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Uses an asynchronous Bukkit task to implement BlockViewUpdateFinisher
 * The advantage to this is that it's probably the correct way of doing it
 * The disadvantage is that it's usually a while before the Bukkit task is run (often the next tick), so this can increase latency
 */
@Singleton
public class AsyncBlockUpdateFinisher extends BlockUpdateFinisher implements Runnable    {
    private final JavaPlugin pl;

    @Inject
    public AsyncBlockUpdateFinisher(JavaPlugin pl, Logger logger) {
        super(logger);

        this.pl = pl;
    }

    private SchedulerUtil.PortalTask updateTask;

    @Override
    public void start() {
        stop();
        super.start();
        updateTask = SchedulerUtil.runTimerAsync(this, 0, 1);
    }

    @Override
    public void stop() {
        if(updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        super.stop();
    }

    @Override
    public void run() {
        super.finishPendingUpdates();
    }
}
