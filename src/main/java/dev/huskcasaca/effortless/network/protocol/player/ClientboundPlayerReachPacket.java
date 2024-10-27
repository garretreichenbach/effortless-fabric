package dev.huskcasaca.effortless.network.protocol.player;

import dev.huskcasaca.effortless.entity.player.ReachSettings;
import dev.huskcasaca.effortless.network.Packets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClientboundPlayerReachPacket(ReachSettings reachSettings) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ClientboundPlayerReachPacket> TYPE = new Type<>(Packets.S2C_PLAYER_REACH_PACKET);
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPlayerReachPacket> CODEC = new StreamCodec<>() {

		@Override
		public void encode(RegistryFriendlyByteBuf object, ClientboundPlayerReachPacket object2) {
			object.writeInt(object2.reachSettings().maxReachDistance());
			object.writeInt(object2.reachSettings().maxBlockPlacePerAxis());
			object.writeInt(object2.reachSettings().maxBlockPlaceAtOnce());
			object.writeBoolean(object2.reachSettings().canBreakFar());
			object.writeBoolean(object2.reachSettings().enableUndoRedo());
			object.writeInt(object2.reachSettings().undoStackSize());
		}

		@Override
		public ClientboundPlayerReachPacket decode(RegistryFriendlyByteBuf object) {
			return new ClientboundPlayerReachPacket(new ReachSettings(object.readInt(), object.readInt(), object.readInt(), object.readBoolean(), object.readBoolean(), object.readInt()));
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
