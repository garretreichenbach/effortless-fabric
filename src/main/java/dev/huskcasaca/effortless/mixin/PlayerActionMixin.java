package dev.huskcasaca.effortless.mixin;

import dev.huskcasaca.effortless.building.BuildHandler;
import dev.huskcasaca.effortless.buildmode.BuildMode;
import dev.huskcasaca.effortless.buildmode.BuildModeHandler;
import dev.huskcasaca.effortless.buildmode.BuildModeHelper;
import dev.huskcasaca.effortless.network.protocol.player.ServerboundPlayerBreakBlockPacket;
import dev.huskcasaca.effortless.network.protocol.player.ServerboundPlayerPlaceBlockPacket;
import dev.huskcasaca.effortless.network.Packets;
import dev.huskcasaca.effortless.utils.InventoryHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static dev.huskcasaca.effortless.EffortlessClient.getLookingAt;

@Mixin(Minecraft.class)
public abstract class PlayerActionMixin {

    @Shadow
    @Nullable
    public HitResult hitResult;

    @Shadow
    protected int missTime;
    @Shadow
    @Nullable
    public MultiPlayerGameMode gameMode;

    @Shadow
    @Nullable
    public LocalPlayer player;
    @Shadow
    @Final
    public GameRenderer gameRenderer;

    @Shadow
    @Nullable
    public ClientLevel level;

    @Shadow
    private int rightClickDelay;

    // TODO: 15/9/22 extract to EffortlessClient class
    // startAttack
    @Inject(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;"), cancellable = true)
    private void onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        var buildMode = BuildModeHelper.getModeSettings(player).buildMode();
        if (buildMode == BuildMode.DISABLE) return;
        // let vanilla handle entity attack
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) return;
        // let vanilla handle attack action
        if (player.isHandsBusy()) return;


        // FIXME: 15/9/22 grass hit result is incorrect using getLookingAt
        HitResult lookingAt = getLookingAt(player);
        if (lookingAt != null && lookingAt.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockLookingAt = (BlockHitResult) lookingAt;

            BuildHandler.onBlockBroken(player, new ServerboundPlayerBreakBlockPacket(blockLookingAt));
            Packets.sendToServer(new ServerboundPlayerBreakBlockPacket(blockLookingAt));

            //play sound if further than normal
            if ((blockLookingAt.getLocation().subtract(player.getEyePosition(1f))).lengthSqr() > 25f) {
                var blockPos = blockLookingAt.getBlockPos();
                var state = player.level().getBlockState(blockPos);
                var soundtype = state.getBlock().getSoundType(state);
                player.level().playSound(player, player.blockPosition(), soundtype.getBreakSound(), SoundSource.BLOCKS,
                        0.4f, soundtype.getPitch());
            }
            cir.setReturnValue(true);
        } else {
            BuildHandler.onBlockBroken(player, new ServerboundPlayerBreakBlockPacket());
            Packets.sendToServer(new ServerboundPlayerBreakBlockPacket());
            cir.setReturnValue(false);
        }
        player.swing(InteractionHand.MAIN_HAND);
        cir.cancel();


    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void onContinueAttack(boolean bl, CallbackInfo ci) {
        var buildMode = BuildModeHelper.getModeSettings(player).buildMode();
        if (buildMode == BuildMode.DISABLE) return;

        if (!bl) {
            this.missTime = 0;
        }

        // let vanilla handle entity attack
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) return;
        // let vanilla handle attack action
        if (player.isHandsBusy()) return;

        ci.cancel();

    }


    @Inject(method = "startUseItem", at = @At(value = "HEAD"), cancellable = true)
    private void onStartUseItem(CallbackInfo ci) {
        var buildMode = BuildModeHelper.getModeSettings(player).buildMode();
        if (buildMode == BuildMode.DISABLE) return;
        // let vanilla handle entity attack
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) return;
        // let vanilla handle attack action
        if (this.gameMode.isDestroying()) {
            ci.cancel();
            return;
        }
        rightClickDelay = 4;
        if (this.player.isHandsBusy()) {
            ci.cancel();
            return;
        }

        for (var interactionHand : InteractionHand.values()) {
            // make a copy, since the item may be gone when we are done with placing (Survival, used up)
            var itemStack = player.getItemInHand(interactionHand).copy();
            HitResult lookingAt = getLookingAt(player);
            if (lookingAt.getType() == HitResult.Type.BLOCK) {
                //find position in distance
                BlockHitResult blockLookingAt = (BlockHitResult) lookingAt;
                BuildHandler.onBlockPlaced(player, new ServerboundPlayerPlaceBlockPacket(blockLookingAt));
                Packets.sendToServer(new ServerboundPlayerPlaceBlockPacket(blockLookingAt));
                //play sound if further than normal
                if ((blockLookingAt.getLocation().subtract(player.getEyePosition(1f))).lengthSqr() > 25f) {
                    if (itemStack.getItem() instanceof BlockItem item) {
                        var state = item.getBlock().defaultBlockState();
                        var soundType = state.getBlock().getSoundType(state);
                        player.level().playSound(player, player.blockPosition(), soundType.getPlaceSound(), SoundSource.BLOCKS,
                                0.4f, soundType.getPitch());
                    }
                    else if (InventoryHelper.holdingBucket(player, true)) {
                        var item = itemStack.getItem();
                        SoundEvent sound;
                        if (item == Items.BUCKET)
                            // Player might have picked up water, lava or snow, we don't know.
                            // Just use the water sound.
                            sound = SoundEvents.BUCKET_FILL;
                        else if (item == Items.LAVA_BUCKET)
                            sound = SoundEvents.BUCKET_EMPTY_LAVA;
                        else if (item == Items.POWDER_SNOW_BUCKET)
                            sound = SoundEvents.BUCKET_EMPTY_POWDER_SNOW;
                        else if (item == Items.MILK_BUCKET)
                            sound = SoundEvents.COW_AMBIENT;
                        else
                            // Probably has something watery in it
                            sound = SoundEvents.BUCKET_EMPTY;
                        player.playSound(sound, 1.0F, 1.0F);
                    }
                    player.swing(interactionHand);
                }
            } else {
                BuildHandler.onBlockPlaced(player, new ServerboundPlayerPlaceBlockPacket());
                Packets.sendToServer(new ServerboundPlayerPlaceBlockPacket());
            }
            ci.cancel();
            return;
        }
    }

}
