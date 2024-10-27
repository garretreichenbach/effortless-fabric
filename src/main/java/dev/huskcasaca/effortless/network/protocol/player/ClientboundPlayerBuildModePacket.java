package dev.huskcasaca.effortless.network.protocol.player;

import dev.huskcasaca.effortless.buildmode.BuildMode;
import dev.huskcasaca.effortless.entity.player.ModeSettings;
import dev.huskcasaca.effortless.network.Packets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Shares mode settings (see ModeSettingsManager) between server and client
 */
public record ClientboundPlayerBuildModePacket(ModeSettings modeSettings) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ClientboundPlayerBuildModePacket> TYPE = new Type<>(Packets.S2C_PLAYER_BUILD_MODE_PACKET);
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPlayerBuildModePacket> CODEC = new StreamCodec<>() {

		@Override
		public void encode(RegistryFriendlyByteBuf object, ClientboundPlayerBuildModePacket object2) {
			object.writeInt(object2.modeSettings().buildMode().ordinal());
			object.writeBoolean(object2.modeSettings().enableMagnet());
		}

		@Override
		public ClientboundPlayerBuildModePacket decode(RegistryFriendlyByteBuf object) {
			return new ClientboundPlayerBuildModePacket(new ModeSettings(BuildMode.values()[object.readInt()], object.readBoolean()));
		}
	};
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
