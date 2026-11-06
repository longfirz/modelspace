package com.example.modelspace.client;

import com.example.modelspace.ModelSpace;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Панель редактора поверх экрана (этап 2: инструменты и режимы, без гизмо). */
public final class ModelSpaceHud {
	private static final int BG = 0xC0101418;
	private static final int ACCENT = 0xFF5AC8FF;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int DIM = 0xFFAAAAAA;
	private static final int PANEL_W = 210;

	private ModelSpaceHud() {}

	public static void register() {
		HudElementRegistry.addLast(ModelSpace.id("panel"), ModelSpaceHud::render);
	}

	private static MutableComponent keyed(KeyMapping key, String langKey) {
		return Component.literal("[").append(key.getTranslatedKeyMessage()).append("] ")
			.append(Component.translatable(langKey));
	}

	private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!FreecamController.isActive()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		// Панель редактора рисует сам EditorScreen
		if (mc.screen instanceof com.example.modelspace.client.editor.EditorScreen) {
			return;
		}
		Font font = mc.font;
		if (font == null) {
			return;
		}

		int x = 8;
		int y = 8;
		int h = 94;
		g.fill(x, y, x + PANEL_W, y + h, BG);
		g.fill(x, y, x + PANEL_W, y + 1, ACCENT);

		int tx = x + 6;
		int ty = y + 6;
		g.text(font, Component.translatable("modelspace.hud.title"), tx, ty, ACCENT, true);

		// Инструменты
		ty += 14;
		EditorState.Tool tool = EditorState.tool;
		g.text(font, keyed(ModelSpaceKeys.toolMove, "modelspace.tool.move"), tx, ty,
			tool == EditorState.Tool.MOVE ? TEXT : DIM, true);
		g.text(font, keyed(ModelSpaceKeys.toolRotate, "modelspace.tool.rotate"), tx + 66, ty,
			tool == EditorState.Tool.ROTATE ? TEXT : DIM, true);
		g.text(font, keyed(ModelSpaceKeys.toolScale, "modelspace.tool.scale"), tx + 134, ty,
			tool == EditorState.Tool.SCALE ? TEXT : DIM, true);

		// Пространство трансформации
		ty += 12;
		MutableComponent spaceLine = keyed(ModelSpaceKeys.space, "modelspace.hud.space").append(": ")
			.append(Component.translatable(EditorState.space == EditorState.Space.GLOBAL
				? "modelspace.space.global" : "modelspace.space.local"));
		g.text(font, spaceLine, tx, ty, TEXT, true);

		// Pivot
		ty += 12;
		MutableComponent pivotLine = keyed(ModelSpaceKeys.pivot, "modelspace.hud.pivot").append(": ")
			.append(Component.translatable(EditorState.pivotMode ? "modelspace.on" : "modelspace.off"));
		g.text(font, pivotLine, tx, ty, EditorState.pivotMode ? ACCENT : DIM, true);

		// Скорость
		ty += 12;
		g.text(font, Component.translatable("modelspace.hud.speed", String.format("%.2f", FreecamController.speedMult())),
			tx, ty, DIM, true);

		// Выход
		ty += 12;
		g.text(font, keyed(ModelSpaceKeys.exit, "modelspace.hud.exit"), tx, ty, DIM, true);
	}
}
