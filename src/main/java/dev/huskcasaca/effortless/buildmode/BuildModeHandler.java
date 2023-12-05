package dev.huskcasaca.effortless.buildmode;

import dev.huskcasaca.effortless.building.BuildOp;
import dev.huskcasaca.effortless.buildmodifier.BuildModifierHelper;
import dev.huskcasaca.effortless.entity.player.EffortlessDataProvider;
import dev.huskcasaca.effortless.network.Packets;
import dev.huskcasaca.effortless.network.protocol.player.ClientboundPlayerBuildModePacket;
import dev.huskcasaca.effortless.utils.SurvivalHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BuildModeHandler {

    /**
     * Given the blockpos and raytrace hit result, remember click and tell us whether construction is finished.
     * @param player the Player
     * @param blockPos position that was clicked (as per packet)
     * @return True if construction is now finished.
     */
    public static boolean onUse(Player player, BlockPos blockPos, BuildOp operation) {
        var modifierSettings = BuildModifierHelper.getModifierSettings(player);
        var skipRaytrace = modifierSettings.enableQuickReplace() || operation == BuildOp.BREAK;
        return buildable(player).onUse(player, blockPos, skipRaytrace, operation);
    }

    // get current BlockPos set in intermediate state (tracking mouse)
    public static List<BlockPos> findCoordinates(Player player, BlockPos startPos, boolean skipRaytrace) {
        return new ArrayList<>(buildable(player).findCoordinates(player, startPos, skipRaytrace));
    }

    /**
     * Given the coordinates previously returned by findCoordinates, tell us what block
     * to place there. Modes are free to leave out some positions.
     * By default, playersBlockState is placed everywhere.
     * Modes can decide to place something different, e.g. (TODO) place a saved structure,
     * or (TODO) adjust top/bottom placing of slabs to form a nice stair.
     * @param posList Positions where to place
     * @param playersBlockState associated blockstate of players held item, placed at initial conditions.
     * @return map of position to block state.
     */
    public static LinkedHashMap<BlockPos, BlockState> findBlockStates(
            Player player, List<BlockPos> posList, BlockState playersBlockState, BuildOp operation
    ) {
        if (buildable(player) instanceof StructureBuildable structureBuildable) {
            return structureBuildable.findBlockStates(player, posList, playersBlockState, operation);
        }
        else {
            var result = new LinkedHashMap<BlockPos, BlockState>(posList.size());
            var blockState = operation == BuildOp.PLACE ? playersBlockState : Blocks.AIR.defaultBlockState();
            if (blockState == null) return result;
            for (var blockPos : posList) {
                result.put(blockPos, blockState);
            }
            return result;
        }
    }

    public static void initializeMode(Player player) {
        buildable(player).initialize(player);
    }
    public static BuildOp operationOnUse(Player player) {
        return buildable(player).operationOnUse(player);
    }
    public static BuildOp operationOnAttack(Player player) {
        return buildable(player).operationOnAttack(player);
    }
    public static boolean isInProgress(Player player) {
        return buildable(player).isInProgress(player);
    }

    //Find coordinates on a line bound by a plane
    public static Vec3 findXBound(double x, Vec3 start, Vec3 look) {
        //then y and z are
        double y = (x - start.x) / look.x * look.y + start.y;
        double z = (x - start.x) / look.x * look.z + start.z;

        return new Vec3(x, y, z);
    }


    //-- Common build mode functionality --//

    public static Vec3 findYBound(double y, Vec3 start, Vec3 look) {
        //then x and z are
        double x = (y - start.y) / look.y * look.x + start.x;
        double z = (y - start.y) / look.y * look.z + start.z;

        return new Vec3(x, y, z);
    }

    public static Vec3 findZBound(double z, Vec3 start, Vec3 look) {
        //then x and y are
        double x = (z - start.z) / look.z * look.x + start.x;
        double y = (z - start.z) / look.z * look.y + start.y;

        return new Vec3(x, y, z);
    }

    //Use this instead of player.getLookVec() in any buildmodes code
    public static Vec3 getPlayerLookVec(Player player) {
        Vec3 lookVec = player.getLookAngle();
        double x = lookVec.x;
        double y = lookVec.y;
        double z = lookVec.z;

        //Further calculations (findXBound etc) don't like any component being 0 or 1 (e.g. dividing by 0)
        //isCriteriaValid below will take up to 2 minutes to raytrace blocks towards infinity if that is the case
        //So make sure they are close to but never exactly 0 or 1
        if (Math.abs(x) < 0.0001) x = 0.0001;
        if (Math.abs(x - 1.0) < 0.0001) x = 0.9999;
        if (Math.abs(x + 1.0) < 0.0001) x = -0.9999;

        if (Math.abs(y) < 0.0001) y = 0.0001;
        if (Math.abs(y - 1.0) < 0.0001) y = 0.9999;
        if (Math.abs(y + 1.0) < 0.0001) y = -0.9999;

        if (Math.abs(z) < 0.0001) z = 0.0001;
        if (Math.abs(z - 1.0) < 0.0001) z = 0.9999;
        if (Math.abs(z + 1.0) < 0.0001) z = -0.9999;

        return new Vec3(x, y, z);
    }

    public static boolean isCriteriaValid(Vec3 start, Vec3 look, int reach, Player player, boolean skipRaytrace, Vec3 lineBound, Vec3 planeBound, double distToPlayerSq) {
        boolean intersects = false;
        if (!skipRaytrace) {
            //collision within a 1 block radius to selected is fine
            ClipContext rayTraceContext = new ClipContext(start, lineBound, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
            HitResult rayTraceResult = player.level().clip(rayTraceContext);
            intersects = rayTraceResult != null && rayTraceResult.getType() == HitResult.Type.BLOCK &&
                    planeBound.subtract(rayTraceResult.getLocation()).lengthSqr() > 4;
        }

        return planeBound.subtract(start).dot(look) > 0 &&
                distToPlayerSq > 2 && distToPlayerSq < reach * reach &&
                !intersects;
    }

    public static void handleNewPlayer(ServerPlayer player) {
        //Makes sure player has mode settings (if it doesnt it will create it)
        Packets.sendToClient(new ClientboundPlayerBuildModePacket(((EffortlessDataProvider) player).getModeSettings()), player);
    }

    private static Buildable buildable(Player player) {
        return BuildModeHelper.getModeSettings(player).buildMode().getInstance();
    }
}
