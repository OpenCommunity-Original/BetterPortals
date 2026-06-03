package org.envel.betterportals.bukkit.block.bukkit;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.envel.betterportals.api.IntVector;
import org.envel.betterportals.bukkit.block.FloodFillBlockMap;
import org.envel.betterportals.bukkit.block.IViewableBlockInfo;
import org.envel.betterportals.bukkit.block.fetch.BlockDataFetcherFactory;
import org.envel.betterportals.bukkit.block.fetch.IBlockDataFetcher;
import org.envel.betterportals.bukkit.block.lighting.ILightDataManager;
import org.envel.betterportals.bukkit.block.rotation.IBlockRotator;
import org.envel.betterportals.bukkit.config.RenderConfig;
import org.envel.betterportals.bukkit.math.Matrix;
import org.envel.betterportals.bukkit.nms.BlockDataUtil;
import org.envel.betterportals.bukkit.portal.IPortal;
import org.envel.betterportals.bukkit.util.MaterialUtil;
import org.envel.betterportals.shared.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * A bukkit implementation of a flood fill block map.
 */
public class BukkitBlockMap extends FloodFillBlockMap {
    private final IBlockRotator blockRotator;
    private final BlockDataFetcherFactory dataFetcherFactory;
    private final Matrix rotateDestToOrigin;
    private IBlockDataFetcher dataFetcher;

    private final World originWorld;
    private final ILightDataManager lightDataManager;

    private WrappedBlockData wrappedLightData;

    @Inject
    public BukkitBlockMap(@Assisted IPortal portal, Logger logger, RenderConfig renderConfig, IBlockRotator blockRotator, BlockDataFetcherFactory dataFetcherFactory, ILightDataManager lightDataManager) {
        super(portal, logger, renderConfig);
        this.blockRotator = blockRotator;
        this.dataFetcherFactory = dataFetcherFactory;
        this.rotateDestToOrigin = portal.getTransformations().getRotateToOrigin();
        this.lightDataManager = lightDataManager;

        this.originWorld = portal.getOriginPos().getWorld();
        logger.fine("Origin pos: %s, Dest pos: %s", portalOriginPos, portalDestPos);
        logger.fine("Origin direction: %s, Dest Direction: %s", portal.getOriginPos().getDirection(), portal.getDestPos().getDirection());
    }

