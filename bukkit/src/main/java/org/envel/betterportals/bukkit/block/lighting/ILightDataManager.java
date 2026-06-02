package org.envel.betterportals.bukkit.block.lighting;

import com.comphenix.protocol.wrappers.WrappedBlockData;
import org.envel.betterportals.bukkit.portal.IPortal;
import org.jetbrains.annotations.Nullable;

public interface ILightDataManager {
    /**
     * Finds the light block data to use for the given portal position
     * @param portal Portal to determine the light block necessary
     * @return The light data to use to simulate the lighting, or null if simulation is not necessary
     */
    @Nullable WrappedBlockData getLightData(IPortal portal);
}
