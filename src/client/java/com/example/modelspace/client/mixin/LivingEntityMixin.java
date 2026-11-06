package com.example.modelspace.client.mixin;

import com.example.modelspace.client.FreecamController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/** Настоящий игрок во время сессии не двигается. */
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void modelspace$freezePlayer(Vec3 input, CallbackInfo ci) {
		if (FreecamController.isActive() && (Object) this == Minecraft.getInstance().player) {
			ci.cancel();
		}
	}
}
