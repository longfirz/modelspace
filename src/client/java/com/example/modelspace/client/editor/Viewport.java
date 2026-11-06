package com.example.modelspace.client.editor;

import org.joml.Vector3d;

/**
 * Перспективная проекция камеры редактора. Координаты — в системе Workspace
 * (мировые минус угол блока Workspace). Экранные координаты — в GUI-единицах.
 */
public final class Viewport {
	public final Vector3d cam = new Vector3d();
	public final Vector3d fwd = new Vector3d();
	public final Vector3d right = new Vector3d();
	public final Vector3d up = new Vector3d();
	public final int width;
	public final int height;
	private final double tanHalf;
	private final double aspect;

	public Viewport(double cx, double cy, double cz, float yawDeg, float pitchDeg, double fovDeg, int width, int height) {
		this.width = Math.max(1, width);
		this.height = Math.max(1, height);
		cam.set(cx, cy, cz);
		double yaw = Math.toRadians(yawDeg);
		double pitch = Math.toRadians(pitchDeg);
		fwd.set(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		right.set(-Math.cos(yaw), 0.0, -Math.sin(yaw));
		right.cross(fwd, up);
		up.normalize();
		tanHalf = Math.tan(Math.toRadians(fovDeg) / 2.0);
		aspect = (double) this.width / this.height;
	}

	/** @return {sx, sy, depth} или null, если точка за камерой. */
	public double[] project(double x, double y, double z) {
		double dx = x - cam.x;
		double dy = y - cam.y;
		double dz = z - cam.z;
		double zc = dx * fwd.x + dy * fwd.y + dz * fwd.z;
		if (zc < 0.05) {
			return null;
		}
		double xc = dx * right.x + dy * right.y + dz * right.z;
		double yc = dx * up.x + dy * up.y + dz * up.z;
		double ndcX = xc / (zc * tanHalf * aspect);
		double ndcY = yc / (zc * tanHalf);
		return new double[] {(ndcX * 0.5 + 0.5) * width, (0.5 - ndcY * 0.5) * height, zc};
	}

	public double[] project(Vector3d p) {
		return project(p.x, p.y, p.z);
	}

	/** Единичное направление луча через точку экрана. */
	public Vector3d rayDir(double sx, double sy) {
		double ndcX = sx / width * 2.0 - 1.0;
		double ndcY = 1.0 - sy / height * 2.0;
		return new Vector3d(fwd)
			.fma(ndcX * tanHalf * aspect, right)
			.fma(ndcY * tanHalf, up)
			.normalize();
	}
}
