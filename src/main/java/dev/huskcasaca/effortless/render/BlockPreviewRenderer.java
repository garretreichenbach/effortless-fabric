package dev.huskcasaca.effortless.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.huskcasaca.effortless.Effortless;
import dev.huskcasaca.effortless.EffortlessClient;
import dev.huskcasaca.effortless.building.BuildHandler;
import dev.huskcasaca.effortless.building.BuildOp;
import dev.huskcasaca.effortless.buildmode.BuildMode;
import dev.huskcasaca.effortless.buildmode.BuildModeHelper;
import dev.huskcasaca.effortless.buildmodifier.BlockSet;
import dev.huskcasaca.effortless.buildmodifier.BuildModifierHandler;
import dev.huskcasaca.effortless.config.ConfigManager;
import dev.huskcasaca.effortless.config.PreviewConfig;
import dev.huskcasaca.effortless.utils.CompatHelper;
import dev.huskcasaca.effortless.utils.InventoryHelper;
import dev.huskcasaca.effortless.utils.SurvivalHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static dev.huskcasaca.effortless.render.RenderUtils.*;

@Environment(EnvType.CLIENT)
public class BlockPreviewRenderer {

    private static final BlockPreviewRenderer INSTANCE = new BlockPreviewRenderer(Minecraft.getInstance());
    private final Minecraft minecraft;
    private final List<Preview> lastPlaced = new ArrayList<>();
    private final List<Preview> currentPlacing = new ArrayList<>();
    private List<BlockPos> previousCoordinates;
    private List<BlockState> previousBlockStates;
    private BlockPos previousFirstPos;
    private BlockPos previousSecondPos;
    private boolean lastMessage = false;

    public BlockPreviewRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public static BlockPreviewRenderer getInstance() {
        return INSTANCE;
    }

    //Whether to draw any block previews or outlines
    public static boolean doRenderBlockPreviews(Player player) {
        return ConfigManager.getGlobalPreviewConfig().isAlwaysShowBlockPreview() || (BuildModeHelper.getBuildMode(player) != BuildMode.DISABLE);
    }

    public static List<BlockPosState> getBlockPosStates(List<BlockPos> coordinates, List<BlockState> blockStates) {
        if (coordinates.size() != blockStates.size()) {
            throw new IllegalArgumentException("Coordinates and blockstates must be the same size");
        }
        if (coordinates.isEmpty()) {
            return Collections.emptyList();
        }
        var result = new ArrayList<BlockPosState>();
        for (int i = 0; i < coordinates.size(); i++) {
            var coordinate = coordinates.get(i);
            var blockState = blockStates.get(i);
            if (coordinate == null || blockState == null) {
                throw new IllegalArgumentException("Coordinate or blockstate is null");
            }
            result.add(new BlockPosState(coordinate, blockState, null, null));
        }
        return result;
    }

    public static List<BlockPosState> getBlockPosStates(Player player, List<BlockPos> coordinates, List<BlockState> blockStates) {
        if (coordinates.size() != blockStates.size()) {
            throw new IllegalArgumentException("Coordinates and blockstates must be the same size");
        }
        if (coordinates.isEmpty()) {
            return Collections.emptyList();
        }
        var result = new ArrayList<BlockPosState>();
        for (int i = 0; i < coordinates.size(); i++) {
            var coordinate = coordinates.get(i);
            var blockState = blockStates.get(i);
            if (coordinate == null || blockState == null) {
                throw new IllegalArgumentException("Coordinate or blockstate is null");
            }
            result.add(new BlockPosState(coordinate, blockState, SurvivalHelper.canPlace(player, coordinate), SurvivalHelper.canBreak(player, coordinate)));
        }
        return result;
    }

    public static Map<Block, Integer> getPlayerBlockCount(Player player, List<BlockState> blockStates) {
        var result = new HashMap<Block, Integer>();
        blockStates.forEach(blockState -> {
            result.putIfAbsent(blockState.getBlock(), InventoryHelper.findTotalBlocksInInventory(player, blockState.getBlock()));
        });
        return result;
    }

