package dev.huskcasaca.effortless.network.protocol.player;

import dev.huskcasaca.effortless.entity.player.ModifierSettings;
import dev.huskcasaca.effortless.network.Packets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Shares modifier settings (see ModifierSettingsManager) between server and client
 */
public record ServerboundPlayerSetBuildModifierPacket(ModifierSettings modifierSettings) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ServerboundPlayerSetBuildModifierPacket> TYPE = new Type<>(Packets.C2S_PLAYER_SET_BUILD_MODIFIER_PACKET);
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPlayerSetBuildModifierPacket> CODEC = new StreamCodec<>() {
		@Override
		public void encode(RegistryFriendlyByteBuf object, ServerboundPlayerSetBuildModifierPacket object2) {
			ModifierSettings.write(object, object2.modifierSettings());
		}

		@Override
		public ServerboundPlayerSetBuildModifierPacket decode(RegistryFriendlyByteBuf object) {
			return new ServerboundPlayerSetBuildModifierPacket(ModifierSettings.decodeBuf(object));
		}
	};
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}


