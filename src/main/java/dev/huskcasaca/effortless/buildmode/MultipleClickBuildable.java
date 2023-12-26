package dev.huskcasaca.effortless.buildmode;

import dev.huskcasaca.effortless.building.BuildOp;
import dev.huskcasaca.effortless.utils.InventoryHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.UUID;

public abstract class MultipleClickBuildable implements Buildable {
    //In singleplayer client and server variables are shared
    //Split everything that needs separate values and may not be called twice in one click
    protected Dictionary<UUID, Integer> rightClickTableClient = new Hashtable<>();
    protected Dictionary<UUID, Integer> rightClickTableServer = new Hashtable<>();
    protected Dictionary<UUID, BlockPos> firstPosTable = new Hashtable<>();

    public BuildOp operationOnUse(Player player) {
        return InventoryHelper.holdingBucket(player, true) ? BuildOp.DRENCH : BuildOp.PLACE;
    }
    public BuildOp operationOnAttack(Player player) {return BuildOp.BREAK; }

    @Override
    public void initialize(Player player) {
        var rightClickTable = player.level().isClientSide ? rightClickTableClient : rightClickTableServer;
        rightClickTable.put(player.getUUID(), 0);
    }

    @Override
    public boolean isInProgress(Player player) {
        var rightClickTable = player.level().isClientSide ? rightClickTableClient : rightClickTableServer;
        return rightClickTable.get(player.getUUID()) != 0;
    }
}
