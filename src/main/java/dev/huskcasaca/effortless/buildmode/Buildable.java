package dev.huskcasaca.effortless.buildmode;

import dev.huskcasaca.effortless.building.BuildOp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public interface Buildable {

    /**
     * Perform initialization after build mode change and/or after finishing construction.
     * @param player the Player
     */
    void initialize(Player player);

    /**
     * The operation that will be performed on USE click
     * @param player The Player
     * @return BuildOperation, null if forbidden
     */
    BuildOp operationOnUse(Player player);
    /**
     * The operation that will be performed on ATTACK click
     * @param player The Player
     * @return BuildOperation, null if forbidden
     */
    BuildOp operationOnAttack(Player player);

    /**
     * Tells us if a construction is ongoing for player
     * @param player
     * @return true if in the middle of a build operation
     */
    boolean isInProgress(Player player);

    /**
     * Called to register click at the given position.
     * Result tells whether construction is finished (coordinates final).
     * @param player the Player
     * @param blockPos blockPos that was clicked
     * @param skipRaytrace
     * @return true if construction is finished, false if not
     */
    boolean onUse(Player player, BlockPos blockPos, boolean skipRaytrace);

    //Fired continuously for visualization purposes
    List<BlockPos> findCoordinates(Player player, BlockPos blockPos, boolean skipRaytrace);
}
