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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
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
        onBlockSet(player, packet.blockHit()? packet.blockPos() : null, packet.hitSide(), packet.hitVec(), true);
    }

    public static void onBlockPlaced(Player player, ServerboundPlayerPlaceBlockPacket packet) {
        onBlockSet(player, packet.blockHit()? packet.blockPos() : null, packet.hitSide(), packet.hitVec(), false);
    }

    public static void onBlockSet(Player player, BlockPos startPos, Direction hitSide, Vec3 hitVec, boolean breaking) {
        var level = player.level();
        var currentlyBreaking = level.isClientSide ? currentlyBreakingClient : currentlyBreakingServer;

        // === if currently in opposite mode, abort ===
        if (currentlyBreaking.get(player) != null && (currentlyBreaking.get(player) != breaking)) {
            initialize(player);
            return;
        }
        // === Check if startpos is within player's mod-configured reach distance ===
        int maxReach = ReachHelper.getMaxReachDistance(player);
        if (
                startPos != null
                        && BuildModeHelper.getBuildMode(player) != BuildMode.DISABLE
                        && player.blockPosition().distSqr(startPos) > maxReach * maxReach
        ) {
            Effortless.log(player, "Placement exceeds your reach.");
            return;
        }

        currentlyBreaking.put(player, breaking);

        // === check if construction is finished ===
        startPos = actualPos(player, startPos, hitSide, breaking);
        if (!BuildModeHandler.onUse(player, startPos, breaking)) {
            // action might not have started (invalid startpos)
            if (BuildModeHandler.isInProgress(player)) {
                // Remember first hit result, might be required for block state
                if (hitSideTable.get(player) == null) {
                    hitSideTable.put(player, hitSide);
                    hitVecTable.put(player, hitVec);
                }
            }
            return;
        }
        var blockSet = findBlockSet(player, startPos, hitSide, hitVec);
        //Effortless.log(String.format("Setting %d blocks", blockSet.coordinates().size()));
        if (level.isClientSide) {
            if (breaking)
                BlockPreviewRenderer.getInstance().onBlocksBroken();
            else
                BlockPreviewRenderer.getInstance().onBlocksPlaced();
            initialize(player);
            // Return here to only set blocks on server side - avoids Ghost-block problems,
            // but induces some lag if connected to a remote server.
            //return;
        }

        //break/place all those blocks
        var coordinates = blockSet.coordinates();
        // current inventory stack to deduct items from
        var itemStack = ItemStack.EMPTY;
        for (int i = 0; i < coordinates.size(); i++) {
            var blockPos = coordinates.get(i);
            if (!level.isLoaded(blockPos)) continue;
            var blockState = blockSet.newBlockStates().get(i);
            if (blockState.isAir()) {
                SurvivalHelper.breakBlock(level, player, blockPos, false);
            }
            else {
                // Make sure that the player has matching item for target block
                if (!player.isCreative()) {
                    if (
                            itemStack.isEmpty()
                                    || !(itemStack.getItem() instanceof BlockItem)
                                    || ((BlockItem) itemStack.getItem()).getBlock().equals(blockState.getBlock())
                    ) {
                        // TODO: prefer main hand / off hand slots
                        itemStack = InventoryHelper.findItemStackInInventory(player, blockState.getBlock());
                        // not found, do NOT place the block.
                        if (itemStack.isEmpty()) continue;
                    }
                }
                SurvivalHelper.placeBlock(level, player, blockPos, blockState, itemStack);
            }
        }
        //find actual new blockstates for undo
        List<BlockState> actualNewBlockStates = coordinates.stream().map(pos -> level.getBlockState(pos)).toList();

        UndoRedo.addUndo(player, blockSet.withNewBlockStates(actualNewBlockStates));
        // Action completed.
        initialize(player);
    }

    /**
     * Given the block targeted by the player, return actual corner point position for placement.
     * * If placing, we will place NEXT to the clicked block (face), unless:
     *  a) Quickreplace is on
     *  b) its something breakable like grass
     *  c) (TODO): placing creates a double slab
     *  If Quickreplace is on AND something breakable was targeted, returns the block BELOW.
     *  * If breaking, we will always target the clicked block.
     * @param player Player
     * @param blockPos Block that was clicked
     * @param hitSide Face of the block that was clicked
     * @param breaking Whether player intends to break or place.
     * @return actual block position to use.
     */
    public static BlockPos actualPos(Player player, BlockPos blockPos, Direction hitSide, boolean breaking) {
        var modifierSettings = BuildModifierHelper.getModifierSettings(player);
        // When placing, find out whether we want to offset.
        if (blockPos != null && !breaking) {
            // Place NEXT to clicked block (in given direction), unless QuickReplace is on or block is replaceable.
            boolean replaceable = player.level().getBlockState(blockPos).canBeReplaced();
            // XXX: becomesDoubleSlab is currently always False
            boolean becomesDoubleSlab = SurvivalHelper.doesBecomeDoubleSlab(player, blockPos, hitSide);
            if (!modifierSettings.enableQuickReplace() && !replaceable && !becomesDoubleSlab) {
                blockPos = blockPos.relative(hitSide);
            }

            //Get under tall grass and other replaceable blocks
            if (modifierSettings.enableQuickReplace() && replaceable) {
                blockPos = blockPos.below();
            }
        }
        return blockPos;
    }

    /**
     * Gets the blocks to be changed, using the current Hit information.
     * Blocks are already filtered by canSetBlock - repeat check will always be True.
     * Result block states don't take into account whether player has enough inventory.
     * If breaking, the blocks to be broken are in previousBlockStates, and all new block states are AIR blocks.
     * @param player current player
     * @param blockPos block that was hit, maybe null
     * @param hitSide face of block that was hit
     * @param hitVec where the block was hit
     * @return BlockSet
     */
    public static BlockSet findBlockSet(Player player, BlockPos blockPos, Direction hitSide, Vec3 hitVec) {
        var level = player.level();
        //Keep blockstate the same for every block in the buildmode
        //So dont rotate blocks when in the middle of placing wall etc.
        if (isActive(player)) {
            if (getHitSide(player) != null) hitSide = getHitSide(player);
            if (getHitVec(player) != null) hitVec = getHitVec(player);
        }
        // Don't know where to place anything - return valid but empty blockset.
        if (hitSide==null || hitVec == null) return new BlockSet(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), BlockPos.ZERO, BlockPos.ZERO
        );

        var breaking = isCurrentlyBreaking(player);

        //get coordinates
        var skipRaytrace = breaking || BuildModifierHelper.isQuickReplace(player);
        var startCoordinates = BuildModeHandler.findCoordinates(player, blockPos, skipRaytrace);

        //Remember first and last point for the shader
        var firstPos = startCoordinates.isEmpty() ? BlockPos.ZERO: startCoordinates.get(0);
        var secondPos = startCoordinates.isEmpty() ? BlockPos.ZERO: startCoordinates.get(startCoordinates.size()-1);

        var newCoordinates = BuildModifierHandler.findCoordinates(player, startCoordinates);
        int N = newCoordinates.size();

        hitVec = new Vec3(Math.abs(hitVec.x - ((int) hitVec.x)), Math.abs(hitVec.y - ((int) hitVec.y)), Math.abs(hitVec.z - ((int) hitVec.z)));

        //Get blockstates (old and new)
        List<BlockState> previousBlockStates = newCoordinates.stream().map(pos -> player.level().getBlockState(pos)).toList();
        // Filter: remove if previousBlockState equals newBlockState or if cannot place.
        var isReplace = (breaking || BuildModifierHelper.isReplace(player));
        var filter = new ArrayList<>(newCoordinates.stream().map(pos -> SurvivalHelper.canSetBlock(player, pos, isReplace)).toList());

        Map<BlockPos, BlockState> blockStateMap;
        if (!breaking) {
            blockStateMap = BuildModifierHandler.findBlockStates(player, startCoordinates, hitVec, hitSide);
            // do not place at position i if not in blockStateMap or if already the same block.
            // FIXME: does not consider actual state, e.g. orientation of stairs.
            for (int i=0; i<N; i++) {
                var blockState = blockStateMap.get(newCoordinates.get(i));
                if (blockState==null || previousBlockStates.get(i).getBlock() == blockState.getBlock()) filter.set(i, false);
            }
        }
        else {
            blockStateMap = null;
            // do not "break" things that are already air.
            for (int i=0; i<N; i++)
                if (previousBlockStates.get(i).getBlock() == Blocks.AIR) filter.set(i, false);
            //If the player is going to inst-break grass or a plant, make sure to only break other inst-breakable things
            if (
                !player.isCreative() && previousBlockStates.get(0).getDestroySpeed(level, newCoordinates.get(0)) == 0f
            ) {
                for (int i=0; i<N; i++)
                    if (previousBlockStates.get(i).getDestroySpeed(level, newCoordinates.get(i)) > 0f)
                        filter.set(i, false);
            };
        }

        var filtCoordinates = new ArrayList<BlockPos>(N);
        var filtPreviousBlockStates = new ArrayList<BlockState>(N);
        var filtBlockStates = new ArrayList<BlockState>(N);

        //Limit number of blocks you can place
        int limit = ReachHelper.getMaxBlockPlaceAtOnce(player);
        for (int i=0; i<N; i++) {
            if (filter.get(i)) {
                filtCoordinates.add(newCoordinates.get(i));
                filtPreviousBlockStates.add(previousBlockStates.get(i));
                filtBlockStates.add(breaking ? Blocks.AIR.defaultBlockState() : blockStateMap.get(newCoordinates.get(i)));
            }
            if (--limit <= 0) break;
        }
        return new BlockSet(filtCoordinates, filtPreviousBlockStates, filtBlockStates, firstPos, secondPos);
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