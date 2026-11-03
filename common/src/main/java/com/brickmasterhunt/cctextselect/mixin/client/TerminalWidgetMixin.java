package com.brickmasterhunt.cctextselect.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import dan200.computercraft.client.gui.widgets.TerminalWidget;
import dan200.computercraft.client.render.text.FixedWidthFontRenderer;
import dan200.computercraft.core.input.UserComputerInput;
import dan200.computercraft.core.terminal.Palette;
import dan200.computercraft.core.terminal.TextBuffer;
import dan200.computercraft.core.terminal.Terminal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static dan200.computercraft.client.render.ComputerBorderRenderer.BORDER;
import static dan200.computercraft.client.render.RenderTypes.FULL_BRIGHT_LIGHTMAP;
import static dan200.computercraft.client.render.text.FixedWidthFontRenderer.*;

@Mixin(TerminalWidget.class)
public class TerminalWidgetMixin {
    @Shadow private Terminal terminal;
    @Shadow @Final private UserComputerInput computerInput;
    @Shadow private int innerX;
    @Shadow private int innerY;
    @Shadow private int innerWidth;
    @Shadow private int innerHeight;

    @Unique private boolean ccselect$selecting = false;
    @Unique private boolean ccselect$hasSelection = false;
    @Unique private boolean ccselect$pendingClear = false;

    @Unique private int ccselect$pressX = -1;
    @Unique private int ccselect$pressY = -1;

    @Unique private int ccselect$startX = -1;
    @Unique private int ccselect$startY = -1;
    @Unique private int ccselect$endX = -1;
    @Unique private int ccselect$endY = -1;

    @Unique private long ccselect$lastClickTime = 0;
    @Unique private int ccselect$clickCount = 0;
    @Unique private static final long MULTI_CLICK_MS = 400;

    // fallback overlay alpha
    @Unique private static final int SELECTION_BG_FALLBACK = 0x80404040;

    @Unique private static final String HINT_TEXT = "Selection Mode";
    @Unique private static final int HINT_PADDING = 6;

    @Unique
    private boolean ccselect$isSelectingMode() {
        return Screen.hasShiftDown();
    }

    @Unique
    private boolean ccselect$isInside(double mouseX, double mouseY) {
        return mouseX >= innerX && mouseY >= innerY && mouseX < innerX + innerWidth && mouseY < innerY + innerHeight;
    }

