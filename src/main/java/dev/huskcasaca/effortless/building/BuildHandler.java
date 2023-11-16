package dev.huskcasaca.effortless.building;

import dev.huskcasaca.effortless.Effortless;
import dev.huskcasaca.effortless.buildmode.BuildMode;
import dev.huskcasaca.effortless.buildmode.BuildModeHandler;
import dev.huskcasaca.effortless.buildmode.BuildModeHelper;
import dev.huskcasaca.effortless.buildmodifier.BuildModifierHandler;
import dev.huskcasaca.effortless.buildmodifier.BuildModifierHelper;
import dev.huskcasaca.effortless.network.protocol.player.ServerboundPlayerBreakBlockPacket;
import dev.huskcasaca.effortless.network.protocol.player.ServerboundPlayerPlaceBlockPacket;
import dev.huskcasaca.effortless.utils.SurvivalHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.UUID;

public class BuildHandler {
    //Static variables are shared between client and server in singleplayer
    //We need them separate
    // Entry = false -> player is currently placing, wait for next click
    // Entry = true -> player is currently breaking, wait for next click
    // no Entry for player -> no operation in progress
    private static final Dictionary<Player, Boolean> currentlyBreakingClient = new Hashtable<>();
    private static final Dictionary<Player, Boolean> currentlyBreakingServer = new Hashtable<>();

    // Remembers the hit data of the first click, if multiple-click buildable.
    private static final Dictionary<Player, Direction> hitSideTable = new Hashtable<>();
    private static final Dictionary<Player, Vec3> hitVecTable = new Hashtable<>();

    public static void initialize(Player player) {
        //Resetting mode, so not placing or breaking
        if (player == null) {
            return;
        }
        var currentlyBreaking = player.level().isClientSide ? currentlyBreakingClient : currentlyBreakingServer;
        currentlyBreaking.remove(player);
        hitSideTable.remove(player);
        hitVecTable.remove(player);

        BuildModeHandler.initializeMode(player);
    }

    public static void onBlockBroken(Player player, ServerboundPlayerBreakBlockPacket packet) {
        var currentlyBreaking = player.level().isClientSide ? currentlyBreakingClient : currentlyBreakingServer;
        // if currently in opposite mode, abort
        if (currentlyBreaking.get(player) != null && !currentlyBreaking.get(player)) {
            initialize(player);
            return;
        }
        if (!ReachHelper.isCanBreakFar(player)) return;

        var startPos = packet.blockHit() ? packet.blockPos() : null;
        //If first click
        if (currentlyBreaking.get(player) == null) {
            //If startpos is null, dont do anything
            if (startPos == null) return;
        }

        var coordinates = BuildModeHandler.getBreakCoordinates(player, startPos);
        if (coordinates.isEmpty()) {
            // another click needed - wait for next click
            currentlyBreaking.put(player, true);
            return;
        }
        //Let buildmodifiers break blocks
        BuildModifierHandler.onBlockBroken(player, coordinates, true);
        // Action completed.
        currentlyBreaking.remove(player);
    }

    public static void onBlockPlaced(Player player, ServerboundPlayerPlaceBlockPacket packet) {
        //Check if not in the middle of breaking
        var currentlyBreaking = player.level().isClientSide ? currentlyBreakingClient : currentlyBreakingServer;
        // if currently in opposite mode, abort
        if (currentlyBreaking.get(player) != null && currentlyBreaking.get(player)) {
            initialize(player);
            return;
        }

        var coordinates = BuildModeHandler.getPlaceCoordinates(
                player, packet.blockHit()? packet.blockPos(): null, packet.hitSide()
        );

        // invalid click
        if (coordinates == null) return;
        if (coordinates.isEmpty()) {
            // another click needed - wait for next click
            currentlyBreaking.put(player, false);
            // Remember first hit result, might be required for block state
            hitSideTable.put(player, packet.hitSide());
            hitVecTable.put(player, packet.hitVec());
            return;
        }

        // Use Hit side + vec of first click if MultipleClickBuildable.
        var hitSide = hitSideTable.get(player);
        if (hitSide == null) hitSide = packet.hitSide();
        var hitVec = hitVecTable.get(player);
        if (hitVec == null) hitVec = packet.hitVec();

        BuildModifierHandler.onBlockPlaced(player, coordinates, hitSide, hitVec, packet.placeStartPos());
        // Action completed
        currentlyBreaking.remove(player);
    }

    /**
     * @param player Player to query
     * @return true if player is in the middle of a multistep break action.
     */
    public static boolean isCurrentlyBreaking(Player player) {
        var currentlyBreaking = player.level().isClientSide ? currentlyBreakingClient : currentlyBreakingServer;
        return currentlyBreaking.get(player) != null && currentlyBreaking.get(player);
    }

    /**
     * @param player Player to query
     * @return true if player is in the middle of a multistep place or break action.
     */
    public static boolean isActive(Player player) {
        var currentlyBreaking = player.level().isClientSide ? currentlyBreakingClient : currentlyBreakingServer;
        return currentlyBreaking.get(player) != null;
    }

    /**
     * Hit Direction of first click
     * @param player Player to track
     * @return Direction, may be null
     */
    public static Direction getHitSide(Player player) {
        return hitSideTable.get(player);
    }

    /**
     * Hit Vector of first click
     * @param player Player to track
     * @return Vector, may be null
     */
    public static Vec3 getHitVec(Player player) {
        return hitVecTable.get(player);
    }

}
