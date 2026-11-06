package com.example.modelspace.client.editor;

import com.example.modelspace.client.EditorState;
import com.example.modelspace.model.ModelPart;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Манипуляторы Move/Rotate/Scale. Рисуются 2D-оверлеем (проекция своей камеры),
 * ручки выбираются по расстоянию на экране, а само перетаскивание считается
 * честной 3D-математикой (луч мыши против оси / плоскости кольца).
 */
public final class Gizmo {
	private static final int[] AXIS_COLORS = {0xFFE64B4B, 0xFF4BD264, 0xFF4B8CFF};
	private static final int HOT = 0xFFFFE14B;
	private static final int PIVOT_COLOR = 0xFFFF9BFF;
	private static final int RING_SEGMENTS = 48;
	private static final double PICK_PX = 7.0;

	// Шаги привязки. NORMAL — ступенчато, FINE — мелко.
	private static final double MOVE_STEP_NORMAL = 0.0625;
	private static final double MOVE_STEP_FINE = 0.02;
	private static final double ROT_STEP_NORMAL = 15.0;
	private static final double ROT_STEP_FINE = 1.0;

	public final Vector3d origin = new Vector3d();
	public final Vector3d[] axes = {new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)};
	public double size = 1.0;
	public boolean visible;
	public int hover = -1;
	public int active = -1;
	/** Менялись ли значения во время текущего перетаскивания. */
	public boolean changed;

	// состояние перетаскивания
	private EditorState.Tool dragTool;
	private boolean dragPivot;
	private int dragAxis;
	private final Vector3d dragOrigin = new Vector3d();
	private final Vector3d dragDir = new Vector3d();
	private double t0;
	private final Vector3d v0 = new Vector3d();
	private int[] indices = new int[0];
	private List<ModelPart> initial = new ArrayList<>();

	public EditorState.Tool effectiveTool() {
		return EditorState.pivotMode ? EditorState.Tool.MOVE : EditorState.tool;
	}

	// ---------------------------------------------------------------- обновление

	public void update(ModelDocument doc, Viewport vp) {
		visible = doc.hasSelection();
		if (!visible) {
			return;
		}
		ModelPart primary = doc.primary();
		if (doc.selection().size() == 1) {
			Vector3f wp = primary.worldPivot();
			origin.set(wp.x, wp.y, wp.z);
		} else {
			Vector3d sum = new Vector3d();
			for (int i : doc.selection()) {
				Vector3f wp = doc.parts().get(i).worldPivot();
				sum.add(wp.x, wp.y, wp.z);
			}
			origin.set(sum.div(doc.selection().size()));
		}
		if (EditorState.space == EditorState.Space.LOCAL) {
			Matrix3f r = primary.rotationMatrix();
			for (int i = 0; i < 3; i++) {
				Vector3f v = r.transform(i == 0 ? 1 : 0, i == 1 ? 1 : 0, i == 2 ? 1 : 0, new Vector3f());
				axes[i].set(v.x, v.y, v.z).normalize();
			}
		} else {
			axes[0].set(1, 0, 0);
			axes[1].set(0, 1, 0);
			axes[2].set(0, 0, 1);
		}
		size = Math.max(0.05, vp.cam.distance(origin) * 0.16);
	}

	// ---------------------------------------------------------------- геометрия ручек

	private double[] tip(Viewport vp, int axis) {
		return vp.project(origin.x + axes[axis].x * size, origin.y + axes[axis].y * size, origin.z + axes[axis].z * size);
	}

	private List<double[]> ring(Viewport vp, int axis) {
		Vector3d n = axes[axis];
		Vector3d a = axes[(axis + 1) % 3];
		Vector3d b = axes[(axis + 2) % 3];
		double r = size * 0.85;
		List<double[]> pts = new ArrayList<>(RING_SEGMENTS + 1);
		for (int i = 0; i <= RING_SEGMENTS; i++) {
			double ang = 2.0 * Math.PI * i / RING_SEGMENTS;
			double c = Math.cos(ang) * r;
			double s = Math.sin(ang) * r;
			pts.add(vp.project(
				origin.x + a.x * c + b.x * s,
				origin.y + a.y * c + b.y * s,
				origin.z + a.z * c + b.z * s));
		}
		return pts;
	}

	/** @return индекс оси под курсором или -1. */
	public int pick(Viewport vp, double mx, double my) {
		if (!visible) {
			return -1;
		}
		double[] o = vp.project(origin);
		int best = -1;
		double bestDist = PICK_PX;
		EditorState.Tool tool = effectiveTool();
		for (int axis = 0; axis < 3; axis++) {
			double d = Double.MAX_VALUE;
			if (tool == EditorState.Tool.ROTATE) {
				List<double[]> pts = ring(vp, axis);
				for (int i = 0; i + 1 < pts.size(); i++) {
					double[] p = pts.get(i);
					double[] q = pts.get(i + 1);
					if (p != null && q != null) {
						d = Math.min(d, DrawUtil.pointSegmentDist(mx, my, p[0], p[1], q[0], q[1]));
					}
				}
			} else {
				double[] t = tip(vp, axis);
				if (o != null && t != null) {
					d = DrawUtil.pointSegmentDist(mx, my, o[0], o[1], t[0], t[1]);
				}
			}
			if (d < bestDist) {
				bestDist = d;
				best = axis;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- рисование

	public void draw(GuiGraphicsExtractor g, Viewport vp, ModelDocument doc) {
		if (!visible) {
			return;
		}
		EditorState.Tool tool = effectiveTool();
		double[] o = vp.project(origin);
		for (int axis = 0; axis < 3; axis++) {
			boolean hot = axis == hover || axis == active;
			int color = hot ? HOT : (EditorState.pivotMode ? PIVOT_COLOR : AXIS_COLORS[axis]);
			int thick = hot ? 2 : 1;
			if (tool == EditorState.Tool.ROTATE) {
				List<double[]> pts = ring(vp, axis);
				for (int i = 0; i + 1 < pts.size(); i++) {
					DrawUtil.line(g, pts.get(i), pts.get(i + 1), color, thick, vp.width, vp.height);
				}
			} else {
				double[] t = tip(vp, axis);
				if (o == null || t == null) {
					continue;
				}
				DrawUtil.line(g, o, t, color, thick, vp.width, vp.height);
				DrawUtil.box(g, t[0], t[1], tool == EditorState.Tool.SCALE ? 3 : 2, color);
			}
		}
		if (o != null) {
			DrawUtil.box(g, o[0], o[1], 1, 0xFFFFFFFF);
		}
	}

	// ---------------------------------------------------------------- перетаскивание

	public boolean begin(int axis, Viewport vp, double mx, double my, ModelDocument doc) {
		if (!visible || axis < 0) {
			return false;
		}
		dragTool = effectiveTool();
		dragPivot = EditorState.pivotMode;
		dragAxis = axis;
		dragOrigin.set(origin);
		dragDir.set(axes[axis]);
		indices = doc.selectedIndices();
		initial = new ArrayList<>();
		for (int i : indices) {
			initial.add(doc.parts().get(i).copy());
		}
		Vector3d ro = vp.cam;
		Vector3d rd = vp.rayDir(mx, my);
		if (dragTool == EditorState.Tool.ROTATE) {
			Vector3d hit = rayPlane(ro, rd, dragOrigin, dragDir);
			if (hit == null) {
				return false;
			}
			v0.set(hit).sub(dragOrigin).normalize();
		} else {
			t0 = lineParam(ro, rd, dragOrigin, dragDir, 0.0);
		}
		active = axis;
		changed = false;
		return true;
	}

	public void drag(Viewport vp, double mx, double my, ModelDocument doc) {
		if (active < 0) {
			return;
		}
		boolean fine = EditorState.mode == EditorState.Mode.FINE;
		Vector3d ro = vp.cam;
		Vector3d rd = vp.rayDir(mx, my);
		List<ModelPart> parts = doc.parts();

		switch (dragTool) {
			case MOVE -> {
				double d = snap(lineParam(ro, rd, dragOrigin, dragDir, t0) - t0, fine ? MOVE_STEP_FINE : MOVE_STEP_NORMAL);
				Vector3f delta = new Vector3f((float) (dragDir.x * d), (float) (dragDir.y * d), (float) (dragDir.z * d));
				if (dragPivot) {
					ModelPart part = parts.get(indices[0]);
					part.set(initial.get(0));
					part.movePivotToWorld(initial.get(0).worldPivot().add(delta));
					part.roundAll();
				} else {
					for (int k = 0; k < indices.length; k++) {
						ModelPart part = parts.get(indices[k]);
						part.pos.set(initial.get(k).pos).add(delta);
						part.roundAll();
					}
				}
			}
			case SCALE -> {
				double d = snap(lineParam(ro, rd, dragOrigin, dragDir, t0) - t0, fine ? MOVE_STEP_FINE : MOVE_STEP_NORMAL);
				for (int k = 0; k < indices.length; k++) {
					ModelPart part = parts.get(indices[k]);
					part.scale.set(initial.get(k).scale);
					float cur = ModelDocument.get(part.scale, dragAxis);
					ModelDocument.set(part.scale, dragAxis, (float) Math.max(0.01, cur + d));
					part.roundAll();
				}
			}
			case ROTATE -> {
				Vector3d hit = rayPlane(ro, rd, dragOrigin, dragDir);
				if (hit == null) {
					return;
				}
				Vector3d v1 = hit.sub(dragOrigin).normalize();
				double angle = Math.atan2(dragDir.dot(new Vector3d(v0).cross(v1)), v0.dot(v1));
				double step = Math.toRadians(fine ? ROT_STEP_FINE : ROT_STEP_NORMAL);
				angle = snap(angle, step);
				Matrix3f dm = new Matrix3f().rotation((float) angle, (float) dragDir.x, (float) dragDir.y, (float) dragDir.z);
				for (int k = 0; k < indices.length; k++) {
					ModelPart part = parts.get(indices[k]);
					ModelPart i0 = initial.get(k);
					Matrix3f nr = new Matrix3f(dm).mul(i0.rotationMatrix());
					Vector3f e = nr.getEulerAnglesZYX(new Vector3f());
					part.rot.set((float) Math.toDegrees(e.x), (float) Math.toDegrees(e.y), (float) Math.toDegrees(e.z));
					// орбита опорной точки вокруг центра гизмо
					Vector3f wp = i0.worldPivot().sub((float) dragOrigin.x, (float) dragOrigin.y, (float) dragOrigin.z);
					dm.transform(wp);
					wp.add((float) dragOrigin.x, (float) dragOrigin.y, (float) dragOrigin.z);
					part.pos.set(wp).sub(i0.pivot);
					part.roundAll();
				}
			}
		}
		changed = true;
	}

	public void end() {
		active = -1;
		initial = new ArrayList<>();
		indices = new int[0];
	}

	// ---------------------------------------------------------------- математика

	private static double snap(double v, double step) {
		return Math.round(v / step) * step;
	}

	/** Параметр t ближайшей к лучу точки прямой P(t) = lo + t*u. fallback — если прямые параллельны. */
	static double lineParam(Vector3d ro, Vector3d rd, Vector3d lo, Vector3d u, double fallback) {
		Vector3d w = new Vector3d(lo).sub(ro);
		double b = u.dot(rd);
		double dd = u.dot(w);
		double e = rd.dot(w);
		double denom = 1.0 - b * b;
		if (denom < 1e-6) {
			return fallback;
		}
		return (b * e - dd) / denom;
	}

	/** Пересечение луча с плоскостью (точка planeO, нормаль n); null — если параллельны/позади. */
	static Vector3d rayPlane(Vector3d ro, Vector3d rd, Vector3d planeO, Vector3d n) {
		double den = rd.dot(n);
		if (Math.abs(den) < 1e-6) {
			return null;
		}
		double s = new Vector3d(planeO).sub(ro).dot(n) / den;
		if (s < 0.0) {
			return null;
		}
		return new Vector3d(ro).fma(s, rd);
	}
}