    @Unique
    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true)
    private void ccselect$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!ccselect$isSelectingMode() || button != 0) return;
        if (!ccselect$isInside(mouseX, mouseY)) return;

        int x = clamp((int) ((mouseX - innerX) / FONT_WIDTH), 0, terminal.getWidth() - 1);
        int y = clamp((int) ((mouseY - innerY) / FONT_HEIGHT), 0, terminal.getHeight() - 1);

        long now = System.currentTimeMillis();
        if (now - ccselect$lastClickTime <= MULTI_CLICK_MS) {
            ccselect$clickCount++;
        } else {
            ccselect$clickCount = 1;
        }
        ccselect$lastClickTime = now;

        if (ccselect$clickCount >= 3) {
            // Triple click: whole line
            ccselect$startX = 0;
            ccselect$endX = terminal.getWidth() - 1;
            ccselect$startY = y;
            ccselect$endY = y;
            ccselect$clickCount = 0;
            ccselect$selecting = true;
            ccselect$hasSelection = true;
        } else if (ccselect$clickCount == 2) {
            // Double click: select word
            ccselect$selectWord(x, y);
            ccselect$selecting = true;
            ccselect$hasSelection = true;
        } else {
            // Single click:
            if (ccselect$hasSelection && !ccselect$selecting) {
                // Clear existing selection visually but remember the press so a drag can start a new selection.
                ccselect$hasSelection = false;
                ccselect$pendingClear = true;
                ccselect$pressX = x;
                ccselect$pressY = y;
                // consume the click
                cir.setReturnValue(true);
                return;
            }

            // Start a normal single-click selection (clicked & will drag)
            ccselect$startX = x;
            ccselect$startY = y;
            ccselect$endX = x;
            ccselect$endY = y;
            ccselect$selecting = true;
            ccselect$hasSelection = true;
        }

        cir.setReturnValue(true); // prevent CC mouse event
    }

    @Inject(method = "mouseDragged(DDIDD)Z", at = @At("HEAD"), cancellable = true)
    private void ccselect$mouseDragged(double mouseX, double mouseY, int button, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        // If we previously cleared selection on press but the user started dragging, begin a new selection.
        if (ccselect$pendingClear) {
            ccselect$startX = ccselect$pressX;
            ccselect$startY = ccselect$pressY;
            ccselect$endX = ccselect$pressX;
            ccselect$endY = ccselect$pressY;
            ccselect$selecting = true;
            ccselect$pendingClear = false;
            ccselect$hasSelection = true;
            // fall through to update the selection below
        }

        if (!ccselect$selecting) return;

        // Allow dragging outside the terminal; auto-scroll the running program
        // when the mouse is above/below the terminal region.
        int charX = (int) ((mouseX - innerX) / FONT_WIDTH);

        if (mouseY < innerY) {
            // above top -> scroll up in the program
            int safeCharX = clamp(charX, 0, terminal.getWidth() - 1);
            try {
                computerInput.mouseScroll(1, safeCharX + 1, 1);
            } catch (Throwable ignored) { }
            ccselect$endX = clamp(charX, 0, terminal.getWidth() - 1);
            ccselect$endY = 0;
            cir.setReturnValue(true);
            return;
        } else if (mouseY >= innerY + innerHeight) {
            // below bottom -> scroll down in the program
            int safeCharX = clamp(charX, 0, terminal.getWidth() - 1);
            try {
                computerInput.mouseScroll(-1, safeCharX + 1, terminal.getHeight());
            } catch (Throwable ignored) { }
            ccselect$endX = clamp(charX, 0, terminal.getWidth() - 1);
            ccselect$endY = terminal.getHeight() - 1;
            cir.setReturnValue(true);
            return;
        }

        // Normal in-term dragging
        int charY = clamp((int) ((mouseY - innerY) / FONT_HEIGHT), 0, terminal.getHeight() - 1);
        charX = clamp(charX, 0, terminal.getWidth() - 1);

        ccselect$endX = charX;
        ccselect$endY = charY;

        cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased(DDI)Z", at = @At("HEAD"), cancellable = true)
    private void ccselect$mouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // If we cleared on press and never dragged, cancel the pending clear and do nothing.
        if (ccselect$pendingClear) {
            ccselect$pendingClear = false;
            cir.setReturnValue(true);
            return;
        }

        if (!ccselect$selecting) return;

        ccselect$selecting = false;
        ccselect$hasSelection = true;

        // Copy very last selected text to clipboard automatically
        String selected = ccselect$getSelectedText();
        if (!selected.isEmpty()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(selected);
        }

        cir.setReturnValue(true); // prevent default computer mouseUp handling
    }

    @Unique
    private void ccselect$renderHint(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        int termWidthPx = terminal.getWidth() * FONT_WIDTH;
        int hintWidth = mc.font.width(HINT_TEXT);

        int x = innerX + termWidthPx - hintWidth - HINT_PADDING;
        int y = (innerY - BORDER) - mc.font.lineHeight - HINT_PADDING;
        if (x < 4) x = 4;

        graphics.fill(x - 4, y - 2, x + hintWidth + 4, y + mc.font.lineHeight + 2, 0xAA000000);
        graphics.drawString(mc.font, Component.literal(HINT_TEXT), x, y, 0xFFCCCCCC);
    }

    @Inject(method = "renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
    private void ccselect$renderSelection(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci, @Local(name = "emitter") FixedWidthFontRenderer.QuadEmitter emitter) {
        if (ccselect$hasSelection) {
            int minY = Math.min(ccselect$startY, ccselect$endY);
            int maxY = Math.max(ccselect$startY, ccselect$endY);

            // Draw selection backgrounds, merge adjacent cells that have the same original foreground color.
            for (int y = minY; y <= maxY; y++) {
                int startX = (y == minY) ? Math.min(ccselect$startX, ccselect$endX) : 0;
                int endX = (y == maxY) ? Math.max(ccselect$startX, ccselect$endX) : terminal.getWidth() - 1;

                // Clamp to terminal bounds
                startX = clamp(startX, 0, terminal.getWidth() - 1);
                endX = clamp(endX, 0, terminal.getWidth() - 1);

                TextBuffer textColour = terminal.getTextColourLine(y);

                int runStart = startX;
                int runColour = terminal.getPalette().getRenderColours(
                    FixedWidthFontRenderer.getColour(textColour.charAt(startX),
                        dan200.computercraft.core.util.Colour.BLACK)
                );

                for (int x = startX + 1; x <= endX + 1; x++) {
                    int colour = x <= endX
                        ? terminal.getPalette().getRenderColours(
                            FixedWidthFontRenderer.getColour(textColour.charAt(x),
                                dan200.computercraft.core.util.Colour.BLACK)
                        )
                        : -1;

                    if (colour != runColour) {
                        float px = innerX + runStart * FONT_WIDTH;
                        float py = innerY + y * FONT_HEIGHT;
                        float width = (x - runStart) * FONT_WIDTH;

                        FixedWidthFontRenderer.drawQuad(emitter, px, py, 0, width, FONT_HEIGHT, runColour, FULL_BRIGHT_LIGHTMAP);

                        runStart = x;
                        runColour = colour;
                    }
                }
            }

            // Draw text characters with the background colour instead.
            for (int y = minY; y <= maxY; y++) {
                int startX = (y == minY)
                        ? Math.min(ccselect$startX, ccselect$endX)
                        : 0;

                int endX = (y == maxY)
                        ? Math.max(ccselect$startX, ccselect$endX)
                        : terminal.getWidth() - 1;

                startX = clamp(startX, 0, terminal.getWidth() - 1);
                endX = clamp(endX, 0, terminal.getWidth() - 1);

                TextBuffer text = terminal.getLine(y);
                TextBuffer bgColour = terminal.getBackgroundColourLine(y);

                for (int x = startX; x <= endX; x++) {
                    int glyph = text.charAt(x);
                    if (glyph > 255) glyph = '?';

                    int glyphColour = terminal.getPalette().getRenderColours(
                            FixedWidthFontRenderer.getColour(bgColour.charAt(x),
                                dan200.computercraft.core.util.Colour.BLACK)
                    );

                    FixedWidthFontRendererAccessor.callDrawChar(emitter, innerX + x * FONT_WIDTH, innerY + y * FONT_HEIGHT, glyph, glyphColour, FULL_BRIGHT_LIGHTMAP);
                }
            }

            // Really hate this solution but whatever I'm not gonna deal with the mixin BS to threadlocal copy ts or sum shit
            emitter.poseMatrix().translate(0.0f, 0.0f, -FixedWidthFontRendererAccessor.getZOffset());
        }

        // Draw the hint if the user is in selection mode.
        // This needs to be done after now because GuiGraphics flushes the buffer on you.
        if (ccselect$isSelectingMode()) {
            ccselect$renderHint(graphics);
        }
    }

    @Unique
    private void ccselect$selectWord(int x, int y) {
        TextBuffer buffer = terminal.getLine(y);
        if (buffer == null) return;

        int width = terminal.getWidth();

        int start = x;
        int end = x;

        while (start > 0 && ccselect$isWordChar(buffer.charAt(start - 1))) start--;
        while (end < width - 1 && ccselect$isWordChar(buffer.charAt(end + 1))) end++;

        ccselect$startX = start;
        ccselect$endX = end;
        ccselect$startY = y;
        ccselect$endY = y;
    }

    @Unique
    private boolean ccselect$isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.';
    }

    @Unique
    private String ccselect$getSelectedText() {
        if (!ccselect$hasSelection) return "";

        int minY = Math.min(ccselect$startY, ccselect$endY);
        int maxY = Math.max(ccselect$startY, ccselect$endY);

        int minX = Math.min(ccselect$startX, ccselect$endX);
        int maxX = Math.max(ccselect$startX, ccselect$endX);

        StringBuilder builder = new StringBuilder();

        for (int y = minY; y <= maxY; y++) {
            TextBuffer buffer = terminal.getLine(y);
            if (buffer == null) continue;

            int start = (y == minY) ? minX : 0;
            int end = (y == maxY) ? maxX : terminal.getWidth() - 1;

            start = clamp(start, 0, terminal.getWidth() - 1);
            end = clamp(end, 0, terminal.getWidth() - 1);

            for (int i = start; i <= end; i++) {
                builder.append(buffer.charAt(i));
            }
            if (y != maxY) builder.append("\n");
        }

        return builder.toString();
    }

    @Unique
    private void ccselect$selectAll() {
        ccselect$startX = 0;
        ccselect$startY = 0;
        ccselect$endX = terminal.getWidth() - 1;
        ccselect$endY = terminal.getHeight() - 1;
        ccselect$hasSelection = true;
    }

    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true)
    private void ccselect$keyPressed(int key, int scancode, int modifiers,
                                     CallbackInfoReturnable<Boolean> cir) {

        if (Screen.isSelectAll(key)) {
            ccselect$selectAll();
            cir.setReturnValue(true);
            return;
        }

        if (ccselect$hasSelection && Screen.isCopy(key)) {
            String text = ccselect$getSelectedText();
            if (!text.isEmpty()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(text);
            }
            cir.setReturnValue(true);
            return;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE && ccselect$hasSelection) {
            ccselect$hasSelection = false;
            ccselect$selecting = false;
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyReleased(III)Z", at = @At("HEAD"))
    private void ccselect$keyReleased(int key, int scancode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            // If Shift got released, drop any selection state
            if (!Screen.hasShiftDown()) {
                ccselect$hasSelection = false;
                ccselect$selecting = false;
                ccselect$pendingClear = false;
            }
        }
    }
}
