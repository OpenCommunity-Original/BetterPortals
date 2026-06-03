package org.envel.betterportals.bukkit.block;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import org.envel.betterportals.api.IntVector;
import org.envel.betterportals.api.PortalDirection;
import org.envel.betterportals.bukkit.config.RenderConfig;
import org.envel.betterportals.bukkit.math.MathUtil;
import org.envel.betterportals.bukkit.math.Matrix;
import org.envel.betterportals.bukkit.portal.IPortal;
import org.envel.betterportals.bukkit.util.performance.OperationTimer;
import org.envel.betterportals.shared.logging.Logger;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract viewable block map that is intended to have the update functions perform a flood fill to find which blocks are viewable.
 */
public abstract class FloodFillBlockMap implements IBlockMap {
    protected final Logger logger;
    protected final RenderConfig renderConfig;

    protected final ConcurrentHashMap<IntVector, PacketContainer> originTileStates = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<IntVector, PacketContainer> destTileStates = new ConcurrentHashMap<>();

    protected StateQueue stateQueue;

    protected List<IViewableBlockInfo> nonObscuredStates = new ArrayList<>();

    protected byte[] alreadyReachedMap;

    protected final IPortal portal;
    protected final Matrix rotateOriginToDest;
    protected final IntVector portalOriginPos;
    protected final IntVector portalDestPos;

    protected final IntVector centerPos;
    protected final PortalDirection destDirection;
    protected boolean firstUpdate;

    public FloodFillBlockMap(IPortal portal, Logger logger, RenderConfig renderConfig) {
        this.portal = portal;
        this.logger = logger;
        this.renderConfig = renderConfig;
        this.centerPos = new IntVector(portal.getOriginPos().getVector());
        this.rotateOriginToDest = portal.getTransformations().getRotateToDestination();
        this.destDirection = portal.getDestPos().getDirection();
        this.portalOriginPos = new IntVector(portal.getOriginPos().getVector());
        this.portalDestPos = roundBasedOnDirection(portal);

        reset();
    }

    private IntVector roundBasedOnDirection(IPortal portal) {
        Vector originPosVec = portal.getOriginPos().getVector();
        Vector originPosCenter = MathUtil.moveToCenterOfBlock(originPosVec);
        Vector relOriginPos = originPosCenter.clone().subtract(originPosVec);
        Vector relDestPos = portal.getTransformations().rotateToDestination(relOriginPos);
        Vector destPosCenter = portal.getDestPos().getVector().add(relDestPos);

        return new IntVector(destPosCenter);
    }

    protected boolean isInLine(IntVector relPos) {
        return destDirection.swapVector(relPos).getZ() == 0;
    }

    protected final int getArrayMapIndex(IntVector relPos) {
        return ((relPos.getX() + (int) renderConfig.getMaxXZ()) + (relPos.getZ() + (int) renderConfig.getMaxXZ()) * renderConfig.getZMultip() + (relPos.getY() + (int) renderConfig.getMaxY()) * renderConfig.getYMultip());
    }

    /**
     * Starts a flood fill from <code>start</code> out to the edges of the viewed portal area.
     * The fill stops when it reaches occluding blocks, as we don't need to render other blocks behind these.
     * The origin data is also fetched, and this is placed in the viewable states
     * @param originPos Start position of the flood fill, at the origin (non-relative)
     * @param statesOutput List to place the new viewable states within
     * @param firstBlockInfo Optional, used instead of adding a new block to the map for the first block. Useful for incremental updates
     */
    protected abstract void searchFromBlock(IntVector originPos, List<IViewableBlockInfo> statesOutput, @Nullable IViewableBlockInfo firstBlockInfo);

    /**
     * Checks the origin and destination blocks for changes.
     * At the origin, we only need to check the actually viewable blocks, since there is no need to re-flood-fill.
     * At the destination, we must check all blocks that were reached by the flood-fill, then do a re-flood-fill for any that have changed to add blocks in a newly revealed cavern, for instance.
     */
    protected abstract void checkForChanges();

    protected final WrappedBlockData getBackgroundData() {
        return renderConfig.findBackgroundData(portal.getDestPos());
    }

    @Override
    public void update(int ticksSinceActivated) {
        if(ticksSinceActivated % renderConfig.getBlockUpdateInterval() != 0) {return;}

        updateInternal();
    }

    protected void updateInternal() {
        if(alreadyReachedMap == null) {
            alreadyReachedMap = new byte[renderConfig.getTotalArrayLength()];
        }

        OperationTimer timer = new OperationTimer();
        if(firstUpdate) {
            List<IViewableBlockInfo> initialStates = new ArrayList<>();
            searchFromBlock(centerPos, initialStates, null);
            stateQueue.addStatesInitially(initialStates);
        }   else    {
            checkForChanges();
        }
        firstUpdate = false;
        logger.fine("Viewable block array update took: %.3f ms. Block count: %d. Viewable count: %d", timer.getTimeTakenMillis(), nonObscuredStates.size(), stateQueue.stateCount());
    }

    @Override
    public void reset() {
        logger.finer("Clearing block array to save memory");

        stateQueue = new StateQueue(logger);
        nonObscuredStates = new ArrayList<>();
        originTileStates.clear();
        destTileStates.clear();
        firstUpdate = true;
        alreadyReachedMap = null;
    }

    @Override
    public List<IViewableBlockInfo> getViewableStates() {
        if(stateQueue == null) {
            return null;
        }

        return stateQueue.getViewableStates();
    }

    @Override
    public @Nullable PacketContainer getOriginTileEntityPacket(@NotNull IntVector position) {
        return originTileStates.get(position);
    }

    @Override
    public @Nullable PacketContainer getDestinationTileEntityPacket(@NotNull IntVector position) {
        return destTileStates.get(position);
    }
}
