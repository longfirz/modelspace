package com.example.modelspace.client.mixin;

import com.example.modelspace.client.FreecamController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
	/** Пока идёт сессия, мышь вращает камеру, а не игрока. */
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void modelspace$redirectLook(double dx, double dy, CallbackInfo ci) {
		if (!FreecamController.isActive()) {
			return;
		}
		if ((Object) this == Minecraft.getInstance().player) {
			FreecamController.applyMouseDelta(dx, dy);
			ci.cancel();
		}
	}
}
