package dev.huskcasaca.effortless.buildmodifier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public record BlockSet(
        List<BlockPos> coordinates,
        List<BlockState> previousBlockStates,
        List<BlockState> newBlockStates,
        BlockPos firstPos,
        BlockPos secondPos
) {
}
