package dev.huskcasaca.effortless.network.protocol.player;

import dev.huskcasaca.effortless.buildmode.BuildMode;
import dev.huskcasaca.effortless.entity.player.ModeSettings;
import dev.huskcasaca.effortless.network.Packets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Shares mode settings (see ModeSettingsManager) between server and client
 */
public record ServerboundPlayerSetBuildModePacket(ModeSettings modeSettings) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ServerboundPlayerSetBuildModePacket> TYPE = new Type<>(Packets.C2S_PLAYER_SET_BUILD_MODE_PACKET);
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPlayerSetBuildModePacket> CODEC = new StreamCodec<>() {
		@Override
		public void encode(RegistryFriendlyByteBuf object, ServerboundPlayerSetBuildModePacket object2) {
			object.writeInt(object2.modeSettings().buildMode().ordinal());
			object.writeBoolean(object2.modeSettings().enableMagnet());
		}

		@Override
		public ServerboundPlayerSetBuildModePacket decode(RegistryFriendlyByteBuf object) {
			return new ServerboundPlayerSetBuildModePacket(new ModeSettings(BuildMode.values()[object.readInt()], object.readBoolean()));
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
