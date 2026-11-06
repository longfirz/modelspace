package com.example.modelspace.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Отвязанная камера для сессии редактирования. */
public final class FreecamController {
	private static final double ACCEL = 0.04;      // блоков/тик^2 при множителе 1.0
	private static final double SPRINT_MULT = 2.0;
	private static final double DAMPING = 0.88;
	private static final double MIN_SPEED = 0.15;
	private static final double MAX_SPEED = 6.0;

	private static boolean active = false;
	private static Vec3 pos = Vec3.ZERO;
	private static Vec3 prevPos = Vec3.ZERO;
	private static Vec3 velocity = Vec3.ZERO;
	private static float yaw;
	private static float pitch;
	private static double speedMult = 1.0;
	private static CameraType prevCameraType = CameraType.FIRST_PERSON;
	private static boolean prevSmartCull = true;

	// Ввод движения. Заполняется экраном редактора: пока он открыт, KeyMapping.isDown() не обновляется.
	private static boolean inFwd, inBack, inLeft, inRight, inUp, inDown, inSprint;

	private FreecamController() {}

	public static boolean isActive() { return active; }
	public static float yaw() { return yaw; }
	public static float pitch() { return pitch; }
	public static double speedMult() { return speedMult; }
	public static Vec3 interpolatedPos(float t) { return prevPos.lerp(pos, t); }

	public static void start(Minecraft mc, BlockPos anchor) {
		if (active || mc == null || mc.player == null || mc.level == null) {
			return;
		}
		pos = mc.player.getEyePosition(1.0F);
		prevPos = pos;
		velocity = Vec3.ZERO;
		yaw = mc.player.getYRot();
		pitch = mc.player.getXRot();

		prevCameraType = mc.options.getCameraType();
		mc.options.setCameraType(CameraType.FIRST_PERSON);
		prevSmartCull = mc.smartCull;
		mc.smartCull = false;

		EditorState.reset();
		EditorState.anchor = anchor;
		ModelSpaceKeys.drain();
		active = true;
	}

	public static void stop(Minecraft mc) {
		if (!active) {
			return;
		}
		active = false;
		velocity = Vec3.ZERO;
		clearInput();
		if (mc != null) {
			if (mc.options != null) {
				mc.options.setCameraType(prevCameraType);
			}
			mc.smartCull = prevSmartCull;
		}
	}

	public static void setInput(boolean fwd, boolean back, boolean left, boolean right, boolean up, boolean down, boolean sprint) {
		inFwd = fwd;
		inBack = back;
		inLeft = left;
		inRight = right;
		inUp = up;
		inDown = down;
		inSprint = sprint;
	}

	public static void clearInput() {
		setInput(false, false, false, false, false, false, false);
	}

	private static Vec3 lookVector() {
		double y = Math.toRadians(yaw);
		double p = Math.toRadians(pitch);
		return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
	}

	/** Прокрутка колеса: приближение/удаление вдоль взгляда. */
	public static void dolly(double scroll) {
		Vec3 d = lookVector().scale(scroll * 0.8 * Math.max(0.5, speedMult));
		pos = pos.add(d);
		prevPos = prevPos.add(d);
	}

	/** Панорамирование средней кнопкой мыши (dx, dy — смещение курсора в GUI-единицах). */
	public static void pan(double dx, double dy) {
		double y = Math.toRadians(yaw);
		Vec3 right = new Vec3(-Math.cos(y), 0.0, -Math.sin(y));
		Vec3 fwd = lookVector();
		Vec3 up = right.cross(fwd).normalize();
		double k = 0.012 * Math.max(0.5, speedMult);
		Vec3 d = right.scale(-dx * k).add(up.scale(dy * k));
		pos = pos.add(d);
		prevPos = prevPos.add(d);
	}

	/** Поставить камеру так, чтобы смотреть на target с расстояния dist. */
	public static void focusOn(Vec3 target, double dist) {
		pos = target.subtract(lookVector().scale(dist));
		prevPos = pos;
		velocity = Vec3.ZERO;
	}

	public static void applyMouseDelta(double dx, double dy) {
		yaw += (float) dx * 0.15F;
		pitch = Math.max(-90.0F, Math.min(90.0F, pitch + (float) dy * 0.15F));
	}

	public static void adjustSpeed(double scroll) {
		if (scroll == 0.0) {
			return;
		}
		double factor = scroll > 0.0 ? 1.15 : 1.0 / 1.15;
		speedMult = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speedMult * factor));
	}

	public static void tick(Minecraft mc) {
		if (!active) {
			return;
		}
		// Вышли в меню/отключились/умерли — сессию закрываем
		if (mc.player == null || mc.level == null || !mc.player.isAlive()) {
			stop(mc);
			return;
		}

		prevPos = pos;
		mc.player.setDeltaMovement(Vec3.ZERO); // настоящий игрок стоит на месте

		double yawRad = Math.toRadians(yaw);
		Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
		Vec3 right = new Vec3(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));

		Vec3 move = Vec3.ZERO;
		if (inFwd) move = move.add(forward);
		if (inBack) move = move.subtract(forward);
		if (inRight) move = move.add(right);
		if (inLeft) move = move.subtract(right);
		if (inUp) move = move.add(0.0, 1.0, 0.0);
		if (inDown) move = move.subtract(0.0, 1.0, 0.0);

		if (move.lengthSqr() > 0.0) {
			double a = ACCEL * speedMult * (inSprint ? SPRINT_MULT : 1.0);
			move = move.normalize().scale(a);
		}
		velocity = velocity.scale(DAMPING).add(move);
		pos = pos.add(velocity);
	}
}
