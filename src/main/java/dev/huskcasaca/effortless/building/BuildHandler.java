package dev.huskcasaca.effortless.building;

import dev.huskcasaca.effortless.Effortless;
import dev.huskcasaca.effortless.buildmode.BuildMode;
import dev.huskcasaca.effortless.buildmode.BuildModeHandler;
import dev.huskcasaca.effortless.buildmode.BuildModeHelper;
import dev.huskcasaca.effortless.buildmodifier.BlockSet;
import dev.huskcasaca.effortless.buildmodifier.BuildModifierHandler;
import dev.huskcasaca.effortless.buildmodifier.BuildModifierHelper;
import dev.huskcasaca.effortless.buildmodifier.UndoRedo;
import dev.huskcasaca.effortless.network.protocol.player.ServerboundPlayerBreakBlockPacket;
import dev.huskcasaca.effortless.network.protocol.player.ServerboundPlayerPlaceBlockPacket;
import dev.huskcasaca.effortless.render.BlockPreviewRenderer;
import dev.huskcasaca.effortless.utils.InventoryHelper;
import dev.huskcasaca.effortless.utils.SurvivalHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

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
        var level = player.level();
        var currentlyBreaking = level.isClientSide ? currentlyBreakingClient : currentlyBreakingServer;
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
        var modCoordinates = BuildModifierHandler.findCoordinates(player, coordinates);
        //remember previous blockstates for undo
        List<BlockState> previousBlockStates = new ArrayList<>(modCoordinates.size());
        List<BlockState> newBlockStates = new ArrayList<>(modCoordinates.size());
        for (var coordinate : modCoordinates) {
            previousBlockStates.add(level.getBlockState(coordinate));
        }
        if (level.isClientSide) BlockPreviewRenderer.getInstance().onBlocksBroken();

        //If the player is going to inst-break grass or a plant, make sure to only break other inst-breakable things
        boolean onlyInstaBreaking = !player.isCreative() &&
                level.getBlockState(modCoordinates.get(0)).getDestroySpeed(level, modCoordinates.get(0)) == 0f;

        //break all those blocks
        for (int i = 0; i < modCoordinates.size(); i++) {
            var coordinate = modCoordinates.get(i);
            if (level.isLoaded(coordinate) && !level.isEmptyBlock(coordinate)) {
                if (!onlyInstaBreaking || level.getBlockState(coordinate).getDestroySpeed(level, coordinate) == 0f) {
                    SurvivalHelper.breakBlock(level, player, coordinate, false);
                }
            }
        }
        //find actual new blockstates for undo
        for (var coordinate : modCoordinates) newBlockStates.add(level.getBlockState(coordinate));

        //add to undo stack
        var firstPos = modCoordinates.get(0);
        // Does not take modifier-added positions into account. Not critical, because this is
        // only used to adjust speed of the "Placed"/"Broken" animation of the PreviewRenderer.
        var secondPos = coordinates.get(coordinates.size() - 1);
        var hitVec = new Vec3(0.5, 0.5, 0.5);
        UndoRedo.addUndo(player, new BlockSet(modCoordinates, previousBlockStates, newBlockStates, hitVec, firstPos, secondPos));
        // Action completed.
        currentlyBreaking.remove(player);
    }

    public static void onBlockPlaced(Player player, ServerboundPlayerPlaceBlockPacket packet) {
        var level = player.level();
        //Check if not in the middle of breaking
        var currentlyBreaking = level.isClientSide ? currentlyBreakingClient : currentlyBreakingServer;
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

        //Format hitvec to 0.x
        hitVec = new Vec3(Math.abs(hitVec.x - ((int) hitVec.x)), Math.abs(hitVec.y - ((int) hitVec.y)), Math.abs(hitVec.z - ((int) hitVec.z)));

        //find modCoordinates and blockstates
        var modCoordinates = BuildModifierHandler.findCoordinates(player, coordinates);
        var itemStacks = new ArrayList<ItemStack>();
        var blockStates = BuildModifierHandler.findBlockStates(player, coordinates, hitVec, hitSide, itemStacks);

        //Limit number of blocks you can place
        int limit = ReachHelper.getMaxBlockPlaceAtOnce(player);
        if (modCoordinates.size() > limit) {
            for (var pos: modCoordinates.subList(limit, modCoordinates.size())) blockStates.remove(pos);
            modCoordinates = modCoordinates.subList(0, limit);
            // blockStates is a map, the now-superfluous items will just sit there unused.
        }
        // Check that blockStates matches modCoordinates.
        if (modCoordinates.size() != blockStates.size()) return;

        //remember previous blockstates for undo
        var previousBlockStates = new ArrayList<BlockState>(modCoordinates.size());
        var newBlockStates = new ArrayList<BlockState>(modCoordinates.size());
        for (var coordinate : modCoordinates) {
            previousBlockStates.add(level.getBlockState(coordinate));
        }

        // TODO: what is the actual difference between clientside and serverside code path? Possible to unify?
        if (level.isClientSide) {
            BlockPreviewRenderer.getInstance().onBlocksPlaced();
            var blockLeft = new HashMap<Block, Integer>();

            // Place blocks and shrink itemstack.
            for (int i = 0; i < modCoordinates.size(); i++) {
                var blockPos = modCoordinates.get(i);
                var blockState = blockStates.get(blockPos);
                var itemStack = itemStacks.get(i);
                if (!blockLeft.containsKey(blockState.getBlock())) {
                    blockLeft.put(blockState.getBlock(), InventoryHelper.findTotalBlocksInInventory(player, blockState.getBlock()));
                }
                var count = blockLeft.getOrDefault(blockState.getBlock(), 0);
                if (player.isCreative() || count > 0) {
                    if (level.isLoaded(blockPos)) {
                        SurvivalHelper.placeBlock(level, player, blockPos, blockState, itemStack.copy(), hitSide, hitVec, false, false, false);
                        if (!player.isCreative()) blockLeft.put(blockState.getBlock(), count - 1);
                    }
                }
            }
            //find actual new blockstates for undo
            for (var coordinate : modCoordinates) newBlockStates.add(level.getBlockState(coordinate));
        } else {
            //place blocks
            for (int i = 0; i < modCoordinates.size(); i++) {
                var blockPos = modCoordinates.get(i);
                var blockState = blockStates.get(blockPos);
                var itemStack = itemStacks.get(i);

                if (level.isLoaded(blockPos)) {
                    //check itemstack empty
                    if (itemStack.isEmpty()) {
                        //try to find new stack, otherwise continue
                        itemStack = InventoryHelper.findItemStackInInventory(player, blockState.getBlock());
                        if (itemStack.isEmpty()) continue;
                    }
                    SurvivalHelper.placeBlock(level, player, blockPos, blockState, itemStack, hitSide, hitVec, false, false, false);
                }
            }
            //find actual new blockstates for undo
            for (var coordinate : modCoordinates) newBlockStates.add(level.getBlockState(coordinate));
        }

        //If all new blockstates are air then no use in adding it, no block was actually placed
        //Can happen when e.g. placing one block in yourself
        if (Collections.frequency(newBlockStates, Blocks.AIR.defaultBlockState()) != newBlockStates.size()) {
            //add to undo stack
            var firstPos = coordinates.get(0);
            var secondPos = coordinates.get(coordinates.size() - 1);
            UndoRedo.addUndo(player, new BlockSet(modCoordinates, previousBlockStates, newBlockStates, hitVec, firstPos, secondPos));
        }
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
