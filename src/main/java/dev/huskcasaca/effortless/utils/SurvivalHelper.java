package dev.huskcasaca.effortless.utils;

import dev.huskcasaca.effortless.Effortless;
import dev.huskcasaca.effortless.buildmodifier.BuildModifierHelper;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class SurvivalHelper {

    //Used for all placing of blocks in this mod.
    //Checks if area is loaded, if player has the right permissions, if existing block can be replaced (drops it if so) and consumes an item from the stack.
    //Based on ItemBlock#onItemUse
    public static boolean placeBlock(Level level, Player player, BlockPos pos, BlockState blockState, ItemStack origstack) {
        if (!level.isLoaded(pos)) return false;
        ItemStack itemstack = origstack;

        var oldBlock = level.getBlockState(pos).getBlock();
        Block block = blockState.getBlock();
        // Deduct inventory item unless (a) creative or (b) replacing block with itself
        if (!block.equals(oldBlock) &&!player.isCreative()) {
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
            itemstack.shrink(1);
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

    /**
     * If survival, check that player has correct bucket for drenching with given blockstate.
     * <p>
     * If picking up, use BucketPickup.pickupBlock - unless it's a nonpickable liquid
     * block (Kelp, Seagrass) - in this case, just destroy. Different from vanilla behavior
     * where you just cannot remove those by bucket.
     * <p>
     * Convert bucket if not picking water, using ItemUtils.CreateFilledResult;
     * trigger: Stats.ITEM_USED; gameevent.FLUID_PICKUP; CriteriaTriggers.FILLED_BUCKET
     * <p>
     * If placing, first check for dimension.ultrawarm & water. If so, refuse.
     * If LiquidContainer and canPlaceLiquid and is water, use placeLiquid.
     * Otherwise, place using SetBlock.
     * <p>
     * Convert bucket if placing lava / snow, giving empty bucket to the player.
     * trigger: awardStats(itemUsed); CriteriaTriggers.Placed_block
     *
     * @param level the level
     * @param player the player
     * @param pos where to place the liquid
     * @param blockState Block corresponding to liquid to be placed. AIR to remove liquid.
     * @param itemStack Matching ItemStack from players inventory.
     */
    public static void drenchBlock(Level level, Player player, BlockPos pos, BlockState blockState, ItemStack itemStack) {
        if (!level.isLoaded(pos)) return;
        var item = itemStack.getItem();
        var oldBlockState = level.getBlockState(pos);
        var oldBlock = oldBlockState.getBlock();
        var block = blockState.getBlock();
        //Effortless.log(String.format("Drench block: item=%s, oldBlock=%s, new block=%s", itemStack.getDescriptionId(), oldBlock.getDescriptionId(), block.getDescriptionId()));
        if (block.equals(Blocks.AIR)) {
            // Pickup mode
            if (oldBlock instanceof BucketPickup bucketPickup) {
                ItemStack newItemStack = bucketPickup.pickupBlock(level, pos, oldBlockState);
                if (!newItemStack.isEmpty() && !newItemStack.getItem().equals(Items.WATER_BUCKET)) {
                    itemStack.shrink(1);
                    if (!player.getInventory().add(newItemStack)) {
                        player.drop(newItemStack, false);
                    }
                    if (player instanceof ServerPlayer serverPlayer)
                        CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, newItemStack);
                }
            }
            else if (oldBlock instanceof LiquidBlockContainer) {
                // Something that normally cannot be picked by bucket, i.e. Seagrass or Kelp. Destroy.
                level.destroyBlock(pos, true);
                level.setBlock(pos, block.defaultBlockState(), 3);
            }
            level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
            player.awardStat(Stats.ITEM_USED.get(item));
        }
        else {
            // Place mode
            boolean isWater = block.equals(Blocks.WATER);
            if ( level.dimensionType().ultraWarm() && isWater)
                // Do not trigger any effects/sounds, since there might be dozens of blocks.
                return;

            if (oldBlock instanceof LiquidBlockContainer liquidBlockContainer) {
                if (isWater) {
                    liquidBlockContainer.placeLiquid(level, pos, oldBlockState, Fluids.WATER.defaultFluidState());
                }
            }
            else {
                level.destroyBlock(pos, true);
                level.setBlock(pos, blockState, 11);
            }
            if (!itemStack.isEmpty() && !player.getAbilities().instabuild && !isWater) {
                itemStack.shrink(1);
                var newItemStack = new ItemStack(Items.BUCKET);
                if (!player.getInventory().add(newItemStack))
                    player.drop(newItemStack, false);
            }
            if (player instanceof  ServerPlayer serverPlayer)
                CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, pos, itemStack);
            level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
            player.awardStat(Stats.ITEM_USED.get(item));
        }
    }

    public static boolean useBlock(Level level, Player player, BlockPos pos, BlockState blockState) {
        if (!level.isLoaded(pos)) return false;
        var itemStack = player.isCreative()
                ? new ItemStack(blockState.getBlock())
                : InventoryHelper.findItemStackInInventory(player, null, blockState.getBlock());

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
}