    private void renderBlockDissolveShader(PoseStack poseStack, MultiBufferSource.BufferSource multiBufferSource, List<BlockPosState> placeData, Map<Block, Integer> blocksLeft, BlockPos firstPos, BlockPos secondPos, float dissolve, BuildOp operation) {
        var player = minecraft.player;
        var dispatcher = minecraft.getBlockRenderer();

        var blockLeft = new HashMap<>(blocksLeft);
        boolean emptyScan = operation == BuildOp.SCAN && placeData.stream().allMatch(blockData -> blockData.blockState.isAir());
        if (emptyScan || placeData.isEmpty()) {
            RenderUtils.renderEmptyBBox(poseStack, multiBufferSource, firstPos, secondPos, EMPTY_BOX_COLOR);
            return;
        }

        for (BlockPosState blockPosState : placeData) {
            var blockPos = blockPosState.coordinate;
            var blockState = blockPosState.blockState;
            var canBreak = blockPosState.canBreak;
            var canPlace = blockPosState.canPlace;

            switch (operation) {
                case BREAK -> {
                    if (canBreak != null && canBreak || SurvivalHelper.canBreak(player, blockPos)) {
                        RenderUtils.renderBlockDissolveShader(poseStack, multiBufferSource, dispatcher, blockPos, blockState, dissolve, firstPos, secondPos, true);
                    }
                }
                case SCAN -> {
                    // Render scanning with the outline shader, to indicate we won't actually harm the blocks.
                    // Do not render air.
                    if (!blockState.isAir())
                        RenderUtils.renderBlockOutlines(poseStack, multiBufferSource, blockPos, blockState, SCAN_OUTLINE_COLOR);
                }
                case DRENCH -> {
                    // Render with the outline shader, since blocks are hard to see otherwise.
                    RenderUtils.renderBlockOutlines(poseStack, multiBufferSource, blockPos, blockState, DRENCH_OUTLINE_COLOR);
                }
                default -> {
                    if (canPlace != null && canPlace || SurvivalHelper.canPlace(player, blockPosState.coordinate)) {
                        if (player.isCreative()) {
                            RenderUtils.renderBlockDissolveShader(poseStack, multiBufferSource, dispatcher, blockPos, blockState, dissolve, firstPos, secondPos, false);
                            continue;
                        }
                        var count = blockLeft.getOrDefault(blockState.getBlock(), 0);
                        if (count > 0) {
                            RenderUtils.renderBlockDissolveShader(poseStack, multiBufferSource, dispatcher, blockPos, blockState, dissolve, firstPos, secondPos, false);
                            blockLeft.put(blockState.getBlock(), count - 1);
                        } else {
                            RenderUtils.renderBlockDissolveShader(poseStack, multiBufferSource, dispatcher, blockPos, blockState, dissolve, firstPos, secondPos, true);
                        }
                    }
                }
            }
        }
    }

