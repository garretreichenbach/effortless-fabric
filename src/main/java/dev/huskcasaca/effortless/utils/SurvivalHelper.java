package dev.huskcasaca.effortless.utils;

import dev.huskcasaca.effortless.buildmodifier.BuildModifierHelper;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class SurvivalHelper {

    //Used for all placing of blocks in this mod.
    //Checks if area is loaded, if player has the right permissions, if existing block can be replaced (drops it if so) and consumes an item from the stack.
    //Based on ItemBlock#onItemUse
    public static boolean placeBlock(Level level, Player player, BlockPos pos, BlockState blockState, ItemStack origstack) {
        if (!level.isLoaded(pos)) return false;
        ItemStack itemstack = origstack;

        Block block;
        if (!player.isCreative()) {
            // Make sure that given itemStack provides the needed item
            if (blockState.isAir() || itemstack.isEmpty()) {
                dropBlock(level, player, pos);
                level.removeBlock(pos, false);
                return true;
            }

            //Randomizer bag, other proxy item synergy
            //Preliminary compatibility code for other items that hold blocks
            if (CompatHelper.isItemBlockProxy(itemstack))
                itemstack = CompatHelper.getItemBlockByState(itemstack, blockState);

            if (!(itemstack.getItem() instanceof BlockItem))
                return false;
            block = ((BlockItem) itemstack.getItem()).getBlock();
            itemstack.shrink(1);
        }
        else {
            block = blockState.getBlock();
        }

        //More manual with ItemBlock#placeBlockAt
        if (canPlace(player, pos)) {
            //Drop existing block
            dropBlock(level, player, pos);

            //TryPlace sets block with offset and reduces itemstack count in creative, so we copy only parts of it
            if (!level.setBlock(pos, blockState, 3)) return false;
            BlockItem.updateCustomBlockEntityTag(level, player, pos, itemstack); //Actually BlockItem::onBlockPlaced but that is protected
            block.setPlacedBy(level, pos, blockState, player, itemstack);
            if (player instanceof ServerPlayer) {
                CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer) player, pos, itemstack);
                ((ServerPlayer) player).getStats().increment(
                        player,
                        Stats.ITEM_USED.get(itemstack.getItem()),
                        1
                );
            }
            return true;
        }
        return false;
    }

    public static boolean useBlock(Level level, Player player, BlockPos pos, BlockState blockState) {
        if (!level.isLoaded(pos)) return false;
        var itemStack = player.isCreative() ? new ItemStack(blockState.getBlock()) : InventoryHelper.findItemStackInInventory(player, blockState.getBlock());

        // FIXME: 27/12/22
        if (blockState.isAir()) {
            dropBlock(level, player, pos);
            level.removeBlock(pos, false);
            return true;
        }

        if (!(itemStack.getItem() instanceof BlockItem)) return false;
        Block block = ((BlockItem) itemStack.getItem()).getBlock();

        if (!canPlace(player, pos)) {
            return false;
        }
        dropBlock(level, player, pos);

        if (!level.setBlock(pos, blockState, 3)) return false;
        BlockItem.updateCustomBlockEntityTag(level, player, pos, itemStack); //Actually BlockItem::onBlockPlaced but that is protected
        block.setPlacedBy(level, pos, blockState, player, itemStack);
        if (player instanceof ServerPlayer) {
            CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer) player, pos, itemStack);
            ((ServerPlayer) player).getStats().increment(player, Stats.ITEM_USED.get(itemStack.getItem()), 1);
        }

        var afterState = level.getBlockState(pos);

        if (true) {
            var soundtype = afterState.getBlock().getSoundType(afterState);
            level.playSound(null, pos, soundtype.getPlaceSound(), SoundSource.BLOCKS, (soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F);
        }

        if (!player.isCreative() && Block.byItem(itemStack.getItem()) == block) {
            itemStack.shrink(1);
        }

        return true;

    }

    public static boolean breakBlock(Level level, Player player, BlockPos blockPos, boolean skipChecks) {
//        if (!level.isLoaded(blockPos) && !level.isEmptyBlock(blockPos)) return false;
        if (!skipChecks && !canBreak(player, blockPos)) return false;
        //Drop existing block
        if (level.isClientSide()) {
            Minecraft.getInstance().gameMode.destroyBlock(blockPos);
        } else {
            ((ServerPlayer) player).gameMode.destroyBlock(blockPos);
        }
        return true;
    }

    //Gives items directly to player
    public static boolean dropBlock(Level level, Player player, BlockPos pos) {
        if (!(player instanceof ServerPlayer)) return false;
        if (player.isCreative()) return false;
//        if (!level.isLoaded(pos) && !level.isEmptyBlock(pos)) return false;

        var blockState = level.getBlockState(pos);
//        if (blockState.isAir()) return false;

        var block = blockState.getBlock();

        if (!player.hasCorrectToolForDrops(blockState)) {
            return false;
        }

        block.playerDestroy(level, player, pos, blockState, level.getBlockEntity(pos), player.getMainHandItem());
        //TODO drop items in inventory instead of level
        return true;
    }

    public static boolean canPlace(Player player, BlockPos pos) {
        return canSetBlock(player, pos, BuildModifierHelper.isReplace(player));
    }
    //Can break using held tool? (or in creative)
    public static boolean canBreak(Player player, BlockPos pos) {
        return canSetBlock(player, pos, true);
    }
    public static boolean canSetBlock(Player player, BlockPos pos, boolean isReplace) {
        var level = player.level();
        if (!level.mayInteract(player, pos)) return false;
        if (!player.getAbilities().mayBuild) return false;
        if (isReplace) {
            if (player.isCreative()) return true;
            return !level.getBlockState(pos).is(BlockTags.FEATURES_CANNOT_REPLACE); // bedrock or similar
        }
        else {
            return level.getBlockState(pos).canBeReplaced(); // air or fluid
        }
    }

    public static boolean doesBecomeDoubleSlab(Player player, BlockPos pos, Direction facing) {
        ItemStack itemstack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (CompatHelper.isItemBlockProxy(itemstack))
            itemstack = CompatHelper.getItemBlockFromStack(itemstack);

        if (itemstack.isEmpty() || !(itemstack.getItem() instanceof BlockItem) || !(((BlockItem) itemstack.getItem()).getBlock() instanceof SlabBlock heldSlab))
            return false;

        return false;
    }
}
