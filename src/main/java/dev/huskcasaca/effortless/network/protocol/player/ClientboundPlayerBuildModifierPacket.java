package dev.huskcasaca.effortless.network.protocol.player;

import dev.huskcasaca.effortless.entity.player.ModifierSettings;
import dev.huskcasaca.effortless.network.Packets;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Shares modifier settings (see ModifierSettingsManager) between server and client
 */
public record ClientboundPlayerBuildModifierPacket(
        ModifierSettings modifierSettings
) implements FabricPacket {
    public static final PacketType<ClientboundPlayerBuildModifierPacket> TYPE = PacketType.create(
            Packets.S2C_PLAYER_BUILD_MODIFIER_PACKET, ClientboundPlayerBuildModifierPacket::new
    );
    @Override
    public PacketType<?> getType() { return TYPE; }

    public ClientboundPlayerBuildModifierPacket(FriendlyByteBuf friendlyByteBuf) {
        this(ModifierSettings.decodeBuf(friendlyByteBuf));
    }

    @Override
    public void write(FriendlyByteBuf friendlyByteBuf) {
        ModifierSettings.write(friendlyByteBuf, modifierSettings);
    }
}


