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
import net.minecraft.world.phys.BlockHitResult;
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
        var startPos = packet.blockHit() ? packet.blockPos() : null;

        // === if currently in opposite mode, abort ===
        if (currentlyBreaking.get(player) != null && !currentlyBreaking.get(player)) {
            initialize(player);
            return;
        }
        // === Check if player reach does not exceed startpos ===
        int maxReach = ReachHelper.getMaxReachDistance(player);
        if (
                startPos != null
                        && BuildModeHelper.getBuildMode(player) != BuildMode.DISABLE
                        && player.blockPosition().distSqr(startPos) > maxReach * maxReach
        ) {
            Effortless.log(player, "Placement exceeds your reach.");
            return;
        }

        // === check if construction is finished ===
        if (currentlyBreaking.get(player) == null) {
            //If startpos is null, dont do anything
            if (startPos == null) return;
        }

        if (!BuildModeHandler.onUseBreak(player, startPos)) {
            // another click needed - wait for next click
            if (BuildModeHandler.isInProgress(player)) currentlyBreaking.put(player, true);
            return;
        }
        // === go ahead and execute breaking ===
        var coordinates = BuildModeHandler.findCoordinates(player, startPos, true);

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
        UndoRedo.addUndo(player, new BlockSet(modCoordinates, previousBlockStates, newBlockStates, firstPos, secondPos));
        // Action completed.
        initialize(player);
    }

    public static void onBlockPlaced(Player player, ServerboundPlayerPlaceBlockPacket packet) {
        var level = player.level();
        var currentlyBreaking = level.isClientSide ? currentlyBreakingClient : currentlyBreakingServer;
        var startPos = packet.blockHit() ? packet.blockPos(): null;

        // === if currently in opposite mode, abort ===
        if (currentlyBreaking.get(player) != null && currentlyBreaking.get(player)) {
            initialize(player);
            return;
        }
        // === Check if player reach does not exceed startpos ===
        int maxReach = ReachHelper.getMaxReachDistance(player);
        if (
                startPos != null
                && BuildModeHelper.getBuildMode(player) != BuildMode.DISABLE
                && player.blockPosition().distSqr(startPos) > maxReach * maxReach
        ) {
            Effortless.log(player, "Placement exceeds your reach.");
            return;
        }
        // === check if construction is finished ===
        if (!BuildModeHandler.onUsePlace(player, startPos, packet.hitSide())) {
            // action might not have started (invalid startpos)
            if (BuildModeHandler.isInProgress(player)) {
                currentlyBreaking.put(player, false);
                // Remember first hit result, might be required for block state
                hitSideTable.put(player, packet.hitSide());
                hitVecTable.put(player, packet.hitVec());
            }
            return;
        }

        // === go ahead and execute placement ===
        var coordinates = BuildModeHandler.findCoordinates(player, startPos, BuildModifierHelper.isQuickReplace(player));

        // Use Hit side + vec of first click if MultipleClickBuildable.
        var hitSide = hitSideTable.get(player);
        if (hitSide == null) hitSide = packet.hitSide();
        var hitVec = hitVecTable.get(player);
        if (hitVec == null) hitVec = packet.hitVec();

        //Format hitvec to 0.x
        hitVec = new Vec3(Math.abs(hitVec.x - ((int) hitVec.x)), Math.abs(hitVec.y - ((int) hitVec.y)), Math.abs(hitVec.z - ((int) hitVec.z)));

        //find modCoordinates and blockstates
        var modCoordinates = BuildModifierHandler.findCoordinates(player, coordinates);
        var blockStates = BuildModifierHandler.findBlockStates(player, coordinates, hitVec, hitSide);

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

        if (level.isClientSide) BlockPreviewRenderer.getInstance().onBlocksPlaced();

        //place blocks
        for (int i = 0; i < modCoordinates.size(); i++) {
            var blockPos = modCoordinates.get(i);
            var blockState = blockStates.get(blockPos);
            var itemStack = ItemStack.EMPTY;

            if (level.isLoaded(blockPos)) {
                // check if itemstack can provide currently desired block; if not, find another one in players inventory.
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
        for (var coordinate : modCoordinates) newBlockStates.add(level.getBlockState(coordinate));

        //If all new blockstates are air then no use in adding it, no block was actually placed
        //Can happen when e.g. placing one block in yourself
        if (Collections.frequency(newBlockStates, Blocks.AIR.defaultBlockState()) != newBlockStates.size()) {
            //add to undo stack
            var firstPos = coordinates.get(0);
            var secondPos = coordinates.get(coordinates.size() - 1);
            UndoRedo.addUndo(player, new BlockSet(modCoordinates, previousBlockStates, newBlockStates, firstPos, secondPos));
        }
        // Action completed
        initialize(player);
    }

    /**
     * Gets the blocks to be shown as preview, using the current Hit information.
     * Blocks are already filtered by canSetBlock - repeat check will always be True.
     * Result block states don't take into account whether player has enough inventory.
     * If breaking, the blocks to be broken are in previousBlockStates, and all new block states are AIR blocks.
     * @param player current player
     * @param hitResult Where the player is looking at
     * @return BlockSet
     */
    public static BlockSet currentPreview(Player player, BlockHitResult hitResult) {
        var level = player.level();
        //Keep blockstate the same for every block in the buildmode
        //So dont rotate blocks when in the middle of placing wall etc.
        var hitSide = hitResult.getDirection();
        var hitVec = hitResult.getLocation();
        if (isActive(player)) {
            if (getHitSide(player) != null) hitSide = BuildHandler.getHitSide(player);
            if (getHitVec(player) != null) hitVec = BuildHandler.getHitVec(player);
        }

        //Should be red?
        var breaking = BuildHandler.isCurrentlyBreaking(player);

        //get coordinates
        var skipRaytrace = breaking || BuildModifierHelper.isQuickReplace(player);
        var startCoordinates = BuildModeHandler.findCoordinates(player, hitResult.getBlockPos(), skipRaytrace);

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