    private void renderBlockOutlines(PoseStack poseStack, MultiBufferSource.BufferSource multiBufferSource, List<BlockPosState> placeData, Map<Block, Integer> blocksLeft, BlockPos firstPos, BlockPos secondPos, float dissolve, BuildOp operation) {
        var player = minecraft.player;
        var dispatcher = minecraft.getBlockRenderer();

        if (placeData.isEmpty()) return;

        var blockLeft = new HashMap<>(blocksLeft);

        for (BlockPosState blockPosState : placeData) {
            var blockPos = blockPosState.coordinate;
            var blockState = blockPosState.blockState;
            var canBreak = blockPosState.canBreak;
            var canPlace = blockPosState.canPlace;

            switch (operation) {
                case BREAK -> {
                    if (canBreak != null && canBreak || SurvivalHelper.canBreak(player, blockPos)) {
                        RenderUtils.renderBlockOutlines(poseStack, multiBufferSource, blockPos, blockState, BREAK_OUTLINE_COLOR);
                    }
                }
                case SCAN -> {
                    RenderUtils.renderBlockOutlines(poseStack, multiBufferSource, blockPos, blockState, SCAN_OUTLINE_COLOR);
                }
                case DRENCH -> {
                    RenderUtils.renderBlockOutlines(poseStack, multiBufferSource, blockPos, blockState, DRENCH_OUTLINE_COLOR);
                }
                default -> {
                    if (canPlace != null && canPlace || SurvivalHelper.canPlace(player, blockPosState.coordinate)) {
                        if (player.isCreative()) {
                            RenderUtils.renderBlockOutlines(poseStack, multiBufferSource, blockPos, blockState, PLACE_OUTLINE_COLOR);
                            continue;
                        }
                        var count = blockLeft.getOrDefault(blockState.getBlock(), 0);
                        if (count > 0) {
                            RenderUtils.renderBlockOutlines(poseStack, multiBufferSource, blockPos, blockState, PLACE_OUTLINE_COLOR);
                            blockLeft.put(blockState.getBlock(), count - 1);
                        } else {
                            RenderUtils.renderBlockOutlines(poseStack, multiBufferSource, blockPos, blockState, ERROR_OUTLINE_COLOR);
                        }
                    }
                }
            }
        }
    }

    private UseResult getBlockUseResult(List<BlockPosState> placeData, Map<Block, Integer> blocksLeft, BuildOp operation) {
        if (placeData.isEmpty()) {
            return UseResult.EMPTY;
        }
        var player = minecraft.player;

        var valid = new HashMap<Block, Integer>();
        var total = new HashMap<Block, Integer>();

        var left = new HashMap<>(blocksLeft);

        for (BlockPosState placeDatum : placeData) {
            var blockPos = placeDatum.coordinate;
            var blockState = placeDatum.blockState;

            switch (operation) {
                case BREAK -> {
                    var canBreak = SurvivalHelper.canBreak(player, blockPos);
                    if (canBreak) {
                        valid.put(blockState.getBlock(), valid.getOrDefault(blockState.getBlock(), 0) + 1);
                        total.put(blockState.getBlock(), total.getOrDefault(blockState.getBlock(), 0) + 1);
                    }
                }
                case SCAN -> {
                    valid.put(blockState.getBlock(), valid.getOrDefault(blockState.getBlock(), 0) + 1);
                    total.put(blockState.getBlock(), total.getOrDefault(blockState.getBlock(), 0) + 1);
                }
                case DRENCH -> {
                    var playersBlock = BuildHandler.getPlayersBlock(player);
                    if (playersBlock==null) playersBlock = Blocks.AIR;
                    if (player.isCreative()
                            || playersBlock.equals(Blocks.AIR)
                            || playersBlock.equals(Blocks.WATER
                    )) {
                        valid.put(playersBlock, valid.getOrDefault(playersBlock, 0) + 1);
                    }
                    else {
                        var count = left.getOrDefault(playersBlock, 0);
                        if (count > 0) {
                            left.put(playersBlock, count - 1);
                            valid.put(playersBlock, valid.getOrDefault(playersBlock, 0) + 1);
                        }
                    }
                    total.put(playersBlock, total.getOrDefault(playersBlock, 0) + 1);
                }
                default -> {
                    var canPlace = SurvivalHelper.canPlace(player, placeDatum.coordinate);
                    if (canPlace) {
                        if (player.isCreative()) {
                            valid.put(blockState.getBlock(), valid.getOrDefault(blockState.getBlock(), 0) + 1);
                        } else {
                            var count = left.getOrDefault(blockState.getBlock(), 0);
                            if (count > 0) {
                                left.put(blockState.getBlock(), count - 1);
                                valid.put(blockState.getBlock(), valid.getOrDefault(blockState.getBlock(), 0) + 1);
                            }
                        }
                        total.put(blockState.getBlock(), total.getOrDefault(blockState.getBlock(), 0) + 1);
                    }
                }
            }
        }

        return new UseResult(valid, total);
    }

