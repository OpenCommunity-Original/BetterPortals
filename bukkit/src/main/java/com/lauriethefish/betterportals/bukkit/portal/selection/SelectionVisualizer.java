package com.lauriethefish.betterportals.bukkit.portal.selection;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.lauriethefish.betterportals.bukkit.player.IPlayerData;
import com.lauriethefish.betterportals.bukkit.player.IPlayerDataManager;
import com.lauriethefish.betterportals.bukkit.util.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

@Singleton
public class SelectionVisualizer implements Runnable {
    private final IPlayerDataManager playerDataManager;
    private SchedulerUtil.PortalTask visualizerTask;
    private static final long TIMEOUT_MS = 60000; // 60 seconds of inactivity before fading out

    @Inject
    public SelectionVisualizer(IPlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
    }

    public void start() {
        if (visualizerTask != null) {
            visualizerTask.cancel();
        }
        visualizerTask = SchedulerUtil.runTaskTimer(this, 0L, 10L); // Run every 10 ticks (0.5s)
    }

    public void stop() {
        if (visualizerTask != null) {
            visualizerTask.cancel();
            visualizerTask = null;
        }
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (IPlayerData playerData : playerDataManager.getPlayers()) {
            Player player = playerData.getPlayer();
            if (player == null || !player.isOnline()) continue;

            ISelectionManager selection = playerData.getSelection();
            if (selection == null) continue;

            // Fading out check: if player has been inactive for too long, don't show particles
            if (now - selection.getLastActivityTime() > TIMEOUT_MS) {
                continue;
            }

            // Draw current active selection
            drawSelectionBorder(player, selection.getCurrentlySelecting(), Particle.HAPPY_VILLAGER);

            // Draw finalized origin selection in redstone (red) if present
            if (selection.getOriginSelection() != null) {
                drawSelectionBorder(player, selection.getOriginSelection(), Particle.HEART);
            }

            // Draw finalized destination selection in portal particles if present
            if (selection.getDestSelection() != null) {
                drawSelectionBorder(player, selection.getDestSelection(), Particle.PORTAL);
            }
        }
    }

    private void drawSelectionBorder(Player player, IPortalSelection selection, Particle particle) {
        if (selection == null || !selection.isValid()) return;

        Location locA = selection.getPosA();
        Location locB = selection.getPosB();
        if (locA == null || locB == null) return;

        World world = locA.getWorld();
        if (world == null || !world.equals(player.getWorld())) return;

        double minX = locA.getX();
        double minY = locA.getY();
        double minZ = locA.getZ();

        double maxX = locB.getX() + 1.0;
        double maxY = locB.getY() + 1.0;
        double maxZ = locB.getZ() + 1.0;

        // Optimization: only spawn particles close to the player
        Location pLoc = player.getLocation();
        if (pLoc.distanceSquared(locA) > 10000) return; // limit to ~100 blocks

        // Draw 12 edges
        drawEdge(player, world, minX, minY, minZ, maxX, minY, minZ, particle);
        drawEdge(player, world, minX, minY, minZ, minX, maxY, minZ, particle);
        drawEdge(player, world, minX, minY, minZ, minX, minY, maxZ, particle);

        drawEdge(player, world, maxX, maxY, maxZ, minX, maxY, maxZ, particle);
        drawEdge(player, world, maxX, maxY, maxZ, maxX, minY, maxZ, particle);
        drawEdge(player, world, maxX, maxY, maxZ, maxX, maxY, minZ, particle);

        drawEdge(player, world, minX, maxY, minZ, maxX, maxY, minZ, particle);
        drawEdge(player, world, minX, maxY, minZ, minX, maxY, maxZ, particle);

        drawEdge(player, world, maxX, minY, minZ, maxX, maxY, minZ, particle);
        drawEdge(player, world, maxX, minY, minZ, maxX, minY, maxZ, particle);

        drawEdge(player, world, minX, minY, maxZ, maxX, minY, maxZ, particle);
        drawEdge(player, world, minX, minY, maxZ, minX, maxY, maxZ, particle);
    }

    private void drawEdge(Player player, World world, double x1, double y1, double z1, double x2, double y2, double z2, Particle particle) {
        double dist = Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2) + (z1 - z2) * (z1 - z2));
        int points = (int) (dist * 2.0); // 2 particles per block
        if (points == 0) points = 1;

        double stepX = (x2 - x1) / points;
        double stepY = (y2 - y1) / points;
        double stepZ = (z2 - z1) / points;

        for (int i = 0; i <= points; i++) {
            double px = x1 + i * stepX;
            double py = y1 + i * stepY;
            double pz = z1 + i * stepZ;
            // Spawn particle specifically for this player to optimize network/rendering
            player.spawnParticle(particle, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
