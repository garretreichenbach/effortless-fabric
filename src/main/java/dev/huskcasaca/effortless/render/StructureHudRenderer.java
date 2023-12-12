package dev.huskcasaca.effortless.render;

import dev.huskcasaca.effortless.Effortless;
import dev.huskcasaca.effortless.buildmode.BuildModeHelper;
import dev.huskcasaca.effortless.buildmode.StructureBuildable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class StructureHudRenderer {
    public static void render(Player player, GuiGraphics guiGraphics) {
        if (player==null) return;
        if (BuildModeHelper.getBuildMode(player).getInstance() instanceof StructureBuildable mode) {
            var slotsOccupied = mode.slotsOccupied(player);
            guiGraphics.pose().pushPose();
            // Cover up original items + decorations
            guiGraphics.pose().translate(0.0F, 0.0F, 300.0F);
            int width = guiGraphics.guiWidth();
            int height = guiGraphics.guiHeight();
            var emptySlotSprite = new ResourceLocation(Effortless.MOD_ID, "textures/gui/structure_slot_empty.png");
            // TODO: it would be really cool to generate some small preview graphic on the fly.
            var fullSlotSprite = new ResourceLocation(Effortless.MOD_ID, "textures/gui/structure_slot_full.png");
            for (int slot=0; slot<9; slot++) {
                // measurements taken from Gui.renderHotbar
                guiGraphics.blit(
                        slotsOccupied.get(slot) ? fullSlotSprite : emptySlotSprite,
                        width/2 - 91 + 3 + slot * 20,
                        height - 20 + 1,
                        16, 16, // w/h to draw
                        0, 0, // offset in png
                        18, 18, 18, 18 // ???
                );
            }
            guiGraphics.pose().popPose();
        }
    }
}
