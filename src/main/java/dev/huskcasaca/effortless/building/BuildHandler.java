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
import dev.huskcasaca.effortless.utils.CompatHelper;
import dev.huskcasaca.effortless.utils.InventoryHelper;
import dev.huskcasaca.effortless.utils.SurvivalHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static dev.huskcasaca.effortless.building.BuildOp.*;

public class BuildHandler {
    private record BuildState(BuildOp operation, Direction hitSide, Vec3 hitVec, BlockState blockState) {

    }
    //Static variables are shared between client and server in singleplayer
    //We need them separate
    private static final Dictionary<Player, BuildState> currentStateClient = new Hashtable<>();
    private static final Dictionary<Player, BuildState> currentStateServer = new Hashtable<>();

    /**
     * Reset:
     *  current operation,
     *  Blockstate+Hit result when operation started,
     *  intermediate block positions
     * @param player the Player
     */
    public static void initialize(Player player) {
        //Resetting mode, so not placing or breaking
        if (player == null) {
            return;
        }
        var currentState = player.level().isClientSide ? currentStateClient : currentStateServer;
        currentState.remove(player);
        BuildModeHandler.initializeMode(player);
    }
    public static void onBlockBroken(Player player, ServerboundPlayerBreakBlockPacket packet) {
        onBlockSet(player, packet.blockHit() ? packet.blockPos() : null, packet.hitSide(), packet.hitVec(), true);
    }

    public static void onBlockPlaced(Player player, ServerboundPlayerPlaceBlockPacket packet) {
        onBlockSet(player, packet.blockHit() ? packet.blockPos() : null, packet.hitSide(), packet.hitVec(), false);
    }

