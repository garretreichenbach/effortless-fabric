package dev.huskcasaca.effortless.network.protocol.player;

import dev.huskcasaca.effortless.building.BuildAction;
import dev.huskcasaca.effortless.network.Packets;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;

public record ServerboundPlayerBuildActionPacket(
        BuildAction action
) implements FabricPacket {
    public static final PacketType<ServerboundPlayerBuildActionPacket> TYPE = PacketType.create(
            Packets.C2S_PLAYER_BUILD_ACTION_PACKET, ServerboundPlayerBuildActionPacket::new
    );
    @Override
    public PacketType<?> getType() { return TYPE; }

    public ServerboundPlayerBuildActionPacket(FriendlyByteBuf friendlyByteBuf) {
        this(BuildAction.values()[friendlyByteBuf.readInt()]);
    }

    @Override
    public void write(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeInt(action.ordinal());
    }
}
