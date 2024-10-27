package dev.huskcasaca.effortless.network.protocol.player;

import dev.huskcasaca.effortless.entity.player.ModifierSettings;
import dev.huskcasaca.effortless.network.Packets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Shares modifier settings (see ModifierSettingsManager) between server and client
 */
public record ClientboundPlayerBuildModifierPacket(ModifierSettings modifierSettings) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ClientboundPlayerBuildModifierPacket> TYPE = new Type<>(Packets.S2C_PLAYER_BUILD_MODIFIER_PACKET);
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPlayerBuildModifierPacket> CODEC = new StreamCodec<>() {

		@Override
		public void encode(RegistryFriendlyByteBuf object, ClientboundPlayerBuildModifierPacket object2) {
			ModifierSettings.write(object, object2.modifierSettings());
		}

		@Override
		public ClientboundPlayerBuildModifierPacket decode(RegistryFriendlyByteBuf object) {
			return new ClientboundPlayerBuildModifierPacket(ModifierSettings.decodeBuf(object));
		}
	};
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}


