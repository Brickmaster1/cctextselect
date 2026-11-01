package com.atlas.cctextselect.mixin.client;

import dan200.computercraft.client.render.text.FixedWidthFontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FixedWidthFontRenderer.class)
public interface FixedWidthFontRendererAccessor {
    @Invoker("drawChar")
    static void callDrawChar(FixedWidthFontRenderer.QuadEmitter emitter, float x, float y, int index, int colour, int light) {
        throw new AssertionError();
    }
}