package com.example.modelspace.client.mixin;

import com.example.modelspace.client.FreecamController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {
        "net.minecraft.client.gui.Hud",
        "net.minecraft.client.renderer.Hud"
})
public abstract class HudHideMixin {
    @Inject(
            method = {
                    "render",
                    "submit",
                    "extractRenderState"
            },
            at = @At("HEAD"),
            cancellable = true
    )
    private void modelspace$hideHud(CallbackInfo ci) {
        if (FreecamController.isActive()) {
            ci.cancel();
        }
    }
}