package dev.huskcasaca.effortless;

import dev.huskcasaca.effortless.building.BuildActionHandler;
import dev.huskcasaca.effortless.building.ReachHelper;
import dev.huskcasaca.effortless.buildmode.BuildModeHandler;
import dev.huskcasaca.effortless.buildmode.BuildModeHelper;
import dev.huskcasaca.effortless.buildmodifier.BuildModifierHelper;
import dev.huskcasaca.effortless.network.protocol.player.*;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public class EffortlessServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        // Register network packet handlers
        ServerPlayNetworking.registerGlobalReceiver(
                ServerboundPlayerBreakBlockPacket.TYPE, EffortlessServer::handlePacket
        );
        ServerPlayNetworking.registerGlobalReceiver(
                ServerboundPlayerBuildActionPacket.TYPE, EffortlessServer::handlePacket
        );
        ServerPlayNetworking.registerGlobalReceiver(
                ServerboundPlayerPlaceBlockPacket.TYPE, EffortlessServer::handlePacket
        );
        ServerPlayNetworking.registerGlobalReceiver(
                ServerboundPlayerSetBuildModePacket.TYPE, EffortlessServer::handlePacket
        );
        ServerPlayNetworking.registerGlobalReceiver(
                ServerboundPlayerSetBuildModifierPacket.TYPE, EffortlessServer::handlePacket
        );
        ServerPlayNetworking.registerGlobalReceiver(
                ServerboundPlayerSetBuildReachPacket.TYPE, EffortlessServer::handlePacket
        );

    }

    public static void handlePacket(ServerboundPlayerBreakBlockPacket packet, ServerPlayer player, PacketSender sender) {
         BuildModeHandler.onBlockBrokenPacketReceived(player, packet);
    }

    public static void handlePacket(ServerboundPlayerBuildActionPacket packet, ServerPlayer player, PacketSender sender) {
        BuildActionHandler.performAction(player, packet.action());
    }

    public static void handlePacket(ServerboundPlayerPlaceBlockPacket packet, ServerPlayer player, PacketSender sender) {
        BuildModeHandler.onBlockPlacedPacketReceived(player, packet);
    }
    public static void handlePacket(ServerboundPlayerSetBuildModePacket packet, ServerPlayer player, PacketSender sender) {
        BuildModeHelper.setModeSettings(player, BuildModeHelper.sanitize(packet.modeSettings(), player));
        BuildModeHandler.initializeMode(player);
    }


    public static void handlePacket(ServerboundPlayerSetBuildModifierPacket packet, ServerPlayer player, PacketSender sender) {
        BuildModifierHelper.setModifierSettings(player, BuildModifierHelper.sanitize(packet.modifierSettings(), player));
    }

    public static void handlePacket(ServerboundPlayerSetBuildReachPacket packet, ServerPlayer player, PacketSender sender) {
        ReachHelper.setReachSettings(player, ReachHelper.sanitize(packet.reachSettings(), player));
        BuildModeHandler.initializeMode(player);
    }

}