    private static void sortOnDistanceToPlayer(List<BlockPos> coordinates, Player player) {

        coordinates.sort((lhs, rhs) -> {
            // -1 - less than, 1 - greater than, 0 - equal
            double lhsDistanceToPlayer = Vec3.atLowerCornerOf(lhs).subtract(player.getEyePosition(1f)).lengthSqr();
            double rhsDistanceToPlayer = Vec3.atLowerCornerOf(rhs).subtract(player.getEyePosition(1f)).lengthSqr();
            return (int) Math.signum(lhsDistanceToPlayer - rhsDistanceToPlayer);
        });

    }

    public void render(Player player, PoseStack poseStack, MultiBufferSource.BufferSource multiBufferSource, Camera camera) {
        //Render placed blocks with dissolve effect
        //Use fancy shader if config allows, otherwise no dissolve
        if (PreviewConfig.useShader()) {
            for (Preview placed : lastPlaced) {
                if (placed.blockPosStates() != null && !placed.blockPosStates().isEmpty()) {
                    double totalTime = Mth.clampedLerp(30, 60, placed.firstPos.distSqr(placed.secondPos) / 100.0) * PreviewConfig.shaderDissolveTimeMultiplier();
                    float dissolve = (EffortlessClient.getTicksInGame() - placed.time) / (float) totalTime;
                    renderBlockDissolveShader(poseStack, multiBufferSource, placed.blockPosStates(), placed.useResult().valid(), placed.firstPos, placed.secondPos, dissolve, placed.operation);
                }
            }
        }
        //Expire
        currentPlacing.clear();
        lastPlaced.removeIf(placed -> {
            double totalTime = Mth.clampedLerp(30, 60, placed.firstPos.distSqr(placed.secondPos) / 100.0) * PreviewConfig.shaderDissolveTimeMultiplier();
            return placed.time + totalTime < EffortlessClient.getTicksInGame();
        });

        //Render block previews
        HitResult lookingAt = EffortlessClient.getLookingAt(player);
        if (BuildModeHelper.getBuildMode(player) == BuildMode.DISABLE) {
            lookingAt = minecraft.hitResult;
        }

        ItemStack mainhand = player.getMainHandItem();
        boolean toolInHand = !(!mainhand.isEmpty() && CompatHelper.isItemBlockProxy(mainhand));

        var operation = BuildHandler.currentOperation(player);

        BlockHitResult blockLookingAt = null;
        //Checking for null is necessary! Even in vanilla when looking down ladders it is occasionally null (instead of Type MISS)
        if (lookingAt != null && lookingAt.getType() == HitResult.Type.BLOCK) {
            blockLookingAt = (BlockHitResult) lookingAt;
            var startPos = BuildHandler.actualPos(
                    player,
                    blockLookingAt.getBlockPos(),
                    blockLookingAt.getDirection()
            );
            blockLookingAt = blockLookingAt.withPosition(startPos);
        }

        //Dont render if in normal mode and modifiers are disabled
        //Unless alwaysShowBlockPreview is true in config
        if (!doRenderBlockPreviews(player)) {
            clearActionBarMessage(player);
            return;
        }
        BlockSet previewData;
        if (blockLookingAt == null)
            previewData = BuildHandler.findBlockSet(player, null, null, null);
        else
            previewData = BuildHandler.findBlockSet(player, blockLookingAt.getBlockPos(), blockLookingAt.getDirection(), blockLookingAt.getLocation());

        // Todo: pass the whole BlockSet around instead of splitting everything up.
        var newCoordinates = previewData.coordinates();
        var blockStates = (operation== BuildOp.BREAK || operation == BuildOp.SCAN)
                ? previewData.previousBlockStates()
                : previewData.newBlockStates();
        var firstPos = previewData.firstPos();
        var secondPos = previewData.secondPos();

        //Check if they are different from previous
        previousCoordinates = newCoordinates;
        //remember the rest for placed blocks
        previousBlockStates = previewData.newBlockStates();
        previousFirstPos = previewData.firstPos();
        previousSecondPos = previewData.secondPos();

        //Render block previews

        //Use fancy shader if config allows, otherwise outlines
        switch (ConfigManager.getGlobalPreviewConfig().getBlockPreviewMode()) {
            case OUTLINES -> renderBlockOutlines(poseStack, multiBufferSource, getBlockPosStates(newCoordinates, blockStates), getPlayerBlockCount(player, blockStates), firstPos, secondPos, 0f, operation);
            case DISSOLVE_SHADER -> renderBlockDissolveShader(poseStack, multiBufferSource, getBlockPosStates(newCoordinates, blockStates), getPlayerBlockCount(player, blockStates), firstPos, secondPos, 0f, operation);
        }

        var placeResult = getBlockUseResult(getBlockPosStates(newCoordinates, blockStates), getPlayerBlockCount(player, blockStates), operation);

        currentPlacing.add(new Preview(getBlockPosStates(player, newCoordinates, blockStates), placeResult, firstPos, secondPos, EffortlessClient.getTicksInGame(), BuildOp.PLACE));

        if (!BuildHandler.isActive(player)) {
            clearActionBarMessage(player);
            return;
        }
        //Display block count and dimensions in actionbar
        BlockPos posDelta;
        if (previewData.firstPos()!= null && previewData.secondPos()!= null)
            posDelta = previewData.secondPos().subtract(previewData.firstPos());
        else
            posDelta = BlockPos.ZERO;

        String dimensions = "(";
        if (posDelta.getX() != 0) dimensions += (Math.abs(posDelta.getX())+1) + "x";
        if (posDelta.getZ() != 0) dimensions += (Math.abs(posDelta.getZ())+1) + "x";
        if (posDelta.getY() != 0) dimensions += (Math.abs(posDelta.getY())+1) + "x";
        dimensions = dimensions.substring(0, dimensions.length() - 1);
        if (dimensions.length() > 1) dimensions += ")";

        var blockCounter = "" + ChatFormatting.WHITE + placeResult.validBlocks() + ChatFormatting.RESET + (placeResult.isFilled() ? " " : " + " + ChatFormatting.RED + placeResult.wantedBlocks() + ChatFormatting.RESET + " ") + (placeResult.totalBlocks() == 1 ? "block" : "blocks");

        displayActionBarMessage(player, "%s%s%s of %s %s".formatted(ChatFormatting.GOLD, BuildModeHelper.getTranslatedModeOptionName(player), ChatFormatting.RESET, blockCounter, dimensions));

    }