    protected void searchFromBlock(IntVector start, List<IViewableBlockInfo> statesOutput, @Nullable IViewableBlockInfo firstBlockInfo) {
        WrappedBlockData backgroundData = getBackgroundData();

        final int timeBetweenLightBlocks = renderConfig.getLightSimulationInterval();

        if (wrappedLightData == null) {
            wrappedLightData = lightDataManager.getLightData(portal);
        }

        boolean enableLightBlocks = wrappedLightData != null && timeBetweenLightBlocks >= 1;
        int airCount = 0;

        // Preallocate stack with a reasonable size
        int[] stack = new int[Math.max(16, renderConfig.getTotalArrayLength())]; // Adjusted to prevent frequent resizing
        stack[0] = getArrayMapIndex(start.subtract(centerPos));
        int stackPos = 0;

        while (stackPos >= 0) {
            int positionInt = stack[stackPos--];

            int relX = (positionInt % renderConfig.getZMultip());
            int relY = Math.floorDiv(positionInt, renderConfig.getYMultip());
            int relZ = Math.floorDiv(positionInt - relY * renderConfig.getYMultip(), renderConfig.getZMultip());

            relX -= renderConfig.getMaxXZ();
            relY -= renderConfig.getMaxY();
            relZ -= renderConfig.getMaxXZ();

            IntVector originPos = new IntVector(relX + portalOriginPos.getX(), relY + portalOriginPos.getY(), relZ + portalOriginPos.getZ());
            IntVector destRelPos = rotateOriginToDest.transform(relX, relY, relZ);
            IntVector destPos = destRelPos.add(portalDestPos);

            BlockData destData = dataFetcher.getData(destPos);
            if (destData == null) {
                logger.warning("Fetched data was null even though the request to get the data had already succeeded. This shouldn't happen!");
                return;
            }

            boolean isOccluding = destData.getMaterial().isOccluding();

            Block originBlock = originPos.getBlock(originWorld);
            BlockData originData = originBlock.getBlockData();

            handleTileEntityUpdates(originBlock, originPos, originData, destData, destPos);

            // Use the existing first block if given, otherwise make a new block info
            BukkitBlockInfo blockInfo = firstBlockInfo == null ? new BukkitBlockInfo(originPos, originData, destData) : (BukkitBlockInfo) firstBlockInfo;
            boolean isEdge = renderConfig.isOutsideBounds(relX, relY, relZ);

            // Update rendered destination data
            updateRenderedData(isEdge, isOccluding, blockInfo, backgroundData, destData);

            if (firstBlockInfo == null) {
                nonObscuredStates.add(blockInfo);
            }
            firstBlockInfo = null;

            boolean canSkip = shouldSkipBlock(destData, originData, firstUpdate, isEdge);

            boolean isInLine = isInLine(destRelPos);

            if (alreadyReachedMap[positionInt] < 2 && !isInLine) {
                if (enableLightBlocks && destData.getMaterial().isAir() && !isEdge) {
                    airCount++;
                    if (airCount == timeBetweenLightBlocks) {
                        airCount = 0;
                        blockInfo.setRenderedDestData(wrappedLightData);
                        alreadyReachedMap[positionInt] = 2; // Avoid adding multiple times
                        statesOutput.add(blockInfo);
                    }
                } else if (!canSkip) {
                    alreadyReachedMap[positionInt] = 2; // Avoid adding multiple times
                    statesOutput.add(blockInfo);
                }
            }

            if (isOccluding || isEdge) continue;

            // Avoid stack resizing if possible
            if (!firstUpdate && stack.length - (stackPos + 1) < 5) {
                stack = Arrays.copyOf(stack, stack.length * 2);
            }

            // Continue for surrounding blocks
            for (int offset : renderConfig.getIntOffsets()) {
                int newPos = positionInt + offset;
                if (alreadyReachedMap[newPos] == 0) {
                    alreadyReachedMap[newPos] = 1;
                    stack[++stackPos] = newPos;
                }
            }
        }
    }

    private void handleTileEntityUpdates(Block originBlock, IntVector originPos, BlockData originData, BlockData destData, IntVector destPos) {
        // Handle tile entity updates for destination block if applicable
        if (!portal.isCrossServer() && MaterialUtil.isTileEntity(destData.getMaterial())) {
            logger.finer("Adding tile state to map . . .");
            Block destBlock = destPos.getBlock(Objects.requireNonNull(portal.getDestPos().getWorld()));
            PacketContainer updatePacket = BlockDataUtil.getUpdatePacket(destBlock.getState());
            if (updatePacket != null) {
                BlockDataUtil.setTileEntityPosition(updatePacket, originPos);
                destTileStates.put(originPos, updatePacket);
            }
        }

        // Handle tile entity updates for origin block if applicable
        if (MaterialUtil.isTileEntity(originBlock.getType())) {
            logger.finer("Adding tile state to map . . .");
            PacketContainer updatePacket = BlockDataUtil.getUpdatePacket(originBlock.getState());
            if (updatePacket != null) {
                originTileStates.put(originPos, updatePacket);
            }
        }
    }


    private void updateRenderedData(boolean isEdge, boolean isOccluding, BukkitBlockInfo blockInfo, WrappedBlockData backgroundData, BlockData destData) {
        if (isEdge && !isOccluding) {
            blockInfo.setRenderedDestData(backgroundData);
        } else {
            blockInfo.setRenderedDestData(WrappedBlockData.createData(blockRotator.rotateByMatrix(rotateDestToOrigin, destData)));
        }
    }

    private boolean shouldSkipBlock(BlockData destData, BlockData originData, boolean firstUpdate, boolean isEdge) {
        return destData.equals(originData) && firstUpdate && !isEdge;
    }

