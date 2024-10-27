package dev.huskcasaca.effortless.network.protocol.player;

import dev.huskcasaca.effortless.building.BuildAction;
import dev.huskcasaca.effortless.network.Packets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerboundPlayerBuildActionPacket(BuildAction action) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ServerboundPlayerBuildActionPacket> TYPE = new Type<>(Packets.C2S_PLAYER_BUILD_ACTION_PACKET);
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPlayerBuildActionPacket> CODEC = new StreamCodec<>() {
		@Override
		public void encode(RegistryFriendlyByteBuf object, ServerboundPlayerBuildActionPacket object2) {
			object.writeInt(object2.action().ordinal());
		}

		@Override
		public ServerboundPlayerBuildActionPacket decode(RegistryFriendlyByteBuf object) {
			return new ServerboundPlayerBuildActionPacket(BuildAction.values()[object.readInt()]);
		}
	};
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
