package dev.huskcasaca.effortless.utils;

import dev.huskcasaca.effortless.Effortless;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;

public class InventoryHelper {

    /**
     * Returns the first-best item stack in player's inventory that can supply the given block.
     * @param player The player to search
     * @param itemStack The ItemStack that was previously used. If it matches, it
     *                  will be immediately returned.
     * @param block Block to find as item
     * @return Reference to ItemStack, ItemStack.EMPTY if not found.
     */
    public static ItemStack findItemStackInInventory(Player player, ItemStack itemStack, Block block) {
        if (itemStack==null) itemStack = ItemStack.EMPTY;
        // "Bucket" blocks
        if (block.equals(Blocks.AIR)
                || block.equals(Blocks.WATER)
                || block.equals(Blocks.LAVA)
                || block.equals(Blocks.POWDER_SNOW)
        )
            return InventoryHelper.findBucket(player, itemStack, block);
        // check if given ItemStack matches
        if (!itemStack.isEmpty()) {
            var item = itemStack.getItem();
            if (item instanceof BlockItem blockItem && blockItem.getBlock().equals(block))
                return itemStack;
        }
        // Scan inventory.
        for (ItemStack invStack : player.getInventory().items) {
            if (!invStack.isEmpty()
                    && invStack.getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock().equals(block)
            ) return invStack;
        }
        return ItemStack.EMPTY;
    }

    public static boolean holdingBucket(Player player, boolean includeSolidBucketItem) {
        var item = player.getMainHandItem().getItem();
        return (
                item instanceof BucketItem)
                || item instanceof MilkBucketItem
                || (includeSolidBucketItem && (item instanceof SolidBucketItem)
        );
    }

    /**
     * By looking at players block state (block the player wants to place), decide
     * what kind of bucket is required, and find in inventory.
     * @param player The player
     * @param itemStack The ItemStack that was previously used. If it matches, it
     *                  will be immediately returned.
     * @param block that should be placed.
     * @return Itemstack found in players inventory, or ItemStack.EMPTY.
     */
    public static ItemStack findBucket(Player player, ItemStack itemStack, Block block) {
        // use correct bucket for placing lava and powder snow
        if (block.equals(Blocks.LAVA))
            return findBucket(player, itemStack, Items.LAVA_BUCKET);
        else if (block.equals(Blocks.POWDER_SNOW))
            return findBucket(player, itemStack, Items.POWDER_SNOW_BUCKET);
        else if (block.equals(Blocks.WATER)) {
            var stack = findBucket(player, itemStack, Items.WATER_BUCKET);
            // also accept milk bucket, if no water bucket is there
            if (!stack.isEmpty()) return stack;
            return findBucket(player, itemStack, Items.MILK_BUCKET);
        }
        else if (block.equals(Blocks.AIR))
            return findBucket(player, itemStack, Items.BUCKET);
        else {
            Effortless.log("Player is drenching with unexpected block");
            return ItemStack.EMPTY;
        }
    }

    public static ItemStack findBucket(Player player, ItemStack itemStack, Item bucketItem) {
        if (!itemStack.isEmpty() && itemStack.getItem().equals(bucketItem))
            return itemStack;
        for (ItemStack invStack : player.getInventory().items) {
            if (!invStack.isEmpty() && invStack.getItem().equals(bucketItem))
                return invStack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Counts how many items corresponding to given block are in player's inventory.
     * @param player The player to search
     * @param block Block to find as item
     * @return Total count of the item in player's inventory.
     */
    public static int findTotalBlocksInInventory(Player player, Block block) {
        int total = 0;
        if (block.equals(Blocks.WATER) || block.equals(Blocks.AIR))
            return Integer.MAX_VALUE;
        for (ItemStack invStack : player.getInventory().items) {
            if (invStack.isEmpty()) continue;
            var item = invStack.getItem();
            if (invStack.getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock().equals(block)
            )
                total += invStack.getCount();
            // Lava bucket
            else if (block.equals(Blocks.LAVA) && item.equals(Items.LAVA_BUCKET))
                total += 1;
            // Powder snow bucket is a block item -> already covered
        }
        return total;
    }
}
