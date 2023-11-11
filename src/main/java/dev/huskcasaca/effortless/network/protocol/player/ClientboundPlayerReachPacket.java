package dev.huskcasaca.effortless.network.protocol.player;

import dev.huskcasaca.effortless.entity.player.ReachSettings;
import dev.huskcasaca.effortless.network.Packets;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;

public record ClientboundPlayerReachPacket(
        ReachSettings reachSettings
) implements FabricPacket {
    public static final PacketType<ClientboundPlayerReachPacket> TYPE = PacketType.create(
            Packets.S2C_PLAYER_REACH_PACKET, ClientboundPlayerReachPacket::new
    );
    @Override
    public PacketType<?> getType() { return TYPE; }
    public ClientboundPlayerReachPacket(FriendlyByteBuf friendlyByteBuf) {
        this(
                new ReachSettings(
                        friendlyByteBuf.readInt(),
                        friendlyByteBuf.readInt(),
                        friendlyByteBuf.readInt(),
                        friendlyByteBuf.readBoolean(),
                        friendlyByteBuf.readBoolean(),
                        friendlyByteBuf.readInt()
                )
        );
    }

    @Override
    public void write(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeInt(reachSettings.maxReachDistance());
        friendlyByteBuf.writeInt(reachSettings.maxBlockPlacePerAxis());
        friendlyByteBuf.writeInt(reachSettings.maxBlockPlaceAtOnce());
        friendlyByteBuf.writeBoolean(reachSettings.canBreakFar());
        friendlyByteBuf.writeBoolean(reachSettings.enableUndoRedo());
        friendlyByteBuf.writeInt(reachSettings.undoStackSize());
    }
}
