package org.envel.betterportals.bukkit.block.lighting;

import com.comphenix.protocol.wrappers.WrappedBlockData;
import org.envel.betterportals.bukkit.config.RenderConfig;
import org.envel.betterportals.bukkit.portal.IPortal;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.type.Light;
import org.jetbrains.annotations.Nullable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class LightDataManger implements ILightDataManager   {
    private final RenderConfig renderConfig;

    @Inject
    public LightDataManger(RenderConfig renderConfig) {
        this.renderConfig = renderConfig;
    }

    private int getLightLevel(IPortal portal) {
        if(renderConfig.getForceLightLevel() >= 0) {
            return renderConfig.getForceLightLevel();
        }

        World destWorld = portal.getDestPos().getWorld();
        if(destWorld == null) {
            return -1;
        }

        World.Environment destEnv = destWorld.getEnvironment();
        if(destEnv == World.Environment.NETHER) {
            return 8; // Set a dim light by default in the nether (avoids nether in caves showing as too dark)
        }   else if(destEnv == World.Environment.NORMAL) {
            // If close-ish to the surface
            if(portal.getDestPos().getVector().getY() > 64.0) {
                long time = destWorld.getTime();
                if (time > 0 && time < 12300) {
                    return 15; // Set to 15 during day time
                } else {
                    return -1; // Disable lighting during night time
                }
            }   else    {
                return -1; // No lighting needed in caves
            }
        }

        return -1;
    }

    @Override
    public @Nullable WrappedBlockData getLightData(IPortal portal) {
        int lightLevel = getLightLevel(portal);

        if(lightLevel == -1) {
            return null;
        }   else    {
            Light lightBlockData = (Light) Bukkit.createBlockData(Material.LIGHT);
            lightBlockData.setLevel(lightLevel);
            return WrappedBlockData.createData(lightBlockData);
        }
    }
}
