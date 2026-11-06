package com.example.modelspace.client.editor;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Примитивы 2D-рисования поверх GUI (линии через fill). */
final class DrawUtil {
	private DrawUtil() {}

	static void line(GuiGraphicsExtractor g, double[] a, double[] b, int color, int thick, int w, int h) {
		if (a == null || b == null) {
			return;
		}
		line(g, a[0], a[1], b[0], b[1], color, thick, w, h);
	}

	static void line(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, int color, int thick, int w, int h) {
		// Liang–Barsky: обрезаем отрезок по экрану
		double dx = x1 - x0;
		double dy = y1 - y0;
		double t0 = 0.0;
		double t1 = 1.0;
		double[] p = {-dx, dx, -dy, dy};
		double[] q = {x0 + 2, w + 2 - x0, y0 + 2, h + 2 - y0};
		for (int i = 0; i < 4; i++) {
			if (p[i] == 0.0) {
				if (q[i] < 0.0) {
					return;
				}
			} else {
				double r = q[i] / p[i];
				if (p[i] < 0.0) {
					t0 = Math.max(t0, r);
				} else {
					t1 = Math.min(t1, r);
				}
			}
		}
		if (t0 > t1) {
			return;
		}
		double ax = x0 + dx * t0;
		double ay = y0 + dy * t0;
		double bx = x0 + dx * t1;
		double by = y0 + dy * t1;
		double len = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
		int steps = (int) Math.min(Math.ceil(len / thick), 700);
		if (steps <= 0) {
			g.fill((int) ax, (int) ay, (int) ax + thick, (int) ay + thick, color);
			return;
		}
		for (int i = 0; i <= steps; i++) {
			int x = (int) Math.round(ax + (bx - ax) * i / steps);
			int y = (int) Math.round(ay + (by - ay) * i / steps);
			g.fill(x, y, x + thick, y + thick, color);
		}
	}

	static void box(GuiGraphicsExtractor g, double cx, double cy, int half, int color) {
		g.fill((int) cx - half, (int) cy - half, (int) cx + half + 1, (int) cy + half + 1, color);
	}

	static double pointSegmentDist(double px, double py, double ax, double ay, double bx, double by) {
		double vx = bx - ax;
		double vy = by - ay;
		double len2 = vx * vx + vy * vy;
		double t = len2 < 1e-9 ? 0.0 : Math.max(0.0, Math.min(1.0, ((px - ax) * vx + (py - ay) * vy) / len2));
		double cx = ax + vx * t;
		double cy = ay + vy * t;
		return Math.hypot(px - cx, py - cy);
	}
}
