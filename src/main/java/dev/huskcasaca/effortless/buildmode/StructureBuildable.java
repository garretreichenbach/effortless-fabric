package dev.huskcasaca.effortless.buildmode;

import dev.huskcasaca.effortless.building.BuildOp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class StructureBuildable implements Buildable{

    protected Dictionary<Player, BlockState> structureServer = new Hashtable<>();
    protected Dictionary<Player, BlockState> structureClient = new Hashtable<>();

    @Override
    public void initialize(Player player) {
        // Do not reset savedBlock, since we want to keep it even if player switches
        // out and back into the mode.
    }

    @Override
    public BuildOp operationOnUse(Player player) {
        var structure = player.level().isClientSide ? structureClient : structureServer;
        // PLACE if a block is saved, otherwise null.
        if (structure.get(player) == null) return null;
        return BuildOp.PLACE;
    }
    @Override
    public BuildOp operationOnAttack(Player player) { return BuildOp.SCAN; }
    public boolean isInProgress(Player player) { return false; }

    @Override
    public boolean onUse(Player player, BlockPos blockPos, boolean skipRaytrace, BuildOp operation) {
        var structure = player.level().isClientSide ? structureClient : structureServer;
        switch (operation) {
            // If operation == PLACE, return true if something is in buffer
            case PLACE -> {
                return structure.get(player) != null;
            }
            case SCAN -> {
                //    if buffer is not empty, discard scan.
                if (structure.get(player) != null) {
                    // Discard
                    structure.remove(player);
                    return false;
                }
                //    if buffer is empty, begin / proceed / finish scan (True if finished).
                else {
                    // Scan
                    var blockState = player.level().getBlockState(blockPos);
                    // TODO:
                    // * scan multiple blocks
                    // * undo rotation
                    structure.put(player, blockState);
                    return true;
                }
            }
            default -> { return false; }
        }
    }

    @Override
    public List<BlockPos> findCoordinates(Player player, BlockPos blockPos, boolean skipRaytrace) {
        if (blockPos == null) return Collections.emptyList();
        return getFinalBlocks(player, blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    public List<BlockPos> getFinalBlocks(Player player, int x1, int y1, int z1) {
        List<BlockPos> list = new ArrayList<>();
        list.add(new BlockPos(x1, y1, z1));
        return list;
    }
    public LinkedHashMap<BlockPos, BlockState> findBlockStates(
            Player player, List<BlockPos> posList, BlockState playersBlockState, BuildOp operation
    ) {
        var structure = player.level().isClientSide ? structureClient : structureServer;
        var result = new LinkedHashMap<BlockPos, BlockState>(posList.size());
        var blockState = structure.get(player);
        if (blockState == null) return result;
        for (var blockPos : posList) {
            result.put(blockPos, blockState);
        }
        return result;
    }
}
