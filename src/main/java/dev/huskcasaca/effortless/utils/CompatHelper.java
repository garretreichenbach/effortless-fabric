package dev.huskcasaca.effortless.utils;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class CompatHelper {

    // Check if the item given is a proxy for blocks. For now, we check for the randomizer bag,
    // /dank/null, or plain old blocks.
    public static boolean isItemBlockProxy(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof BlockItem;
//		return item instanceof AbstractRandomizerBagItem;
    }

    public static ItemStack getItemBlockByState(ItemStack stack, BlockState state) {
        if (state == null) return ItemStack.EMPTY;

        Item blockItem = Item.byBlock(state.getBlock());
        if (stack.getItem() instanceof BlockItem)
            return stack;
//		else if (stack.getItem() instanceof AbstractRandomizerBagItem) {
//			AbstractRandomizerBagItem randomizerBagItem = (AbstractRandomizerBagItem) stack.getItem();
//			Container bagInventory = randomizerBagItem.getBagInventory(stack);
//			return randomizerBagItem.findStack(bagInventory, blockItem);
//		}

        return ItemStack.EMPTY;
    }

}
