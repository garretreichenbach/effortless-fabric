package dev.huskcasaca.effortless;

import dev.huskcasaca.effortless.building.BuildActionHandler;
import dev.huskcasaca.effortless.building.BuildHandler;
import dev.huskcasaca.effortless.building.ReachHelper;
import dev.huskcasaca.effortless.buildmode.BuildMode;
import dev.huskcasaca.effortless.buildmode.BuildModeHandler;
import dev.huskcasaca.effortless.buildmode.BuildModeHelper;
import dev.huskcasaca.effortless.buildmodifier.BuildModifierHandler;
import dev.huskcasaca.effortless.buildmodifier.BuildModifierHelper;
import dev.huskcasaca.effortless.buildmodifier.UndoRedo;
import dev.huskcasaca.effortless.entity.player.ModeSettings;
import dev.huskcasaca.effortless.entity.player.ModifierSettings;
import dev.huskcasaca.effortless.network.protocol.player.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.jmx.Server;

public class Effortless implements ModInitializer {

	public static final String MOD_ID = "effortless";
	public static final Logger logger = LogManager.getLogger();

	public static void onPlayerLogin(ServerPlayer player) {
		BuildModifierHandler.handleNewPlayer(player);
		BuildModeHandler.handleNewPlayer(player);
		ReachHelper.handleNewPlayer(player);
	}

	public static void onPlayerLogout(ServerPlayer player) {
		UndoRedo.clear(player);
		// FIXME: 18/11/22
//        Packets.sendToClient(new ClearUndoMessage(), player);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		BuildModifierHandler.handleNewPlayer(player);
		BuildModeHandler.handleNewPlayer(player);
		ReachHelper.handleNewPlayer(player);
	}

	public static void onPlayerChangedDimension(ServerPlayer player) {
//        //Set build mode to normal
		var modeSettings = BuildModeHelper.getModeSettings(player);
		modeSettings = new ModeSettings(
				BuildMode.DISABLE,
				modeSettings.enableMagnet()
		);
		BuildModeHelper.setModeSettings(player, modeSettings);

		var modifierSettings = BuildModifierHelper.getModifierSettings(player);
		modifierSettings = new ModifierSettings();

		BuildModifierHelper.setModifierSettings(player, modifierSettings);

		BuildModifierHandler.handleNewPlayer(player);
		BuildModeHandler.handleNewPlayer(player);
		ReachHelper.handleNewPlayer(player);

		UndoRedo.clear(player);
		// FIXME: 18/11/22
//        Packets.sendToClient(new ClearUndoMessage(), player);
	}

	//
	public static void onPlayerClone(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		BuildModifierHelper.setModifierSettings(newPlayer, BuildModifierHelper.getModifierSettings(oldPlayer));
		BuildModeHelper.setModeSettings(newPlayer, BuildModeHelper.getModeSettings(oldPlayer));
		ReachHelper.setReachSettings(newPlayer, ReachHelper.getReachSettings(oldPlayer));
	}

	public static void log(String msg) {
		logger.info(msg);
	}

	public static void log(Player player, String msg) {
		log(player, msg, false);
	}

	public static void log(Player player, String msg, boolean actionBar) {
		player.displayClientMessage(Component.literal(msg), actionBar);
	}

	//Log with translation supported, call either on client or server (which then sends a message)
	public static void logTranslate(Player player, String prefix, String translationKey, String suffix, boolean actionBar) {
//		proxy.logTranslate(player, prefix, translationKey, suffix, actionBar);
	}

	@Override
	public void onInitialize() {
		log("Effortless.onInitialize was called.");
		
		//Register payload types
		PayloadTypeRegistry.playC2S().register(ServerboundPlayerBreakBlockPacket.TYPE, ServerboundPlayerBreakBlockPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(ServerboundPlayerBuildActionPacket.TYPE, ServerboundPlayerBuildActionPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(ServerboundPlayerPlaceBlockPacket.TYPE, ServerboundPlayerPlaceBlockPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(ServerboundPlayerSetBuildModePacket.TYPE, ServerboundPlayerSetBuildModePacket.CODEC);
		PayloadTypeRegistry.playC2S().register(ServerboundPlayerSetBuildModifierPacket.TYPE, ServerboundPlayerSetBuildModifierPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(ServerboundPlayerSetBuildReachPacket.TYPE, ServerboundPlayerSetBuildReachPacket.CODEC);
		
		// Register serverside network packet handlers
		ServerPlayNetworking.registerGlobalReceiver(ServerboundPlayerBreakBlockPacket.TYPE, Effortless::handlePacket);
		ServerPlayNetworking.registerGlobalReceiver(ServerboundPlayerBuildActionPacket.TYPE, Effortless::handlePacket);
		ServerPlayNetworking.registerGlobalReceiver(ServerboundPlayerPlaceBlockPacket.TYPE, Effortless::handlePacket);
		ServerPlayNetworking.registerGlobalReceiver(ServerboundPlayerSetBuildModePacket.TYPE, Effortless::handlePacket);
		ServerPlayNetworking.registerGlobalReceiver(ServerboundPlayerSetBuildModifierPacket.TYPE, Effortless::handlePacket);
		ServerPlayNetworking.registerGlobalReceiver(ServerboundPlayerSetBuildReachPacket.TYPE, Effortless::handlePacket);
	}

	public static void handlePacket(ServerboundPlayerBreakBlockPacket packet, ServerPlayNetworking.Context context) {
		BuildHandler.onBlockBroken(context.player(), packet);
	}

	public static void handlePacket(ServerboundPlayerBuildActionPacket packet, ServerPlayNetworking.Context context) {
		BuildActionHandler.performAction(context.player(), packet.action());
	}

	public static void handlePacket(ServerboundPlayerPlaceBlockPacket packet, ServerPlayNetworking.Context context) {
		BuildHandler.onBlockPlaced(context.player(), packet);
	}

	public static void handlePacket(ServerboundPlayerSetBuildModePacket packet, ServerPlayNetworking.Context context) {
		BuildModeHelper.setModeSettings(context.player(), BuildModeHelper.sanitize(packet.modeSettings(), context.player()));
	}

	public static void handlePacket(ServerboundPlayerSetBuildModifierPacket packet, ServerPlayNetworking.Context context) {
		BuildModifierHelper.setModifierSettings(context.player(), BuildModifierHelper.sanitize(packet.modifierSettings(), context.player()));
	}

	public static void handlePacket(ServerboundPlayerSetBuildReachPacket packet, ServerPlayNetworking.Context context) {
		ReachHelper.setReachSettings(context.player(), ReachHelper.sanitize(packet.reachSettings(), context.player()));
		BuildHandler.initialize(context.player());
	}
}
