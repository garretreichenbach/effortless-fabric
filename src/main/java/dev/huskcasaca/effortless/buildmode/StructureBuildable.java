package dev.huskcasaca.effortless.buildmode;

import dev.huskcasaca.effortless.Effortless;
import dev.huskcasaca.effortless.building.BuildOp;
import dev.huskcasaca.effortless.buildmode.threeclick.Cube;
import dev.huskcasaca.effortless.entity.player.EffortlessStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.NotImplementedException;

import java.util.*;

public class StructureBuildable implements Buildable{

    protected Dictionary<Player, EffortlessStructure> structuresServer = new Hashtable<>();
    protected Dictionary<Player, EffortlessStructure> structuresClient = new Hashtable<>();

    // Use cube mode internally to provide the scan action.
    final static Cube cube = (Cube) BuildMode.CUBE.getInstance();

    @Override
    public void initialize(Player player) {
        // Do not reset structures, since we want to keep it even if player switches
        // out and back into the mode.
        cube.initialize(player);
    }

    @Override
    public BuildOp operationOnUse(Player player) {
        var structures = player.level().isClientSide ? structuresClient : structuresServer;
        // PLACE if a block is saved, otherwise null.
        if (structures.get(player) == null) return BuildOp.SCAN;
        return BuildOp.PLACE;
    }
    @Override
    public BuildOp operationOnAttack(Player player) { return BuildOp.BREAK; }
    public boolean isInProgress(Player player) {
        // "in progress" state only active when scanning. Delegate to cube mode.
        return cube.isInProgress(player);
    }

    @Override
    public boolean onUse(Player player, BlockPos blockPos, boolean skipRaytrace, BuildOp operation) {
        var structures = player.level().isClientSide ? structuresClient : structuresServer;
        switch (operation) {
            // If operation == PLACE, return true if something is in buffer
            case PLACE -> {
                return structures.get(player) != null;
            }
            case BREAK -> {
                // discard scan, return false (nothing actually broken).
                structures.remove(player);
                return false;
            }
            case SCAN -> {
                // Scan
                var coordinates = cube.findCoordinates(player, blockPos, skipRaytrace);
                if (cube.onUse(player, blockPos, skipRaytrace, BuildOp.BREAK)) {
                    structures.put(player, performScan(player, coordinates));
                    return true;
                }
                else return false;
            }
            default -> { return false; }
        }
    }

    @Override
    public List<BlockPos> findCoordinates(Player player, BlockPos blockPos, boolean skipRaytrace) {
        // Can't sensibly find coordinates because we're missing info that findBlockStates needs.
        throw new NotImplementedException();
    }

    public LinkedHashMap<BlockPos, BlockState> findBlockStates(
            Player player, BlockPos startPos, Vec3 hitVec, Direction facing, BuildOp operation
    ) {
        var result = new LinkedHashMap<BlockPos, BlockState>();
        var structure = getRotatedStructure(player);
        if (structure == null) {
            // Scan mode: return whatever cube mode gives us.
            for (var pos: cube.findCoordinates(player, startPos, true))
                result.put(pos, Blocks.AIR.defaultBlockState());
            return result;
        }
        // Return blocks of the structure offset by given blockPos
        if (startPos == null) return result;

        for (var entry: structure.blockStates.entrySet()) {
            var worldPos = entry.getKey().offset(startPos);
            var blockState = entry.getValue();
            if (blockState != null && !blockState.isAir()) result.put(worldPos, blockState);
        }
        return result;
    }

    protected EffortlessStructure performScan(Player player, List<BlockPos> coordinates) {
        if (coordinates.isEmpty()) return EffortlessStructure.empty();
        int minX = coordinates.stream().mapToInt(Vec3i::getX).min().getAsInt();
        int minY = coordinates.stream().mapToInt(Vec3i::getY).min().getAsInt();
        int minZ = coordinates.stream().mapToInt(Vec3i::getZ).min().getAsInt();
        var offset = new BlockPos(minX, minY, minZ);

        var blockStates = new LinkedHashMap<BlockPos, BlockState>(coordinates.size());
        for (var pos: coordinates) {
            var blockState = player.level().getBlockState(pos);
            if (!blockState.isAir()) {
                blockStates.put(pos.subtract(offset), blockState);
            }
        }
        var structure = new EffortlessStructure(blockStates);
        // de-rotate using players current view direction
        // Minecraft's "zero" direction is SOUTH, stick to it.
        var facing = player.getDirection();
        switch (facing) {
            case NORTH -> { return structure.getRotated(Rotation.CLOCKWISE_180); }
            case EAST -> { return structure.getRotated(Rotation.CLOCKWISE_90); }
            case WEST -> { return structure.getRotated(Rotation.COUNTERCLOCKWISE_90); }
            default -> { return structure; }
        }
    }

    protected EffortlessStructure getRotatedStructure(Player player) {
        var structures = player.level().isClientSide ? structuresClient : structuresServer;
        var structure = structures.get(player);
        if (structure==null) return null;

        var facing = player.getDirection();
        switch (facing) {
            case NORTH -> { return structure.getRotated(Rotation.CLOCKWISE_180); }
            case EAST -> { return structure.getRotated(Rotation.COUNTERCLOCKWISE_90); }
            case WEST -> { return structure.getRotated(Rotation.CLOCKWISE_90); }
            default -> { return structure; }
        }
    }
}
