package dev.huskcasaca.effortless.network.protocol.player;

import dev.huskcasaca.effortless.entity.player.ReachSettings;
import dev.huskcasaca.effortless.network.Packets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerboundPlayerSetBuildReachPacket(ReachSettings reachSettings) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ServerboundPlayerSetBuildReachPacket> TYPE = new Type<>(Packets.C2S_PLAYER_SET_BUILD_REACH_PACKET);
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPlayerSetBuildReachPacket> CODEC = new StreamCodec<>() {
		@Override
		public void encode(RegistryFriendlyByteBuf object, ServerboundPlayerSetBuildReachPacket object2) {
			object.writeInt(object2.reachSettings().maxReachDistance());
			object.writeInt(object2.reachSettings().maxBlockPlacePerAxis());
			object.writeInt(object2.reachSettings().maxBlockPlaceAtOnce());
			object.writeBoolean(object2.reachSettings().canBreakFar());
			object.writeBoolean(object2.reachSettings().enableUndoRedo());
			object.writeInt(object2.reachSettings().undoStackSize());
		}

		@Override
		public ServerboundPlayerSetBuildReachPacket decode(RegistryFriendlyByteBuf object) {
			return new ServerboundPlayerSetBuildReachPacket(new ReachSettings(object.readInt(), object.readInt(), object.readInt(), object.readBoolean(), object.readBoolean(), object.readInt()));
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