    public static void onBlockSet(Player player, BlockPos startPos, Direction hitSide, Vec3 hitVec, boolean isAttack) {
        var level = player.level();
        var currentState = level.isClientSide ? currentStateClient : currentStateServer;
        var operation = isAttack
                ? BuildModeHandler.operationOnAttack(player)
                : BuildModeHandler.operationOnUse(player);

        // === if invalid operation or currently in other mode, abort ===
        if (
            operation == null
            || (
                    currentState.get(player) != null
                    && currentState.get(player).operation != operation
            )
        ) {
            initialize(player);
            return;
        }
        // === Check if startpos is within player's mod-configured reach distance ===
        int maxReach = ReachHelper.getMaxReachDistance(player);
        if (
                startPos != null
                        && operation != SCAN
                        && BuildModeHelper.getBuildMode(player) != BuildMode.DISABLE
                        && player.blockPosition().distSqr(startPos) > maxReach * maxReach
        ) {
            Effortless.log(player, "Placement exceeds your reach.");
            return;
        }

        // If starting construction, remember hit details and block to place.
        if (currentState.get(player) == null) {
            var block = getPlayersBlock(player);
            var blockState = getBlockStateWhenPlaced(player, block, startPos, hitSide, hitVec);
            currentState.put(player, new BuildState(operation, hitSide, hitVec, blockState));
        }

        // === check if construction is finished ===
        startPos = actualPos(player, startPos, hitSide);
        var blockSet = findBlockSet(player, startPos, hitSide, hitVec);

        // Tells us if we ought to place the blockset
        var commitChange = BuildModeHandler.onUse(player, startPos, operation);

        if (commitChange && level.isClientSide) {
            switch(operation) {
                case BREAK -> BlockPreviewRenderer.getInstance().onBlocksBroken();
                case PLACE -> BlockPreviewRenderer.getInstance().onBlocksPlaced();
                case SCAN -> BlockPreviewRenderer.getInstance().onBlocksScanned();
            }
        }
        // add &&!level.isClientSide to only set blocks on server. Avoids ghost blocks but induces lag.
        if (commitChange && operation != SCAN) {
            //Effortless.log(String.format("Setting %d blocks", blockSet.coordinates().size()));
            // current inventory stack to deduct items from
            var itemStack = ItemStack.EMPTY;
            for (int i = 0; i < blockSet.size(); i++) {
                var blockPos = blockSet.coordinates().get(i);
                if (!level.isLoaded(blockPos)) continue;
                var blockState = blockSet.newBlockStates().get(i);
                if (blockState.isAir()) {
                    SurvivalHelper.breakBlock(level, player, blockPos, false);
                } else {
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
            List<BlockState> actualNewBlockStates = blockSet.coordinates().stream().map(pos -> level.getBlockState(pos)).toList();
            UndoRedo.addUndo(player, blockSet.withNewBlockStates(actualNewBlockStates));
        }
        // Action completed?
        if (!BuildModeHandler.isInProgress(player)) initialize(player);
    }

    /**
     * Given the block targeted by the player, return actual corner point position for placement.
     * * If placing, we will place NEXT to the clicked block (face), unless:
     *  a) Quickreplace is on
     *  b) its something breakable like grass
     *  If Quickreplace is on AND something breakable was targeted, returns the block BELOW.
     *  * If breaking, we will always target the clicked block.
     * @param player Player
     * @param blockPos Block that was clicked
     * @param hitSide Face of the block that was clicked
     * @return actual block position to use.
     */
    public static BlockPos actualPos(Player player, BlockPos blockPos, Direction hitSide) {
        var modifierSettings = BuildModifierHelper.getModifierSettings(player);
        var operation = currentOperation(player);
        // When placing, find out whether we want to offset.
        if (blockPos != null && operation != BREAK) {
            // Place NEXT to clicked block (in given direction), unless QuickReplace is on or block is replaceable.
            boolean replaceable = player.level().getBlockState(blockPos).canBeReplaced();
            // TODO: handle completion of slab to double-slab.
            if (!modifierSettings.enableQuickReplace() && !replaceable) {
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

        var operation = currentOperation(player);

        //get coordinates
        var skipRaytrace = (operation == BREAK) || BuildModifierHelper.isQuickReplace(player);
        var startCoordinates = BuildModeHandler.findCoordinates(player, blockPos, skipRaytrace);

        // Don't know where to place anything - return valid but empty blockset.
        if (startCoordinates.isEmpty()) return new BlockSet(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), BlockPos.ZERO, BlockPos.ZERO
        );

        //Remember first and last point for the shader
        var firstPos = startCoordinates.get(0);
        var secondPos = startCoordinates.get(startCoordinates.size() - 1);

        var newCoordinates = BuildModifierHandler.findCoordinates(player, startCoordinates);
        int N = newCoordinates.size();

        //Get blockstates (old and new)
        List<BlockState> previousBlockStates = newCoordinates.stream().map(pos -> player.level().getBlockState(pos)).toList();
        // Filter: remove if previousBlockState equals newBlockState or if cannot place.
        var isReplace = (operation != PLACE || BuildModifierHelper.isReplace(player));
        var filter = new ArrayList<>(newCoordinates.stream().map(pos -> SurvivalHelper.canSetBlock(player, pos, isReplace)).toList());

        Map<BlockPos, BlockState> blockStateMap;
        blockStateMap = findBlockStates(player, startCoordinates, hitVec, hitSide);
        // do not place at position i if not in blockStateMap or if already the same block.
        // FIXME: does not consider actual state, e.g. orientation of stairs.
        if (operation != SCAN) {
            for (int i = 0; i < N; i++) {
                var blockState = blockStateMap.get(newCoordinates.get(i));
                if (blockState == null || previousBlockStates.get(i).getBlock() == blockState.getBlock())
                    filter.set(i, false);
            }
        }
        //If the player is going to inst-break grass or a plant, make sure to only break other inst-breakable things
        if (
                operation == BREAK
                && !player.isCreative()
                && previousBlockStates.get(0).getDestroySpeed(level, newCoordinates.get(0)) == 0f
        ) {
            for (int i=0; i<N; i++)
                if (previousBlockStates.get(i).getDestroySpeed(level, newCoordinates.get(i)) > 0f)
                    filter.set(i, false);
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
                filtBlockStates.add(blockStateMap.get(newCoordinates.get(i)));
            }
            if (--limit <= 0) break;
        }
        return new BlockSet(filtCoordinates, filtPreviousBlockStates, filtBlockStates, firstPos, secondPos);
    }

    public static Map<BlockPos, BlockState> findBlockStates(Player player, List<BlockPos> posList, Vec3 hitVec, Direction facing) {
        var currentState = player.level().isClientSide ? currentStateClient : currentStateServer;
        if (posList.isEmpty()) return new LinkedHashMap<>();
        var operation = currentOperation(player);

        BlockState playersBlockState;
        if (currentState.get(player) != null) {
            // Action in progress, use blockstate as remembered on construction start.
            playersBlockState = currentState.get(player).blockState;
        }
        else {
            // Idly looking around, use current data.
            var block = getPlayersBlock(player);
            //hitVec = new Vec3(Math.abs(hitVec.x - ((int) hitVec.x)), Math.abs(hitVec.y - ((int) hitVec.y)), Math.abs(hitVec.z - ((int) hitVec.z)));
            playersBlockState = getBlockStateWhenPlaced(player, block, posList.get(0), facing, hitVec);
        }

        var blockStates = BuildModeHandler.findBlockStates(player, posList, playersBlockState, operation);
        var modBlockStates = BuildModifierHandler.findBlockStates(player, blockStates);
        // TODO Adjust blockstates for torches and ladders etc to place on a valid side
        return modBlockStates;
    }

    public static Block getPlayersBlock(Player player) {
        //Get itemstack - either a BlockItem or a proxy (container) that provides Items (e.g. RandomizerBag)
        ItemStack itemStack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (itemStack.isEmpty() || !CompatHelper.isItemBlockProxy(itemStack)) {
            itemStack = player.getItemInHand(InteractionHand.OFF_HAND);
        }
        if (itemStack.isEmpty() || !CompatHelper.isItemBlockProxy(itemStack)) {
            return null;
        }
        // TODO: Randomizer support: detect whether item is a shulker chest.
        // We would need to return a list of blocks with count, so that prob's can be set.
        var block = Block.byItem(itemStack.getItem());
        return block == Blocks.AIR ? null : block;
    }

    public static BlockState getBlockStateWhenPlaced(Player player, Block block, BlockPos blockPos, Direction facing, Vec3 hitVec) {
        if (block == null) return null;
        var hand = InteractionHand.MAIN_HAND;
        // If aiming nowhere in particular, return default state.
        if (facing == null || hitVec == null || blockPos == null) return block.defaultBlockState();
        var blockHitResult = new BlockHitResult(hitVec, facing, blockPos, false);
        var itemStack = new ItemStack(block.asItem());
        var context = new BlockPlaceContext(player.level(), player, hand, itemStack, blockHitResult);
        return block.getStateForPlacement(context);
    }

    /**
     * Returns player's current operation.
     * If in idle state, will return first non-null from the mode's Use operation, its
     * Attack operation, or PLACE.
     * @param player Player to query
     * @return operation that player is performing
     */
    public static BuildOp currentOperation(Player player) {
        var currentState = player.level().isClientSide ? currentStateClient : currentStateServer;
        var defaultOp = BuildModeHandler.operationOnUse(player);
        if (defaultOp==null) defaultOp = BuildModeHandler.operationOnAttack(player);
        if (defaultOp==null) defaultOp = PLACE;
        return (currentState.get(player) != null) ? currentState.get(player).operation : defaultOp;
    }

    /**
     * @param player Player to query
     * @return true if player is in the middle of a multistep place or break action.
     */
    public static boolean isActive(Player player) {
        var currentState = player.level().isClientSide ? currentStateClient : currentStateServer;
        return currentState.get(player) != null;
    }
}