    @Override
    protected void checkForChanges() {
        List<IViewableBlockInfo> newStates = new ArrayList<>();
        int statesLength = nonObscuredStates.size();

        // Loop through non-obscured states
        for (int i = 0; i < statesLength; i++) {
            BukkitBlockInfo blockInfo = (BukkitBlockInfo) nonObscuredStates.get(i);
            IntVector originPos = blockInfo.getOriginPos();
            IntVector destPos = rotateOriginToDest.transform(originPos.subtract(portalOriginPos)).add(portalDestPos);

            // Fetch destination block data once
            BlockData newDestData = dataFetcher.getData(destPos);
            if (newDestData == null) continue; // Skip if data fetch failed

            // Check for changes at the destination block
            if (!newDestData.equals(blockInfo.getBaseDestData())) {
                logger.finer("Destination block change detected at " + destPos);
                blockInfo.setBaseDestData(newDestData);
                searchFromBlock(originPos, newStates, blockInfo); // Reflood fill if necessary
            }

            // Handle tile entity updates if not cross-server
            if (!portal.isCrossServer() && MaterialUtil.isTileEntity(newDestData.getMaterial())) {
                Block destBlock = destPos.getBlock(Objects.requireNonNull(portal.getDestPos().getWorld()));
                PacketContainer updatePacket = BlockDataUtil.getUpdatePacket(destBlock.getState());
                if (updatePacket != null) {
                    BlockDataUtil.setTileEntityPosition(updatePacket, originPos);
                    destTileStates.put(originPos, updatePacket);
                }
            }

            // Fetch and compare origin block data
            Block originBlock = originPos.getBlock(originWorld);
            BlockData newOriginData = originBlock.getBlockData();

            // Handle origin tile entity updates
            if (MaterialUtil.isTileEntity(originBlock.getType())) {
                PacketContainer updatePacket = BlockDataUtil.getUpdatePacket(originBlock.getState());
                if (updatePacket != null) {
                    originTileStates.put(originPos, updatePacket);
                }
            }

            // Check for changes at the origin block
            if (!newOriginData.equals(blockInfo.getBaseOriginData())) {
                blockInfo.setOriginData(newOriginData);
                if (!newOriginData.equals(newDestData) && !portal.getOriginPos().isInLine(originPos)) {
                    int position = getArrayMapIndex(originPos.subtract(portalOriginPos));
                    // Add to newStates if it's not already marked as viewable
                    if (alreadyReachedMap[position] < 2) {
                        alreadyReachedMap[position] = 2;
                        newStates.add(blockInfo);
                    }
                }
            }
        }

        // Update the tile state maps
        updateTileStateMap(originTileStates, originWorld, false);
        if (!portal.isCrossServer()) {
            updateTileStateMap(destTileStates, portal.getDestPos().getWorld(), true);
        }

        // Enqueue new states if any were found
        if (!newStates.isEmpty()) {
            stateQueue.enqueueStates(newStates);
        }
    }


    private void updateTileStateMap(ConcurrentMap<IntVector, PacketContainer> map, World world, boolean isDestination) {
        for(Map.Entry<IntVector, PacketContainer> entry : map.entrySet()) {
            IntVector position;
            if(isDestination) {
                IntVector portalRelativePos = entry.getKey().subtract(portalOriginPos);
                position = rotateOriginToDest.transform(portalRelativePos).add(portalDestPos);
            }   else    {
                position = entry.getKey();
            }

            Block block = position.getBlock(world);
            BlockState state = block.getState();
            if(!MaterialUtil.isTileEntity(state.getType())) {
                logger.finer("Removing tile state from map . . . %b", isDestination);
                map.remove(entry.getKey());
            }
        }
    }

    @Override
    protected void updateInternal() {
        if(dataFetcher == null) {
            dataFetcher = dataFetcherFactory.create(portal);
        }
        dataFetcher.update();

        // If fetching external blocks has not yet finished, we can't do the flood-fill.
        if(!dataFetcher.isReady()) {
            logger.fine("Not updating portal, data was not yet been fetched");
            return;
        }

        super.updateInternal();
    }

    @Override
    public void reset() {
        dataFetcher = null;
        wrappedLightData = null;
        super.reset();
    }
}