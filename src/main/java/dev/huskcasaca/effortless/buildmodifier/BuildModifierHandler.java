package dev.huskcasaca.effortless.buildmodifier;

import dev.huskcasaca.effortless.buildmodifier.array.Array;
import dev.huskcasaca.effortless.buildmodifier.mirror.Mirror;
import dev.huskcasaca.effortless.buildmodifier.mirror.RadialMirror;
import dev.huskcasaca.effortless.entity.player.EffortlessDataProvider;
import dev.huskcasaca.effortless.entity.player.ModifierSettings;
import dev.huskcasaca.effortless.network.Packets;
import dev.huskcasaca.effortless.network.protocol.player.ClientboundPlayerBuildModifierPacket;
import dev.huskcasaca.effortless.utils.CompatHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class BuildModifierHandler {

    public static List<BlockPos> findCoordinates(Player player, List<BlockPos> posList) {
        //Add current blocks being placed too
        var coordinates = new LinkedHashSet<>(posList);

        //Find mirror/array/radial mirror coordinates for each blockpos
        for (var blockPos : posList) {
            var arrayCoordinates = Array.findCoordinates(player, blockPos);
            coordinates.addAll(arrayCoordinates);
            coordinates.addAll(Mirror.findCoordinates(player, blockPos));
            coordinates.addAll(RadialMirror.findCoordinates(player, blockPos));
            //get mirror for each array coordinate
            for (var coordinate : arrayCoordinates) {
                coordinates.addAll(Mirror.findCoordinates(player, coordinate));
                coordinates.addAll(RadialMirror.findCoordinates(player, coordinate));
            }
        }

        return coordinates.stream().toList();
    }

    public static List<BlockPos> findCoordinates(Player player, BlockPos blockPos) {
        return findCoordinates(player, new ArrayList<>(Collections.singletonList(blockPos)));
    }

    public static Map<BlockPos, BlockState> findBlockStates(Player player, List<BlockPos> posList, Vec3 hitVec, Direction facing, List<ItemStack> itemStacks) {
        var blockStates = new LinkedHashMap<BlockPos, BlockState>();
        itemStacks.clear();

        //Get itemstack
        ItemStack itemStack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (itemStack.isEmpty() || !CompatHelper.isItemBlockProxy(itemStack)) {
            itemStack = player.getItemInHand(InteractionHand.OFF_HAND);
        }
        if (itemStack.isEmpty() || !CompatHelper.isItemBlockProxy(itemStack)) {
            return Collections.emptyMap();
        }

        //Get ItemBlock stack
        ItemStack itemBlock = ItemStack.EMPTY;
        if (itemStack.getItem() instanceof BlockItem) itemBlock = itemStack;
        else itemBlock = CompatHelper.getItemBlockFromStack(itemStack);
//		AbstractRandomizerBagItem.resetRandomness();

        //Add blocks in posList first
        for (var blockPos : posList) {
            if (!(itemStack.getItem() instanceof BlockItem)) itemBlock = CompatHelper.getItemBlockFromStack(itemStack);
            BlockState blockState = getBlockStateFromItem(itemBlock, player, blockPos, facing, hitVec, InteractionHand.MAIN_HAND);
//            Effortless.log(player, "getBlockStateFromItem " + blockState);
            if (blockState == null) continue;

            blockStates.put(blockPos, blockState);
            itemStacks.add(itemBlock);
        }

        for (var blockPos : posList) {
            BlockState blockState = getBlockStateFromItem(itemBlock, player, blockPos, facing, hitVec, InteractionHand.MAIN_HAND);
            if (blockState == null) continue;

            var arrayBlockStates = Array.findBlockStates(player, blockPos, blockState, itemStack, itemStacks);
            blockStates.putAll(arrayBlockStates);

            blockStates.putAll(Mirror.findBlockStates(player, blockPos, blockState, itemStack, itemStacks));
            blockStates.putAll(RadialMirror.findBlockStates(player, blockPos, blockState, itemStack, itemStacks));
            //add mirror for each array coordinate
            for (BlockPos coordinate : Array.findCoordinates(player, blockPos)) {
                var blockState1 = arrayBlockStates.get(coordinate);
                if (blockState1 == null) continue;

                blockStates.putAll(Mirror.findBlockStates(player, coordinate, blockState1, itemStack, itemStacks));
                blockStates.putAll(RadialMirror.findBlockStates(player, coordinate, blockState1, itemStack, itemStacks));

            }

            //Adjust blockstates for torches and ladders etc to place on a valid side
            //TODO optimize findCoordinates (done twice now)
            //TODO fix mirror
//            List<BlockPos> coordinates = findCoordinates(player, startPos);
//            for (int i = 0; i < blockStates.size(); i++) {
//                blockStates.set(i, blockStates.get(i).getBlock().getStateForPlacement(player.world, coordinates.get(i), facing,
//                        (float) hitVec.x, (float) hitVec.y, (float) hitVec.z, itemStacks.get(i).getMetadata(), player, EnumHand.MAIN_HAND));
//            }
        }

        return blockStates;
    }

    public static boolean isEnabled(ModifierSettings modifierSettings, BlockPos startPos) {
        return Array.isEnabled(modifierSettings.arraySettings()) ||
                Mirror.isEnabled(modifierSettings.mirrorSettings(), startPos) ||
                RadialMirror.isEnabled(modifierSettings.radialMirrorSettings(), startPos) ||
                modifierSettings.enableQuickReplace();
    }

    public static BlockState getBlockStateFromItem(ItemStack itemStack, Player player, BlockPos blockPos, Direction facing, Vec3 hitVec, InteractionHand hand) {
        var hitresult = new BlockHitResult(hitVec, facing, blockPos, false);

        var item = itemStack.getItem();

        if (item instanceof BlockItem) {
            // FIXME: 23/11/22
            return ((BlockItem) Item.byBlock(((BlockItem) item).getBlock())).getPlacementState(new BlockPlaceContext(player, hand, itemStack, hitresult));
        } else {
            return Block.byItem(item).getStateForPlacement(new BlockPlaceContext(new UseOnContext(player, hand, new BlockHitResult(hitVec, facing, blockPos, false))));
        }
    }

    //Returns true if equal (or both null)
    public static boolean compareCoordinates(List<BlockPos> coordinates1, List<BlockPos> coordinates2) {
        if (coordinates1 == null && coordinates2 == null) return true;
        if (coordinates1 == null || coordinates2 == null) return false;

        //Check count, not actual values
        if (coordinates1.size() == coordinates2.size()) {
            if (coordinates1.size() == 1) {
                return coordinates1.get(0).equals(coordinates2.get(0));
            }
            return true;
        } else {
            return false;
        }

//        return coordinates1.equals(coordinates2);
    }

    public static void handleNewPlayer(ServerPlayer player) {
        //Only on server
        Packets.sendToClient(new ClientboundPlayerBuildModifierPacket(((EffortlessDataProvider) player).getModifierSettings()), player);
    }
}
