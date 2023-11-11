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
public record ServerboundPlayerSetBuildModePacket(
        ModeSettings modeSettings
) implements FabricPacket {
    public static final PacketType<ServerboundPlayerSetBuildModePacket> TYPE = PacketType.create(
            Packets.C2S_PLAYER_SET_BUILD_MODE_PACKET, ServerboundPlayerSetBuildModePacket::new
    );
    @Override
    public PacketType<?> getType() { return TYPE; }

    public ServerboundPlayerSetBuildModePacket(FriendlyByteBuf friendlyByteBuf) {
        this(new ModeSettings(BuildMode.values()[friendlyByteBuf.readInt()], friendlyByteBuf.readBoolean()));
    }

    @Override
    public void write(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeInt(modeSettings.buildMode().ordinal());
        friendlyByteBuf.writeBoolean(modeSettings.enableMagnet());
    }
}
