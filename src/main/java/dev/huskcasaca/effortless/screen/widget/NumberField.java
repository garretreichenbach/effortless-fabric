package dev.huskcasaca.effortless.screen.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.ParametersAreNonnullByDefault;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Environment(EnvType.CLIENT)
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class NumberField implements Renderable {

    public int x, y, width, height;
    public int buttonWidth = 10;

    protected EditBox textField;
    protected Button minusButton, plusButton;

    List<Component> tooltip = new ArrayList<>();

    public NumberField(Font font, List<Renderable> renderables, int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        textField = new EditBox(font, x + buttonWidth + 1, y + 1, width - 2 * buttonWidth - 2, height - 2, Component.empty());
        minusButton = new Button(x, y - 1, buttonWidth, height + 2, Component.literal("-"), button -> {
            float valueChanged = 1f;
            if (Screen.hasControlDown()) valueChanged = 5f;
            if (Screen.hasShiftDown()) valueChanged = 10f;

            setNumber(getNumber() - valueChanged);
        }, Button.DEFAULT_NARRATION);
        plusButton = new Button(x + width - buttonWidth, y - 1, buttonWidth, height + 2, Component.literal("+"), button -> {
            float valueChanged = 1f;
            if (Screen.hasControlDown()) valueChanged = 5f;
            if (Screen.hasShiftDown()) valueChanged = 10f;

            setNumber(getNumber() + valueChanged);
        }, Button.DEFAULT_NARRATION);

        renderables.add(minusButton);
        renderables.add(plusButton);
    }

    public double getNumber() {
        if (textField.getValue().isEmpty()) return 0;
        try {
            return DecimalFormat.getInstance().parse(textField.getValue()).doubleValue();
        } catch (ParseException e) {
            return 0;
        }
    }

    public void setNumber(double number) {
        textField.setValue(DecimalFormat.getInstance().format(number));
    }

    public void setTooltip(Component tooltip) {
        setTooltip(Collections.singletonList(tooltip));
    }

    public void setTooltip(List<Component> tooltip) {
        this.tooltip = tooltip;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean result = textField.mouseClicked(mouseX, mouseY, button);

        //Check if clicked inside textfield
        boolean flag = mouseX >= x + buttonWidth && mouseX < x + width - buttonWidth && mouseY >= y && mouseY < y + height;

        //Rightclicked inside textfield
        if (flag && button == 1) {
            textField.setValue("");
            textField.setFocused(true);
            result = true;
        }

        return result;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        textField.setY(y + 1);
        minusButton.setY(y - 1);
        plusButton.setY(y - 1);

        textField.render(guiGraphics, mouseX, mouseY, partialTicks);
        minusButton.render(guiGraphics, mouseX, mouseY, partialTicks);
        plusButton.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    public void drawTooltip(GuiGraphics guiGraphics, Screen screen, int mouseX, int mouseY) {
        boolean insideTextField = mouseX >= x + buttonWidth && mouseX < x + width - buttonWidth && mouseY >= y && mouseY < y + height;
        boolean insideMinusButton = mouseX >= x && mouseX < x + buttonWidth && mouseY >= y && mouseY < y + height;
        boolean insidePlusButton = mouseX >= x + width - buttonWidth && mouseX < x + width && mouseY >= y && mouseY < y + height;

        // List<String> textLines = new ArrayList<>();

        List<Component> textLines = new ArrayList<>();


        if (insideTextField) {
            textLines.addAll(tooltip);
//            textLines.add(TextFormatting.GRAY + "Tip: try scrolling.");
        }

        if (insideMinusButton) {
            textLines.add(Component.literal("Hold ").append(Component.literal("shift ").withStyle(ChatFormatting.AQUA)).append("for ")
                    .append(Component.literal("10").withStyle(ChatFormatting.RED)));
            textLines.add(Component.literal("Hold ").append(Component.literal("ctrl ").withStyle(ChatFormatting.AQUA)).append("for ")
                    .append(Component.literal("5").withStyle(ChatFormatting.RED)));
        }

        if (insidePlusButton) {
            textLines.add(Component.literal("Hold ").append(Component.literal("shift ").withStyle(ChatFormatting.DARK_GREEN)).append("for ")
                    .append(Component.literal("10").withStyle(ChatFormatting.RED)));
            textLines.add(Component.literal("Hold ").append(Component.literal("ctrl ").withStyle(ChatFormatting.DARK_GREEN)).append("for ")
                    .append(Component.literal("5").withStyle(ChatFormatting.RED)));
        }

        guiGraphics.renderComponentTooltip(Minecraft.getInstance().font , textLines, mouseX - 10, mouseY + 25);

    }

    public boolean charTyped(char typedChar, int keyCode) {
        if (!textField.isFocused()) return false;
//        if (Character.isDigit(typedChar) || typedChar == '.' || typedChar == '-' || keyCode == Keyboard.KEY_BACK
//            || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT
//            || keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
        return textField.charTyped(typedChar, keyCode);
//        }
    }

    //Scroll inside textfield to change number
    //Disabled because entire screen can be scrolled
//    public void handleMouseInput(int mouseX, int mouseY) {
//        boolean insideTextField = mouseX >= x + buttonWidth && mouseX < x + width - buttonWidth && mouseY >= y && mouseY < y + height;
//
//        if (insideTextField)
//        {
//            int valueChanged = 0;
//            if (Mouse.getEventDWheel() > 0)
//                valueChanged = 1;
//            if (Mouse.getEventDWheel() < 0)
//                valueChanged = -1;
//
//            if (valueChanged != 0)
//                setNumber(getNumber() + valueChanged);
//        }
//    }
}
