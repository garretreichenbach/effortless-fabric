package dev.huskcasaca.effortless.network.protocol.player;

import dev.huskcasaca.effortless.network.Packets;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/***
 * Sends a message to the server indicating that a player wants to place a block.
 * Received clientside: server has placed blocks and its letting the client know.
 */
public record ServerboundPlayerPlaceBlockPacket(
        boolean blockHit,
        BlockPos blockPos,
        Direction hitSide,
        Vec3 hitVec,
        // Unused - left in for compat
        boolean placeStartPos
) implements FabricPacket {
    public static final PacketType<ServerboundPlayerPlaceBlockPacket> TYPE = PacketType.create(
            Packets.C2S_PLAYER_PLACE_BLOCK_PACKET, ServerboundPlayerPlaceBlockPacket::new
    );
    @Override
    public PacketType<?> getType() { return TYPE; }

    public ServerboundPlayerPlaceBlockPacket() {
        this(false, BlockPos.ZERO, Direction.UP, new Vec3(0, 0, 0), true);
    }

    public ServerboundPlayerPlaceBlockPacket(BlockHitResult result) {
        this(result.getType() == HitResult.Type.BLOCK, result.getBlockPos(), result.getDirection(), result.getLocation(), true);
    }

    public ServerboundPlayerPlaceBlockPacket(FriendlyByteBuf friendlyByteBuf) {
        this(friendlyByteBuf.readBoolean(),
                friendlyByteBuf.readBlockPos(),
                Direction.from3DDataValue(friendlyByteBuf.readByte()),
                new Vec3(friendlyByteBuf.readDouble(), friendlyByteBuf.readDouble(), friendlyByteBuf.readDouble()),
                friendlyByteBuf.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeBoolean(blockHit);
        friendlyByteBuf.writeBlockPos(blockPos);
        friendlyByteBuf.writeByte(hitSide.get3DDataValue());
        friendlyByteBuf.writeDouble(hitVec.x);
        friendlyByteBuf.writeDouble(hitVec.y);
        friendlyByteBuf.writeDouble(hitVec.z);
        friendlyByteBuf.writeBoolean(placeStartPos);
    }
}
