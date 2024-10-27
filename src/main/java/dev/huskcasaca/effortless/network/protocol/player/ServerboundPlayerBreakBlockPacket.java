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
 * Sends a message to the server indicating that a player wants to break a block
 */
public record ServerboundPlayerBreakBlockPacket(boolean blockHit, BlockPos blockPos, Direction hitSide, Vec3 hitVec) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ServerboundPlayerBreakBlockPacket> TYPE = new Type<>(Packets.C2S_PLAYER_BREAK_BLOCK_PACKET);
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPlayerBreakBlockPacket> CODEC = new StreamCodec<>() {

		@Override
		public void encode(RegistryFriendlyByteBuf object, ServerboundPlayerBreakBlockPacket object2) {
			object.writeBoolean(object2.blockHit());
			object.writeBlockPos(object2.blockPos());
			object.writeByte(object2.hitSide().get3DDataValue());
			object.writeDouble(object2.hitVec().x);
			object.writeDouble(object2.hitVec().y);
			object.writeDouble(object2.hitVec().z);
		}

		@Override
		public ServerboundPlayerBreakBlockPacket decode(RegistryFriendlyByteBuf object) {
			return new ServerboundPlayerBreakBlockPacket(object.readBoolean(), object.readBlockPos(), Direction.from3DDataValue(object.readByte()), new Vec3(object.readDouble(), object.readDouble(), object.readDouble()));
		}
	};

	public ServerboundPlayerBreakBlockPacket() {
		this(false, BlockPos.ZERO, Direction.UP, new Vec3(0, 0, 0));
	}

	public ServerboundPlayerBreakBlockPacket(BlockHitResult result) {
		this(result.getType() == HitResult.Type.BLOCK, result.getBlockPos(), result.getDirection(), result.getLocation());
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
