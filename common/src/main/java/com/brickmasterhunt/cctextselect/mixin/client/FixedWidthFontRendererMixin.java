package com.brickmasterhunt.cctextselect.mixin.client;

import dan200.computercraft.client.render.text.FixedWidthFontRenderer;
import dan200.computercraft.core.terminal.Terminal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FixedWidthFontRenderer.class)
public class FixedWidthFontRendererMixin {
    @Inject(method = "drawTerminal",
            at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;set(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;"),
            cancellable = true)
    private static void ccselect$deferTransform(FixedWidthFontRenderer.QuadEmitter emitter, float x, float y, Terminal terminal,
        float topMarginSize, float bottomMarginSize, float leftMarginSize, float rightMarginSize, CallbackInfo ci
    ) {
        ci.cancel();
    }
}