    private void displayActionBarMessage(Player player, String message) {
        Effortless.log(player, message, true);
        lastMessage = true;
    }

    private void clearActionBarMessage(Player player) {
        if (lastMessage) {
            Effortless.log(player, "", true);
            lastMessage = false;
        }
    }

    public void onBlocksPlaced() {
        onBlocksPlaced(previousCoordinates, previousBlockStates, previousFirstPos, previousSecondPos);
    }

    public void onDrenched() {
        // TODO: other animation for this?
        onBlocksPlaced(previousCoordinates, previousBlockStates, previousFirstPos, previousSecondPos);
    }

    public void onBlocksPlaced(List<BlockPos> coordinates, List<BlockState> blockStates, BlockPos firstPos, BlockPos secondPos) {
        var player = minecraft.player;

        if (!doRenderBlockPreviews(player)) {
            return;
        }
        if (coordinates != null && blockStates != null && !coordinates.isEmpty() && blockStates.size() == coordinates.size() && coordinates.size() > 1/*  && coordinates.size() < PreviewConfig.shaderThresholdRounded() */) {
            lastPlaced.add(new Preview(
                    getBlockPosStates(player, coordinates, blockStates),
                    getBlockUseResult(
                            getBlockPosStates(coordinates, blockStates),
                            getPlayerBlockCount(player, blockStates),
                            BuildOp.PLACE
                    ),
                    firstPos,
                    secondPos,
                    EffortlessClient.getTicksInGame(),
                    BuildOp.PLACE
            ));
        }
    }

    public void onBlocksBroken() {
        onBlocksBroken(previousCoordinates, previousBlockStates, previousFirstPos, previousSecondPos);
    }

