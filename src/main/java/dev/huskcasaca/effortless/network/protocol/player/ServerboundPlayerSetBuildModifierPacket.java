package dev.huskcasaca.effortless.network.protocol.player;

import dev.huskcasaca.effortless.entity.player.ModifierSettings;
import dev.huskcasaca.effortless.network.Packets;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Shares modifier settings (see ModifierSettingsManager) between server and client
 */
public record ServerboundPlayerSetBuildModifierPacket(
        ModifierSettings modifierSettings
) implements FabricPacket {
    public static final PacketType<ServerboundPlayerSetBuildModifierPacket> TYPE = PacketType.create(
            Packets.C2S_PLAYER_SET_BUILD_MODIFIER_PACKET, ServerboundPlayerSetBuildModifierPacket::new
    );
    @Override
    public PacketType<?> getType() { return TYPE; }


    public ServerboundPlayerSetBuildModifierPacket(FriendlyByteBuf friendlyByteBuf) {
        this(ModifierSettings.decodeBuf(friendlyByteBuf));
    }

    @Override
    public void write(FriendlyByteBuf friendlyByteBuf) {
        ModifierSettings.write(friendlyByteBuf, modifierSettings);
    }
}


