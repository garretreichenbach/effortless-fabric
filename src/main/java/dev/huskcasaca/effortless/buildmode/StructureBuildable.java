package dev.huskcasaca.effortless.buildmode;

import dev.huskcasaca.effortless.building.BuildOp;
import dev.huskcasaca.effortless.buildmode.threeclick.Cube;
import dev.huskcasaca.effortless.entity.player.EffortlessStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class StructureBuildable implements Buildable{

    protected Dictionary<Player, EffortlessStructure[]> structuresServer = new Hashtable<>();
    protected Dictionary<Player, EffortlessStructure[]> structuresClient = new Hashtable<>();

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
        var structure = getCurrentStructure(player);
        // PLACE if a block is saved, otherwise null.
        if (structure == null) return BuildOp.SCAN;
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
        if (structures.get(player) == null) structures.put(player, new EffortlessStructure[9]);
        var playerStructures = structures.get(player);
        int slot = player.getInventory().selected;
        var structure = playerStructures[slot];

        switch (operation) {
            // If operation == PLACE, return true if something is in buffer
            case PLACE -> {
                return structure!=null;
            }
            case BREAK -> {
                // discard scan, return false (nothing actually broken).
                playerStructures[slot] = null;
                return false;
            }
            case SCAN -> {
                // Scan
                var coordinates = cube.findCoordinates(player, blockPos, skipRaytrace);
                if (cube.onUse(player, blockPos, skipRaytrace, BuildOp.BREAK)) {
                    playerStructures[slot] = performScan(player, coordinates);
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
        var offset = getPlaceOffset(player, startPos, facing, structure.size);

        for (var entry: structure.blockStates.entrySet()) {
            var worldPos = entry.getKey().offset(offset);
            var blockState = entry.getValue();
            if (blockState != null && !blockState.isAir()) result.put(worldPos, blockState);
        }
        return result;
    }

    /**
     * Find alignment of structure, i.e. where it should be placed in the world
     * startPos will be one of the corners of the placement;
     * player's look direction and targeted face affect how it will be placed:
     * If player is looking on the ground (facing = UP):
     * "leftward" of the cardinal direction, structure is right-aligned,
     * "rightward" of the cardinal direction, structure is left-aligned.
     * If looking at a vertical face, align against that face.
     * If looking at an upside-down face (facing=DOWN), ground rules apply AND structure is top-aligned.
     * @param player The player
     * @param startPos build position (might be offset to the hit position)
     * @param facing direction of targeted face
     * @param structSize size of structure to build
     * @return Offset position for placing
     */
    private static BlockPos getPlaceOffset(
            Player player, BlockPos startPos, Direction facing, Vec3i structSize
    ) {
        var offset = startPos;
        var direction = player.getDirection();
        // align so that startPos is at the right, viewed from player?
        boolean alignRight = (player.getYRot() % 90.0 + 90.0) % 90.0 >= 45.0;
        // if targeting vertical face, force
        if (facing == direction.getClockWise()) alignRight = false;
        if (facing == direction.getCounterClockWise()) alignRight = true;
        // align away from player?
        boolean alignAway = (facing != direction.getOpposite());

        // unify north/south and east/west cases.
        // i.e. think of the flags as if looking south or east.
        if (direction == Direction.NORTH || direction == Direction.WEST) {
            alignRight = !alignRight;
            alignAway = !alignAway;
        }
        switch (direction) {
            case NORTH, SOUTH -> {
                if (!alignRight) offset = offset.relative(Direction.Axis.X, -structSize.getX() + 1);
                if (!alignAway) offset = offset.relative(Direction.Axis.Z, -structSize.getZ() + 1);
            }
            case EAST, WEST -> {
                if (alignRight) offset = offset.relative(Direction.Axis.Z, -structSize.getZ() + 1);
                if (!alignAway) offset = offset.relative(Direction.Axis.X, -structSize.getX() + 1);
            }
            default -> {}
        }
        // Upside-down face? place below
        if (facing == Direction.DOWN)
            offset = offset.relative(Direction.Axis.Y, -structSize.getY() + 1);
        return offset;
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
        var structure = getCurrentStructure(player);
        if (structure == null) return null;

        var facing = player.getDirection();
        switch (facing) {
            case NORTH -> { return structure.getRotated(Rotation.CLOCKWISE_180); }
            case EAST -> { return structure.getRotated(Rotation.COUNTERCLOCKWISE_90); }
            case WEST -> { return structure.getRotated(Rotation.CLOCKWISE_90); }
            default -> { return structure; }
        }
    }

    @Nullable
    protected EffortlessStructure getCurrentStructure(Player player) {
        var structures = player.level().isClientSide ? structuresClient : structuresServer;
        var playerStructures = structures.get(player);
        if (playerStructures==null) return null;
        return playerStructures[player.getInventory().selected];
    }
}
