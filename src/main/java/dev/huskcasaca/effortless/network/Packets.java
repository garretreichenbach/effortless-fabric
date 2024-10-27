package dev.huskcasaca.effortless.network;

import dev.huskcasaca.effortless.Effortless;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class Packets {

    public static final ResourceLocation S2C_PLAYER_BUILD_MODE_PACKET = new ResourceLocation(Effortless.MOD_ID, "player_build_mode");
    public static final ResourceLocation S2C_PLAYER_BUILD_MODIFIER_PACKET = new ResourceLocation(Effortless.MOD_ID, "player_build_modifier");
    public static final ResourceLocation S2C_PLAYER_REACH_PACKET = new ResourceLocation(Effortless.MOD_ID, "player_reach");
    public static final ResourceLocation S2C_PLAYER_REQUEST_LOOK_AT_PACKET = new ResourceLocation(Effortless.MOD_ID, "player_request_look_at");

    public static final ResourceLocation C2S_PLAYER_BREAK_BLOCK_PACKET = new ResourceLocation(Effortless.MOD_ID, "player_break_block");
    public static final ResourceLocation C2S_PLAYER_BUILD_ACTION_PACKET = new ResourceLocation(Effortless.MOD_ID, "player_build_action");
    public static final ResourceLocation C2S_PLAYER_PLACE_BLOCK_PACKET = new ResourceLocation(Effortless.MOD_ID, "player_place_block");
    public static final ResourceLocation C2S_PLAYER_SET_BUILD_MODE_PACKET = new ResourceLocation(Effortless.MOD_ID, "player_set_build_mode");
    public static final ResourceLocation C2S_PLAYER_SET_BUILD_MODIFIER_PACKET = new ResourceLocation(Effortless.MOD_ID, "player_set_build_modifier");
    public static final ResourceLocation C2S_PLAYER_SET_BUILD_REACH_PACKET = new ResourceLocation(Effortless.MOD_ID, "player_set_build_reach");

    public static <T extends CustomPacketPayload> void sendToServer(T packet) {
        ClientPlayNetworking.send(packet);
        //Minecraft.getInstance().getConnection().send(packet);
    }

    public static <T extends CustomPacketPayload> void sendToClient(T packet, ServerPlayer player) {
        ServerPlayNetworking.send(player, packet);
        //player.connection.send(packet);
    }
}
