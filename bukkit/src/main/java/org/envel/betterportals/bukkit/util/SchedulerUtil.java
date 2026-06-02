package org.envel.betterportals.bukkit.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public class SchedulerUtil {
    private static JavaPlugin plugin;
    private static boolean isFolia;
    
    private static final java.util.Set<PortalTask> activeTasks = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void init(JavaPlugin pl) {
        plugin = pl;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }
    }

    public static boolean isFolia() {
        return isFolia;
    }

    public interface PortalTask {
        void cancel();
    }

    public static void cancelAll() {
        for (PortalTask task : new java.util.ArrayList<>(activeTasks)) {
            try {
                task.cancel();
            } catch (Exception ignored) {}
        }
        activeTasks.clear();
    }

    private static PortalTask trackOneShot(java.util.function.Function<Runnable, PortalTask> scheduler, Runnable runnable) {
        PortalTask[] taskRef = new PortalTask[1];
        Runnable wrapped = () -> {
            try {
                runnable.run();
            } finally {
                activeTasks.remove(taskRef[0]);
            }
        };
        PortalTask task = scheduler.apply(wrapped);
        PortalTask wrappedTask = () -> {
            activeTasks.remove(taskRef[0]);
            task.cancel();
        };
        taskRef[0] = wrappedTask;
        activeTasks.add(wrappedTask);
        return wrappedTask;
    }

    private static PortalTask trackRepeating(java.util.function.Function<Runnable, PortalTask> scheduler, Runnable runnable) {
        PortalTask[] taskRef = new PortalTask[1];
        PortalTask task = scheduler.apply(runnable);
        PortalTask wrappedTask = () -> {
            activeTasks.remove(taskRef[0]);
            task.cancel();
        };
        taskRef[0] = wrappedTask;
        activeTasks.add(wrappedTask);
        return wrappedTask;
    }

    public static PortalTask runTask(Runnable runnable) {
        return trackOneShot(wrapped -> {
            if (isFolia) {
                var task = Bukkit.getGlobalRegionScheduler().run(plugin, t -> wrapped.run());
                return () -> {
                    if (task != null) task.cancel();
                };
            } else {
                var task = Bukkit.getScheduler().runTask(plugin, wrapped);
                return task::cancel;
            }
        }, runnable);
    }

    public static PortalTask runTaskLater(Runnable runnable, long delayTicks) {
        return trackOneShot(wrapped -> {
            if (isFolia) {
                var task = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> wrapped.run(), delayTicks);
                return () -> {
                    if (task != null) task.cancel();
                };
            } else {
                var task = Bukkit.getScheduler().runTaskLater(plugin, wrapped, delayTicks);
                return task::cancel;
            }
        }, runnable);
    }

    public static PortalTask runTaskTimer(Runnable runnable, long initialDelayTicks, long periodTicks) {
        return trackRepeating(wrapped -> {
            if (isFolia) {
                long delay = Math.max(1L, initialDelayTicks);
                var task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> wrapped.run(), delay, periodTicks);
                return () -> {
                    if (task != null) task.cancel();
                };
            } else {
                var task = Bukkit.getScheduler().runTaskTimer(plugin, wrapped, initialDelayTicks, periodTicks);
                return task::cancel;
            }
        }, runnable);
    }

    public static PortalTask runAsync(Runnable runnable) {
        return trackOneShot(wrapped -> {
            if (isFolia) {
                var task = Bukkit.getAsyncScheduler().runNow(plugin, t -> wrapped.run());
                return () -> {
                    if (task != null) task.cancel();
                };
            } else {
                var task = Bukkit.getScheduler().runTaskAsynchronously(plugin, wrapped);
                return task::cancel;
            }
        }, runnable);
    }

    public static PortalTask runTimerAsync(Runnable runnable, long initialDelayTicks, long periodTicks) {
        return trackRepeating(wrapped -> {
            if (isFolia) {
                var task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, 
                    t -> wrapped.run(), 
                    initialDelayTicks * 50L, 
                    periodTicks * 50L, 
                    TimeUnit.MILLISECONDS
                );
                return () -> {
                    if (task != null) task.cancel();
                };
            } else {
                var task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, wrapped, initialDelayTicks, periodTicks);
                return task::cancel;
            }
        }, runnable);
    }

    public static PortalTask runAtLocation(Location location, Runnable runnable) {
        return trackOneShot(wrapped -> {
            if (isFolia) {
                var task = Bukkit.getRegionScheduler().run(plugin, location, t -> wrapped.run());
                return () -> {
                    if (task != null) task.cancel();
                };
            } else {
                var task = Bukkit.getScheduler().runTask(plugin, wrapped);
                return task::cancel;
            }
        }, runnable);
    }

    public static PortalTask runAtLocation(World world, int blockX, int blockZ, Runnable runnable) {
        return trackOneShot(wrapped -> {
            if (isFolia) {
                var task = Bukkit.getRegionScheduler().run(plugin, world, blockX >> 4, blockZ >> 4, t -> wrapped.run());
                return () -> {
                    if (task != null) task.cancel();
                };
            } else {
                var task = Bukkit.getScheduler().runTask(plugin, wrapped);
                return task::cancel;
            }
        }, runnable);
    }

    public static PortalTask runForEntity(Entity entity, Runnable runnable) {
        return trackOneShot(wrapped -> {
            if (isFolia) {
                var task = entity.getScheduler().run(plugin, t -> wrapped.run(), null);
                if (task == null) {
                    wrapped.run();
                    return () -> {};
                }
                return task::cancel;
            } else {
                var task = Bukkit.getScheduler().runTask(plugin, wrapped);
                return task::cancel;
            }
        }, runnable);
    }

    public static PortalTask runForEntityLater(Entity entity, Runnable runnable, long delayTicks) {
        return trackOneShot(wrapped -> {
            if (isFolia) {
                var task = entity.getScheduler().runDelayed(plugin, t -> wrapped.run(), null, delayTicks);
                if (task == null) {
                    wrapped.run();
                    return () -> {};
                }
                return task::cancel;
            } else {
                var task = Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
                return task::cancel;
            }
        }, runnable);
    }
}
