package dev.huskcasaca.effortless.buildmode;

import dev.huskcasaca.effortless.Effortless;
import dev.huskcasaca.effortless.entity.player.EffortlessDataProvider;
import dev.huskcasaca.effortless.buildmodifier.BuildModifierHandler;
import dev.huskcasaca.effortless.buildmodifier.BuildModifierHelper;
import dev.huskcasaca.effortless.building.ReachHelper;
import dev.huskcasaca.effortless.utils.SurvivalHelper;
import dev.huskcasaca.effortless.network.Packets;
import dev.huskcasaca.effortless.network.protocol.player.ClientboundPlayerBuildModePacket;
import dev.huskcasaca.effortless.network.protocol.player.ServerboundPlayerBreakBlockPacket;
import dev.huskcasaca.effortless.network.protocol.player.ServerboundPlayerPlaceBlockPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

public class BuildModeHandler {

    /**
     * Given the blockpos and raytrace hit result, Tell us where blocks should be placed in current mode.
     * Stateful: Might only return sensible data after multiple clicks (i.e. calls).
     * @param player
     * @param blockPos
     * @param hitSide
     * @return list of block positions; empty list if needing another click; null if invalid click.
     */
    public static List<BlockPos> getPlaceCoordinates(Player player, BlockPos blockPos, Direction hitSide) {
        var modifierSettings = BuildModifierHelper.getModifierSettings(player);
        var modeSettings = BuildModeHelper.getModeSettings(player);
        var buildMode = modeSettings.buildMode();
        BlockPos startPos = null;
        // Find actual start pos
        if (blockPos != null) {
            startPos = blockPos;
            //Offset in direction of hitSide if not quickreplace and not replaceable
            boolean replaceable = player.level().getBlockState(startPos).canBeReplaced();
            boolean becomesDoubleSlab = SurvivalHelper.doesBecomeDoubleSlab(player, startPos, hitSide);
            if (!modifierSettings.enableQuickReplace() && !replaceable && !becomesDoubleSlab) {
                startPos = startPos.relative(hitSide);
            }

            //Get under tall grass and other replaceable blocks
            if (modifierSettings.enableQuickReplace() && replaceable) {
                startPos = startPos.below();
            }

            //Check if player reach does not exceed startpos
            int maxReach = ReachHelper.getMaxReachDistance(player);
            if (buildMode != BuildMode.DISABLE && player.blockPosition().distSqr(startPos) > maxReach * maxReach) {
                Effortless.log(player, "Placement exceeds your reach.");
                return null;
            }
        }

        //Even when no starting block is found, call buildmode instance
        //We might want to place things in the air
        var skipRaytrace = modifierSettings.enableQuickReplace();
        return buildMode.getInstance().onUse(player, startPos, skipRaytrace);
    }

    /**
     * Given the startPos, tell us which blocks will be broken.
     * Stateful: Might only return sensible data after multiple clicks (i.e. calls).
     * @param player
     * @param startPos
     * @return list of block positions; empty list if needing another click; null if invalid click.
     */
    public static List<BlockPos> getBreakCoordinates(Player player, BlockPos startPos) {
        var modeSettings = BuildModeHelper.getModeSettings(player);

        //Get coordinates
        var buildMode = modeSettings.buildMode();
        return buildMode.getInstance().onUse(player, startPos, true);
    }

    // get current BlockPos set in intermediate state (tracking mouse)
    public static List<BlockPos> findCoordinates(Player player, BlockPos startPos, boolean skipRaytrace) {
        List<BlockPos> coordinates = new ArrayList<>();

        var modeSettings = BuildModeHelper.getModeSettings(player);
        coordinates.addAll(modeSettings.buildMode().getInstance().findCoordinates(player, startPos, skipRaytrace));

        return coordinates;
    }

    public static void initializeMode(Player player) {
        BuildModeHelper.getModeSettings(player).buildMode().getInstance().initialize(player);
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
}
