package org.envel.betterportals.bukkit.block.bukkit;

import com.comphenix.protocol.wrappers.WrappedBlockData;
import org.envel.betterportals.api.IntVector;
import org.envel.betterportals.bukkit.block.IViewableBlockInfo;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.block.data.BlockData;

public class BukkitBlockInfo implements IViewableBlockInfo {
    @Getter private final IntVector originPos;

    @Getter private BlockData baseOriginData;
    @Getter @Setter private BlockData baseDestData;
    @Getter private WrappedBlockData originData;
    @Getter private WrappedBlockData renderedDestData;

    public BukkitBlockInfo(IntVector originPos, BlockData originData, BlockData destData) {
        this.originPos = originPos;
        this.baseOriginData = originData;
        this.baseDestData = destData;
        this.originData = WrappedBlockData.createData(originData);
    }

    public void setOriginData(BlockData originData) {
        this.baseOriginData = originData;
        this.originData = WrappedBlockData.createData(originData);
    }

    public void setRenderedDestData(WrappedBlockData destData) {
        this.renderedDestData = destData;
    }
}
