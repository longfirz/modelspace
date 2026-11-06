package com.example.modelspace.client;

import com.example.modelspace.ModelSpace;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class ModelSpaceKeys {
	private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(ModelSpace.id("main"));

	public static KeyMapping exit;
	public static KeyMapping toolMove;
	public static KeyMapping toolRotate;
	public static KeyMapping toolScale;
	public static KeyMapping pivot;
	public static KeyMapping space;
	public static KeyMapping mode;
	public static KeyMapping mirrorX;
	public static KeyMapping mirrorY;
	public static KeyMapping mirrorZ;

	private ModelSpaceKeys() {}

	private static KeyMapping key(String name, int code) {
		return KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.modelspace." + name, InputConstants.Type.KEYBOARD, code, CATEGORY));
	}

	public static void register() {
		exit = key("exit", InputConstants.KEY_BACKSPACE);
		toolMove = key("tool_move", InputConstants.KEY_V);
		toolRotate = key("tool_rotate", InputConstants.KEY_R);
		toolScale = key("tool_scale", InputConstants.KEY_B);
		pivot = key("pivot", InputConstants.KEY_U);
		space = key("space", InputConstants.KEY_G);
		mode = key("mode", InputConstants.KEY_M);
		mirrorX = key("mirror_x", InputConstants.KEY_X);
		mirrorY = key("mirror_y", InputConstants.KEY_Y);
		mirrorZ = key("mirror_z", InputConstants.KEY_Z);
	}

	/** Сбросить накопленные нажатия, чтобы они не сработали в начале сессии. */
	public static void drain() {
		for (KeyMapping k : new KeyMapping[] {exit, toolMove, toolRotate, toolScale, pivot, space, mode, mirrorX, mirrorY, mirrorZ}) {
			if (k != null) {
				while (k.consumeClick()) { /* drain */ }
			}
		}
	}

	public static void tick(Minecraft mc) {
		if (!FreecamController.isActive()) {
			drain();
			return;
		}
		while (toolMove.consumeClick()) EditorState.tool = EditorState.Tool.MOVE;
		while (toolRotate.consumeClick()) EditorState.tool = EditorState.Tool.ROTATE;
		while (toolScale.consumeClick()) EditorState.tool = EditorState.Tool.SCALE;
		// Горячие клавиши во время сессии обрабатывает EditorScreen (пока он открыт, consumeClick не срабатывает).
		while (pivot.consumeClick()) EditorState.togglePivot();
		while (space.consumeClick()) EditorState.toggleSpace();
		while (exit.consumeClick()) {
			EditorSession.end(mc);
		}
	}
}
