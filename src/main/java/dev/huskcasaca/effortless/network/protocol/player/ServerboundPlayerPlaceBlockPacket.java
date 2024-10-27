package dev.huskcasaca.effortless.network.protocol.player;

import dev.huskcasaca.effortless.network.Packets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/***
 * Sends a message to the server indicating that a player wants to place a block.
 * Received clientside: server has placed blocks and its letting the client know.
 */
public record ServerboundPlayerPlaceBlockPacket(boolean blockHit, BlockPos blockPos, Direction hitSide, Vec3 hitVec, boolean placeStartPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ServerboundPlayerPlaceBlockPacket> TYPE = new CustomPacketPayload.Type<>(Packets.C2S_PLAYER_PLACE_BLOCK_PACKET);
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPlayerPlaceBlockPacket> CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf object, ServerboundPlayerPlaceBlockPacket object2) {
            object.writeBoolean(object2.blockHit());
            object.writeBlockPos(object2.blockPos());
            object.writeByte(object2.hitSide().get3DDataValue());
            object.writeDouble(object2.hitVec().x);
            object.writeDouble(object2.hitVec().y);
            object.writeDouble(object2.hitVec().z);
            object.writeBoolean(object2.placeStartPos());
        }

        @Override
        public ServerboundPlayerPlaceBlockPacket decode(RegistryFriendlyByteBuf object) {
            return new ServerboundPlayerPlaceBlockPacket(object.readBoolean(), object.readBlockPos(), Direction.from3DDataValue(object.readByte()), new Vec3(object.readDouble(), object.readDouble(), object.readDouble()), object.readBoolean());
        }
    };
    
    public ServerboundPlayerPlaceBlockPacket() {
        this(false, BlockPos.ZERO, Direction.UP, new Vec3(0, 0, 0), true);
    }

    public ServerboundPlayerPlaceBlockPacket(BlockHitResult result) {
        this(result.getType() == HitResult.Type.BLOCK, result.getBlockPos(), result.getDirection(), result.getLocation(), true);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
