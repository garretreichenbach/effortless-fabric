package dev.huskcasaca.effortless.network.protocol.player;

import dev.huskcasaca.effortless.network.Packets;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;

/***
 * Sends a message to the client asking for its lookat (objectmouseover) data.
 * This is then sent back with a BlockPlacedMessage.
 */
public record ClientboundPlayerRequestLookAtPacket(
        boolean placeStartPos
) implements FabricPacket {
    public static final PacketType<ClientboundPlayerRequestLookAtPacket> TYPE = PacketType.create(
            Packets.S2C_PLAYER_REQUEST_LOOK_AT_PACKET, ClientboundPlayerRequestLookAtPacket::new
    );
    @Override
    public PacketType<?> getType() { return TYPE; }
    public ClientboundPlayerRequestLookAtPacket() {
        this(false);
    }

    public ClientboundPlayerRequestLookAtPacket(FriendlyByteBuf friendlyByteBuf) {
        this(friendlyByteBuf.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeBoolean(placeStartPos);
    }
}
