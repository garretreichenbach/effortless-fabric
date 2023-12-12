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

    /**
     * Given the blockStates to place, returns list extended with the "modified" block
     * states. Block states are rotated / mirrored according to what the modifier
     * says.
     * @param player - Player, needed to get modifier settings
     * @param blockStates - map of blockstates
     * @return new map of blockstates
     */
    public static Map<BlockPos, BlockState> findBlockStates(
            Player player, Map<BlockPos, BlockState>blockStates
    ) {
        // Make a copy to modify inplace
        var newBlockStates = new LinkedHashMap<>(blockStates);
        for (var entry : blockStates.entrySet()) {
            var blockPos = entry.getKey();
            var blockState = entry.getValue();

            var arrayBlockStates = Array.findBlockStates(player, blockPos, blockState);
            newBlockStates.putAll(arrayBlockStates);
            newBlockStates.putAll(Mirror.findBlockStates(player, blockPos, blockState));
            newBlockStates.putAll(RadialMirror.findBlockStates(player, blockPos, blockState));
            //add mirror for each array coordinate
            for (BlockPos coordinate : Array.findCoordinates(player, blockPos)) {
                var blockState1 = arrayBlockStates.get(coordinate);
                if (blockState1 == null) continue;
                newBlockStates.putAll(Mirror.findBlockStates(player, coordinate, blockState1));
                newBlockStates.putAll(RadialMirror.findBlockStates(player, coordinate, blockState1));
            }
        }
        return newBlockStates;
    }

    public static boolean isEnabled(ModifierSettings modifierSettings, BlockPos startPos) {
        return Array.isEnabled(modifierSettings.arraySettings()) ||
                Mirror.isEnabled(modifierSettings.mirrorSettings(), startPos) ||
                RadialMirror.isEnabled(modifierSettings.radialMirrorSettings(), startPos) ||
                modifierSettings.enableQuickReplace();
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
