package dev.huskcasaca.effortless.screen.buildmodifier;

import dev.huskcasaca.effortless.Effortless;
import dev.huskcasaca.effortless.buildmodifier.BuildModifierHelper;
import dev.huskcasaca.effortless.buildmodifier.array.Array;
import dev.huskcasaca.effortless.building.ReachHelper;
import dev.huskcasaca.effortless.screen.widget.ExpandableScrollEntry;
import dev.huskcasaca.effortless.screen.widget.NumberField;
import dev.huskcasaca.effortless.screen.widget.ScrollPane;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ArraySettingsPane extends ExpandableScrollEntry {

    protected List<NumberField> arrayNumberFieldList = new ArrayList<>();

    private Checkbox buttonArrayEnabled;
    private NumberField textArrayOffsetX, textArrayOffsetY, textArrayOffsetZ, textArrayCount;

    public ArraySettingsPane(ScrollPane scrollPane) {
        super(scrollPane);
    }

    @Override
    public void init(List<Renderable> renderables) {
        super.init(renderables);
        var modifierSettings = BuildModifierHelper.getModifierSettings(mc.player);
        var arraySettings = (modifierSettings != null)? modifierSettings.arraySettings() : new Array.ArraySettings();
        int y = top;
        buttonArrayEnabled = new Checkbox(left - 25 + 8, y, 20, 20, Component.literal(""), arraySettings.enabled(), false) {
            @Override
            public void onPress() {
                super.onPress();
                setCollapsed(!buttonArrayEnabled.selected());
            }
        };
        renderables.add(buttonArrayEnabled);

        y = top + 20;
        textArrayOffsetX = new NumberField(font, renderables, left + 60, y, 90, 18);
        textArrayOffsetX.setNumber(arraySettings.offset().getX());
        textArrayOffsetX.setTooltip(Component.literal("How much each copy is shifted."));
        arrayNumberFieldList.add(textArrayOffsetX);

        textArrayOffsetY = new NumberField(font, renderables, left + 60, y + 24, 90, 18);
        textArrayOffsetY.setNumber(arraySettings.offset().getY());
        textArrayOffsetY.setTooltip(Component.literal("How much each copy is shifted."));
        arrayNumberFieldList.add(textArrayOffsetY);

        textArrayOffsetZ = new NumberField(font, renderables, left + 60, y + 24 * 2, 90, 18);
        textArrayOffsetZ.setNumber(arraySettings.offset().getZ());
        textArrayOffsetZ.setTooltip(Component.literal("How much each copy is shifted."));
        arrayNumberFieldList.add(textArrayOffsetZ);

        textArrayCount = new NumberField(font, renderables, left + 200, y, 80, 18);
        textArrayCount.setNumber(arraySettings.count());
        textArrayCount.setTooltip(Component.literal("How many copies should be made."));
        arrayNumberFieldList.add(textArrayCount);

        setCollapsed(!buttonArrayEnabled.selected());
     }



    @Override
    public void drawEntry(GuiGraphics guiGraphics, int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY,
                          boolean isSelected, float partialTicks) {
        int yy = y;
        int offset = 8;

        buttonArrayEnabled.render(guiGraphics, mouseX, mouseY, partialTicks);
        if (buttonArrayEnabled.selected()) {
            buttonArrayEnabled.setY(yy);
            guiGraphics.drawString(font, "Array enabled", left + offset, yy + 8, 0xFFFFFF);

            var positionOffsetX0 = left + 8;
            var positionOffsetX1 = left + 160;
            var positionOffsetY0 = y + 10 + 24;
            var positionOffsetY1 = y + 10 + 24 * 2;

            var textOffsetX = 40;
            var componentOffsetX = 15;
            var componentOffsetY = -5;

            guiGraphics.drawString(font, "Offset", positionOffsetX0, positionOffsetY0, 0xFFFFFF);
            guiGraphics.drawString(font, "X", positionOffsetX0 + textOffsetX, positionOffsetY0, 0xFFFFFF);
            guiGraphics.drawString(font, "Y", positionOffsetX0 + textOffsetX, positionOffsetY0 + 24, 0xFFFFFF);
            guiGraphics.drawString(font, "Z", positionOffsetX0 + textOffsetX, positionOffsetY0 + 24 * 2, 0xFFFFFF);
            textArrayOffsetX.y = positionOffsetY0 + componentOffsetY;
            textArrayOffsetY.y = positionOffsetY0 + componentOffsetY + 24;
            textArrayOffsetZ.y = positionOffsetY0 + componentOffsetY + 24 * 2;

            guiGraphics.drawString(font, "Count", positionOffsetX1, positionOffsetY0, 0xFFFFFF);
            textArrayCount.y = positionOffsetY0 + componentOffsetY;

            int currentReach = Math.max(-1, getArrayReach());
            int maxReach = ReachHelper.getMaxReachDistance(mc.player);
            var reachColor = isCurrentReachValid(currentReach, maxReach) ? ChatFormatting.GRAY : ChatFormatting.RED;
            var reachText = "Reach  " + reachColor + currentReach + ChatFormatting.GRAY + "/" + ChatFormatting.GRAY + maxReach;

            guiGraphics.drawString(font, reachText, positionOffsetX1, positionOffsetY1, 0xFFFFFF);

            arrayNumberFieldList.forEach(numberField -> numberField.render(guiGraphics, mouseX, mouseY, partialTicks));
        } else {
            buttonArrayEnabled.setY(yy);
            guiGraphics.drawString(font, "Array disabled", left + offset, yy + 8, 0x999999);
        }

    }

    public void drawTooltip(GuiGraphics guiGraphics, Screen guiScreen, int mouseX, int mouseY) {
        //Draw tooltips last
        if (buttonArrayEnabled.selected()) {
            arrayNumberFieldList.forEach(numberField -> numberField.drawTooltip(guiGraphics, scrollPane.parent, mouseX, mouseY));
        }
    }

    @Override
    public boolean charTyped(char typedChar, int keyCode) {
        super.charTyped(typedChar, keyCode);
        for (NumberField numberField : arrayNumberFieldList) {
            numberField.charTyped(typedChar, keyCode);
        }
        return true;
    }

    @Override
    public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
        arrayNumberFieldList.forEach(numberField -> numberField.mouseClicked(mouseX, mouseY, mouseEvent));

        boolean insideArrayEnabledLabel = mouseX >= left && mouseX < right && relativeY >= 4 && relativeY < 16;

        if (insideArrayEnabledLabel) {
            buttonArrayEnabled.playDownSound(this.mc.getSoundManager());
            buttonArrayEnabled.onClick(mouseX, mouseY);
        }

        return true;
    }

    public Array.ArraySettings getArraySettings() {
        boolean arrayEnabled = buttonArrayEnabled.selected();
        var arrayOffset = new BlockPos(0, 0, 0);
        try {
            arrayOffset = BlockPos.containing(textArrayOffsetX.getNumber(), textArrayOffsetY.getNumber(), textArrayOffsetZ.getNumber());
        } catch (NumberFormatException | NullPointerException ex) {
            Effortless.log(mc.player, "Array offset not a valid number.");
        }

        int arrayCount = 5;
        try {
            arrayCount = (int) textArrayCount.getNumber();
        } catch (NumberFormatException | NullPointerException ex) {
            Effortless.log(mc.player, "Array count not a valid number.");
        }

        return new Array.ArraySettings(arrayEnabled, arrayOffset, arrayCount);
    }

    @Override
    protected String getName() {
        return "Array";
    }

    @Override
    protected int getExpandedHeight() {
        return 106;
    }

    private int getArrayReach() {
        try {
            //find largest offset
            double x = Math.abs(textArrayOffsetX.getNumber());
            double y = Math.abs(textArrayOffsetY.getNumber());
            double z = Math.abs(textArrayOffsetZ.getNumber());
            double largestOffset = Math.max(Math.max(x, y), z);
            var count = textArrayCount.getNumber();
            return (int) (count > 1 ? largestOffset * count : 0);
        } catch (NumberFormatException | NullPointerException ex) {
            return 0;
        }
    }

    private boolean isCurrentReachValid(int currentReach, int maxReach) {
        return currentReach <= maxReach && currentReach > -1;
    }
}
