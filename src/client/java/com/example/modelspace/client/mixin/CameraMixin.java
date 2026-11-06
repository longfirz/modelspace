package com.example.modelspace.client.mixin;

import com.example.modelspace.client.FreecamController;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Invoker("setPosition")
	protected abstract void modelspace$setPos(double x, double y, double z);

	@Invoker("setRotation")
	protected abstract void modelspace$setRotation(float yaw, float pitch);

	@Inject(method = "update", at = @At("TAIL"))
	private void modelspace$override(DeltaTracker tracker, CallbackInfo ci) {
		if (!FreecamController.isActive()) {
			return;
		}
		float t = tracker.getGameTimeDeltaPartialTick(false);
		Vec3 p = FreecamController.interpolatedPos(t);
		modelspace$setPos(p.x, p.y, p.z);
		modelspace$setRotation(FreecamController.yaw(), FreecamController.pitch());
	}
}
