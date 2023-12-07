package dev.huskcasaca.effortless.entity.player;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * Holds data about a structure that can be placed into the world.
 *
 * We don't use Minecraft's builtin structure system because 1. it is way to complex for what we need, and
 * 2. we gain independence from changes in the MC codebase. The Block API is much less likely to change
 * than the structure stuff.
 */
public class EffortlessStructure {

    /**
     * Bounding box size of the structure.
     */
    final public Vec3i size;
    final public ImmutableMap<BlockPos, BlockState> blockStates;

    /**
     * Constructor.
     * BlockState map must be relative (coordinates counting from 0 upward).
     * Preferably give an ordered map like LinkedHashMap. Order is preserved.
     * If blockStates is empty, size is (1,1,1), otherwise max(coordinate)+1 for each axis.
     * @param blockStates Block state map.
     */
    public EffortlessStructure(Map<BlockPos, BlockState> blockStates) {
        this.blockStates = ImmutableMap.copyOf(blockStates);
        if (blockStates.isEmpty())
            this.size = new Vec3i(1, 1, 1);
        else {
            int maxX = blockStates.keySet().stream(). mapToInt(Vec3i::getX).max().getAsInt();
            int maxY = blockStates.keySet().stream(). mapToInt(Vec3i::getY).max().getAsInt();
            int maxZ = blockStates.keySet().stream(). mapToInt(Vec3i::getZ).max().getAsInt();
            this.size = new Vec3i(maxX+1, maxY+1, maxZ+1);
        }
    }

    /**
     * @return A structure of 1x1x1 containing nothing.
     */
    public static EffortlessStructure empty() {
        return new EffortlessStructure(
                (new ImmutableMap.Builder<BlockPos, BlockState>()).build()
        );
    }

    public EffortlessStructure getRotated(Rotation rotation) {
        var newBlockStates = new ImmutableMap.Builder<BlockPos, BlockState>();
        var z1 = size.getZ() - 1;
        var x1 = size.getX() - 1;
        switch (rotation) {
            case NONE -> { return this; }
            case CLOCKWISE_90 -> {
                for (var entry: this.blockStates.entrySet()) {
                    var pos = entry.getKey();
                    var newPos = new BlockPos(z1-pos.getZ(), pos.getY(), pos.getX());
                    newBlockStates.put(newPos, entry.getValue().rotate(rotation));
                }
            }
            case CLOCKWISE_180 -> {
                for (var entry: this.blockStates.entrySet()) {
                    var pos = entry.getKey();
                    var newPos = new BlockPos(x1-pos.getX(), pos.getY(), z1-pos.getZ());
                    newBlockStates.put(newPos, entry.getValue().rotate(rotation));
                }
            }
            case COUNTERCLOCKWISE_90 -> {
                for (var entry: this.blockStates.entrySet()) {
                    var pos = entry.getKey();
                    var newPos = new BlockPos(pos.getZ(), pos.getY(), x1-pos.getX());
                    newBlockStates.put(newPos, entry.getValue().rotate(rotation));
                }
            }
        }
        return new EffortlessStructure(newBlockStates.build());
    }
}