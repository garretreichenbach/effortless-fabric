package dev.huskcasaca.effortless.buildmode.oneclick;

import dev.huskcasaca.effortless.building.BuildOp;
import dev.huskcasaca.effortless.buildmode.OneClickBuildable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Disable extends OneClickBuildable {
    @Override
    public void initialize(Player player) { }

    public BuildOp operationOnUse(Player player) {return null; }
    public BuildOp operationOnAttack(Player player) {return null; }

    @Override
    public boolean isInProgress(Player player) { return false; }

    @Override
    public boolean onUse(Player player, BlockPos blockPos, boolean skipRaytrace) {
        return blockPos != null;
    }

    @Override
    public List<BlockPos> findCoordinates(Player player, BlockPos blockPos, boolean skipRaytrace) {
        if (blockPos == null) return Collections.emptyList();
        return getFinalBlocks(player, blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    @Override
    public List<BlockPos> getFinalBlocks(Player player, int x1, int y1, int z1) {
        List<BlockPos> list = new ArrayList<>();
        list.add(new BlockPos(x1, y1, z1));
        return list;
    }
}
