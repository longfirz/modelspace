package com.example.modelspace.client.mixin;

import com.example.modelspace.client.FreecamController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	/** Колесо меняет скорость полёта камеры, а не слот хотбара. */
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void modelspace$scroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		// Мышь захвачена только когда нет открытого экрана (меню, инвентарь, чат)
		if (FreecamController.isActive() && Minecraft.getInstance().mouseHandler.isMouseGrabbed()) {
			FreecamController.adjustSpeed(vertical);
			ci.cancel();
		}
	}
}