    public void onBlocksBroken(List<BlockPos> coordinates, List<BlockState> blockStates,
                               BlockPos firstPos, BlockPos secondPos) {
        var player = minecraft.player;

        if (doRenderBlockPreviews(player)) {
            return;
        }
        if (coordinates != null && blockStates != null && !coordinates.isEmpty() && blockStates.size() == coordinates.size() && coordinates.size() > 1/*  && coordinates.size() < PreviewConfig.shaderThresholdRounded() */) {
//                sortOnDistanceToPlayer(coordinates, player);
            lastPlaced.add(new Preview(
                    getBlockPosStates(coordinates, blockStates),
                    getBlockUseResult(
                            getBlockPosStates(coordinates, blockStates),
                            getPlayerBlockCount(player, blockStates),
                            BuildOp.BREAK
                    ),
                    firstPos,
                    secondPos,
                    EffortlessClient.getTicksInGame(),
                    BuildOp.BREAK
            ));
        }

    }

    public void onBlocksScanned() {
        // No visualization yet
    }

    public List<Preview> getLastPlaced() {
        return lastPlaced;
    }

    public List<Preview> getCurrentPlacing() {
        return currentPlacing;
    }

    record BlockPosState(
            BlockPos coordinate,
            BlockState blockState,
            Boolean canPlace,
            Boolean canBreak
    ) { }

    public record Preview(
            List<BlockPosState> blockPosStates,
            UseResult useResult,
            BlockPos firstPos,
            BlockPos secondPos,
            float time,
            BuildOp operation
    ) {

        public List<ItemStack> getValidItemStacks() {
            var result = new ArrayList<ItemStack>();
            useResult.valid.forEach((block, count) -> {
                // FIXME: 31/12/22
                if (block.equals(Blocks.AIR) || block.equals(Blocks.WATER)) return;
                while (count > 0) {
                    Item item;
                    if (block.equals(Blocks.LAVA)) item = Items.LAVA_BUCKET;
                    else if (block.equals(Blocks.POWDER_SNOW)) item = Items.POWDER_SNOW_BUCKET;
                    else item = block.asItem();
                    var itemStack = new ItemStack(item);
                    if (itemStack.getMaxStackSize() <= 0) continue;
                    var used = count > BlockItem.MAX_STACK_SIZE ? BlockItem.MAX_STACK_SIZE : count;
                    itemStack.setCount(used);
                    count -= used;
                    result.add(itemStack);
                }
            });
            return result;
        }

        public List<ItemStack> getInvalidItemStacks() {
            var result = new ArrayList<ItemStack>();
            useResult.total.forEach((block, count) -> {
                if (block.equals(Blocks.AIR) || block.equals(Blocks.WATER)) return;
                count = count - useResult.valid.getOrDefault(block, 0);
                while (count > 0) {
                    Item item;
                    if (block.equals(Blocks.LAVA)) item = Items.LAVA_BUCKET;
                    else if (block.equals(Blocks.POWDER_SNOW)) item = Items.POWDER_SNOW_BUCKET;
                    else item = block.asItem();
                    var itemStack = new ItemStack(item);
                    if (itemStack.getMaxStackSize() <= 0) continue;
                    var used = count > BlockItem.MAX_STACK_SIZE ? BlockItem.MAX_STACK_SIZE : count;
                    itemStack.setCount(used);
                    count -= used;
                    result.add(itemStack);
                }
            });
            return result;
        }

    }

    record UseResult(
            Map<Block, Integer> valid,
            Map<Block, Integer> total
    ) {

        public static UseResult EMPTY = new UseResult(Collections.emptyMap(), Collections.emptyMap());

        public int validBlocks() {
            return valid.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int totalBlocks() {
            return total.values().stream().mapToInt(Integer::intValue).sum();
        }

        public boolean isFilled() {
            return validBlocks() == totalBlocks();
        }

        public int wantedBlocks() {
            return totalBlocks() - validBlocks();
        }
    }

}
