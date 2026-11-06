package com.example.modelspace.client.editor;

import com.example.modelspace.model.ModelPart;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.block.Mirror;
import org.joml.Vector3f;

/** Редактируемая копия модели: части, выделение, история (undo/redo). */
public final class ModelDocument {
	public record Snapshot(List<ModelPart> parts, List<Integer> selection) {}

	private static final int MAX_HISTORY = 100;
	/** Буфер обмена живёт между сессиями. */
	public static List<ModelPart> clipboard = new ArrayList<>();

	private final List<ModelPart> parts = new ArrayList<>();
	/** Порядок важен: первый элемент — "главная" часть (для Local-осей и числовых полей). */
	private final LinkedHashSet<Integer> selection = new LinkedHashSet<>();
	private final ArrayDeque<Snapshot> undo = new ArrayDeque<>();
	private final ArrayDeque<Snapshot> redo = new ArrayDeque<>();
	private int lastClicked = -1;
	private boolean dirty;

	public ModelDocument(List<ModelPart> initial) {
		for (ModelPart p : initial) {
			parts.add(p.copy());
		}
	}

	// ---------- доступ ----------

	public List<ModelPart> parts() { return parts; }
	public Set<Integer> selection() { return selection; }
	public boolean isSelected(int i) { return selection.contains(i); }
	public boolean hasSelection() { return !selection.isEmpty(); }
	public boolean isDirty() { return dirty; }
	public void markSaved() { dirty = false; }
	public boolean canUndo() { return !undo.isEmpty(); }
	public boolean canRedo() { return !redo.isEmpty(); }

	public ModelPart primary() {
		if (selection.isEmpty()) {
			return null;
		}
		return parts.get(selection.iterator().next());
	}

	public List<ModelPart> copyParts() {
		List<ModelPart> out = new ArrayList<>(parts.size());
		for (ModelPart p : parts) {
			out.add(p.copy());
		}
		return out;
	}

	public int[] selectedIndices() {
		return selection.stream().mapToInt(Integer::intValue).toArray();
	}

	// ---------- история ----------

	public Snapshot snapshot() {
		return new Snapshot(copyParts(), new ArrayList<>(selection));
	}

	/** Вызвать перед дискретным изменением. */
	public void checkpoint() {
		pushUndo(snapshot());
	}

	/** Для перетаскиваний: передать снимок, сделанный в начале жеста. */
	public void commitDrag(Snapshot before) {
		pushUndo(before);
	}

	private void pushUndo(Snapshot s) {
		undo.addLast(s);
		while (undo.size() > MAX_HISTORY) {
			undo.removeFirst();
		}
		redo.clear();
		dirty = true;
	}

	private void restore(Snapshot s) {
		parts.clear();
		for (ModelPart p : s.parts()) {
			parts.add(p.copy());
		}
		selection.clear();
		for (int i : s.selection()) {
			if (i >= 0 && i < parts.size()) {
				selection.add(i);
			}
		}
		dirty = true;
	}

	public void undo() {
		if (undo.isEmpty()) {
			return;
		}
		redo.addLast(snapshot());
		restore(undo.removeLast());
	}

	public void redo() {
		if (redo.isEmpty()) {
			return;
		}
		undo.addLast(snapshot());
		restore(redo.removeLast());
	}

	// ---------- выделение ----------

	public void clearSelection() {
		selection.clear();
	}

	public void selectAll() {
		selection.clear();
		for (int i = 0; i < parts.size(); i++) {
			selection.add(i);
		}
	}

	/** Клик во вьюпорте: hit < 0 — пустое место. */
	public void clickViewport(int hit, boolean additive) {
		if (hit < 0) {
			if (!additive) {
				selection.clear();
			}
			return;
		}
		if (additive) {
			if (!selection.remove(hit)) {
				selection.add(hit);
			}
		} else {
			selection.clear();
			selection.add(hit);
		}
		lastClicked = hit;
	}

	/** Клик в списке: Ctrl — переключить, Shift — диапазон. */
	public void clickList(int i, boolean shift, boolean ctrl) {
		if (i < 0 || i >= parts.size()) {
			return;
		}
		if (shift && lastClicked >= 0 && lastClicked < parts.size()) {
			int a = Math.min(lastClicked, i);
			int b = Math.max(lastClicked, i);
			if (!ctrl) {
				selection.clear();
			}
			for (int k = a; k <= b; k++) {
				selection.add(k);
			}
			selection.remove(i);
			selection.add(i);
		} else if (ctrl) {
			if (!selection.remove(i)) {
				selection.add(i);
			}
			lastClicked = i;
		} else {
			selection.clear();
			selection.add(i);
			lastClicked = i;
		}
	}

	// ---------- операции ----------

	public boolean add(ModelPart part) {
		if (parts.size() >= ModelPart.MAX_PARTS) {
			return false;
		}
		checkpoint();
		parts.add(part);
		selection.clear();
		selection.add(parts.size() - 1);
		lastClicked = parts.size() - 1;
		return true;
	}

	public void deleteSelected() {
		if (selection.isEmpty()) {
			return;
		}
		checkpoint();
		int[] idx = selectedIndices();
		java.util.Arrays.sort(idx);
		for (int k = idx.length - 1; k >= 0; k--) {
			parts.remove(idx[k]);
		}
		selection.clear();
		lastClicked = -1;
	}

