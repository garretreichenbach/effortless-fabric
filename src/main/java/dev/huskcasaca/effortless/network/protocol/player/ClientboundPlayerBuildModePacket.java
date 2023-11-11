package dev.huskcasaca.effortless.network.protocol.player;

import dev.huskcasaca.effortless.buildmode.BuildMode;
import dev.huskcasaca.effortless.entity.player.ModeSettings;
import dev.huskcasaca.effortless.network.Packets;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
/**
 * Shares mode settings (see ModeSettingsManager) between server and client
 */
public record ClientboundPlayerBuildModePacket(
        ModeSettings modeSettings
) implements FabricPacket {
    public static final PacketType<ClientboundPlayerBuildModePacket> TYPE = PacketType.create(
            Packets.S2C_PLAYER_BUILD_MODE_PACKET, ClientboundPlayerBuildModePacket::new
    );
    @Override
    public PacketType<?> getType() { return TYPE; }

    public ClientboundPlayerBuildModePacket(FriendlyByteBuf friendlyByteBuf) {
        this(new ModeSettings(BuildMode.values()[friendlyByteBuf.readInt()], friendlyByteBuf.readBoolean()));
    }

    @Override
    public void write(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeInt(modeSettings.buildMode().ordinal());
        friendlyByteBuf.writeBoolean(modeSettings.enableMagnet());
    }
}
