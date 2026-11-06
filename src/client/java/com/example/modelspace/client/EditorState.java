package com.example.modelspace.client;

import com.example.modelspace.client.editor.ModelDocument;
import com.example.modelspace.model.ModelPart;
import java.util.List;
import net.minecraft.core.BlockPos;

/** Состояние редактора. */
public final class EditorState {
	public enum Tool { MOVE, ROTATE, SCALE }
	public enum Space { GLOBAL, LOCAL }
	/** NORMAL — ступенчатые шаги (15°, 1/16 блока), FINE — мелкие (1°, 0.02). */
	public enum Mode { NORMAL, FINE }

	public static Tool tool = Tool.MOVE;
	public static Space space = Space.GLOBAL;
	public static Mode mode = Mode.NORMAL;
	public static boolean pivotMode = false;
	/** Блок Workspace, с которого начата сессия. */
	public static BlockPos anchor = BlockPos.ZERO;
	/** Рабочая копия модели; null, если сессии нет. */
	public static ModelDocument doc;

	public static void reset() {
		tool = Tool.MOVE;
		space = Space.GLOBAL;
		mode = Mode.NORMAL;
		pivotMode = false;
	}

	public static void toggleSpace() {
		space = space == Space.GLOBAL ? Space.LOCAL : Space.GLOBAL;
	}

	public static void togglePivot() {
		pivotMode = !pivotMode;
	}

	public static void toggleMode() {
		mode = mode == Mode.NORMAL ? Mode.FINE : Mode.NORMAL;
	}

	/** Что рисовать у блока pos: во время сессии — рабочую копию, иначе сохранённые данные. */
	public static List<ModelPart> partsFor(BlockPos pos, List<ModelPart> stored) {
		ModelDocument d = doc;
		return (d != null && pos.equals(anchor)) ? d.parts() : stored;
	}

	private EditorState() {}
}