	public void duplicateSelected() {
		if (selection.isEmpty() || parts.size() + selection.size() > ModelPart.MAX_PARTS) {
			return;
		}
		checkpoint();
		int[] idx = selectedIndices();
		java.util.Arrays.sort(idx);
		selection.clear();
		for (int i : idx) {
			parts.add(parts.get(i).copy());
			selection.add(parts.size() - 1);
		}
	}

	public void copyToClipboard() {
		if (selection.isEmpty()) {
			return;
		}
		int[] idx = selectedIndices();
		java.util.Arrays.sort(idx);
		List<ModelPart> out = new ArrayList<>();
		for (int i : idx) {
			out.add(parts.get(i).copy());
		}
		clipboard = out;
	}

	public void paste() {
		if (clipboard.isEmpty() || parts.size() + clipboard.size() > ModelPart.MAX_PARTS) {
			return;
		}
		checkpoint();
		selection.clear();
		for (ModelPart p : clipboard) {
			parts.add(p.copy());
			selection.add(parts.size() - 1);
		}
	}

	public void toggleVisible(int i) {
		if (i < 0 || i >= parts.size()) {
			return;
		}
		checkpoint();
		ModelPart p = parts.get(i);
		p.visible = !p.visible;
	}

	/**
	 * Числовое поле. group: 0 pos, 1 rot, 2 scale, 3 pivot; axis 0..2.
	 * pos меняется на разницу для всех выделенных, остальное выставляется как есть.
	 * Смена pivot не меняет внешний вид (pos компенсируется).
	 */
	public void setComponent(int group, int axis, float value) {
		ModelPart main = primary();
		if (main == null || !Float.isFinite(value)) {
			return;
		}
		if (group == 2) {
			value = Math.max(0.01F, value);
		}
		checkpoint();
		float delta = value - get(main.vec(group), axis);
		for (int i : selection) {
			ModelPart p = parts.get(i);
			switch (group) {
				case 0 -> set(p.pos, axis, get(p.pos, axis) + delta);
				case 3 -> {
					Vector3f np = new Vector3f(p.pivot);
					set(np, axis, value);
					p.movePivotToLocal(np);
				}
				default -> set(p.vec(group), axis, value);
			}
			p.roundAll();
		}
	}

	/** Шаговое изменение колесом над полем. */
	public void nudgeComponent(int group, int axis, float delta) {
		ModelPart main = primary();
		if (main == null) {
			return;
		}
		setComponent(group, axis, get(main.vec(group), axis) + delta);
	}

	public static float get(Vector3f v, int axis) {
		return axis == 0 ? v.x : axis == 1 ? v.y : v.z;
	}

	public static void set(Vector3f v, int axis, float f) {
		if (axis == 0) {
			v.x = f;
		} else if (axis == 1) {
			v.y = f;
		} else {
			v.z = f;
		}
	}

	// ---------- зеркало ----------

	/** Центр выделения (по центрам кубов частей). */
	public Vector3f selectionCenter() {
		Vector3f min = new Vector3f(Float.MAX_VALUE);
		Vector3f max = new Vector3f(-Float.MAX_VALUE);
		for (int i : selection) {
			Vector3f c = parts.get(i).worldCenter();
			min.min(c);
			max.max(c);
		}
		return min.add(max).mul(0.5F);
	}

	/** Отзеркалить выделенное "на месте" относительно центра выделения. */
	public void mirrorInPlace(int axis) {
		if (selection.isEmpty()) {
			return;
		}
		checkpoint();
		float c = get(selectionCenter(), axis);
		for (int i : selection) {
			mirror(parts.get(i), axis, c);
		}
	}

	/** Добавить зеркальные копии выделенного относительно центра Workspace-блока (0.5). */
	public void mirrorCopy(int axis) {
		if (selection.isEmpty() || parts.size() + selection.size() > ModelPart.MAX_PARTS) {
			return;
		}
		checkpoint();
		int[] idx = selectedIndices();
		java.util.Arrays.sort(idx);
		selection.clear();
		for (int i : idx) {
			ModelPart copy = parts.get(i).copy();
			mirror(copy, axis, 0.5F);
			parts.add(copy);
			selection.add(parts.size() - 1);
		}
	}

	/**
	 * Отражение части относительно плоскости, перпендикулярной axis и проходящей через c.
	 * Формула выведена из M(v) = pos + pivot + R*S*(v - pivot):
	 *   pivot'[axis] = 1 - pivot[axis]
	 *   pos' = F(pos + pivot) + 2c*e_axis - pivot'
	 *   углы: X -> (rx,-ry,-rz), Y -> (-rx,ry,-rz), Z -> (-rx,-ry,rz)
	 */
	static void mirror(ModelPart p, int axis, float c) {
		Vector3f wp = new Vector3f(p.pos).add(p.pivot);
		set(wp, axis, -get(wp, axis) + 2 * c);
		set(p.pivot, axis, 1.0F - get(p.pivot, axis));
		p.pos.set(wp).sub(p.pivot);
		switch (axis) {
			case 0 -> { p.rot.y = -p.rot.y; p.rot.z = -p.rot.z; }
			case 1 -> { p.rot.x = -p.rot.x; p.rot.z = -p.rot.z; }
			default -> { p.rot.x = -p.rot.x; p.rot.y = -p.rot.y; }
		}
		if (axis == 0) {
			p.state = p.state.mirror(Mirror.FRONT_BACK);
		} else if (axis == 2) {
			p.state = p.state.mirror(Mirror.LEFT_RIGHT);
		}
		p.roundAll();
	}
}
