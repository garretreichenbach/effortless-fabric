package dev.huskcasaca.effortless.utils;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class InventoryHelper {

    /**
     * Returns the first-best item stack in player's inventory that can supply the given block.
     * @param player The player to search
     * @param block Block to find as item
     * @return Reference to ItemStack, ItemStack.EMPTY if not found.
     */
    public static ItemStack findItemStackInInventory(Player player, Block block) {
        for (ItemStack invStack : player.getInventory().items) {
            if (!invStack.isEmpty() && invStack.getItem() instanceof BlockItem &&
                    ((BlockItem) invStack.getItem()).getBlock().equals(block)) {
                return invStack;
            }
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
        for (ItemStack invStack : player.getInventory().items) {
            if (!invStack.isEmpty() && invStack.getItem() instanceof BlockItem &&
                    ((BlockItem) invStack.getItem()).getBlock().equals(block)) {
                total += invStack.getCount();
            }
        }
        return total;
    }
}
