package dev.huskcasaca.effortless.network.protocol.player;

import dev.huskcasaca.effortless.entity.player.ReachSettings;
import dev.huskcasaca.effortless.network.Packets;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;

public record ServerboundPlayerSetBuildReachPacket(
        ReachSettings reachSettings
) implements FabricPacket {
    public static final PacketType<ServerboundPlayerSetBuildReachPacket> TYPE = PacketType.create(
            Packets.C2S_PLAYER_SET_BUILD_REACH_PACKET, ServerboundPlayerSetBuildReachPacket::new
    );
    @Override
    public PacketType<?> getType() { return TYPE; }
    public ServerboundPlayerSetBuildReachPacket(FriendlyByteBuf friendlyByteBuf) {
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
