package com.example.modelspace.client;

import com.example.modelspace.block.WorkspaceBlockEntity;
import com.example.modelspace.client.editor.EditorScreen;
import com.example.modelspace.client.editor.ModelDocument;
import com.example.modelspace.model.ModelPart;
import com.example.modelspace.net.SaveModelPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Жизненный цикл сессии редактирования: старт, сохранение, завершение. */
public final class EditorSession {
	private EditorSession() {}

	public static void begin(Minecraft mc, BlockPos pos) {
		if (FreecamController.isActive()) {
			return;
		}
		FreecamController.start(mc, pos);
		if (!FreecamController.isActive()) {
			return;
		}
		List<ModelPart> initial = new ArrayList<>();
		if (mc.level != null && mc.level.getBlockEntity(pos) instanceof WorkspaceBlockEntity be) {
			initial.addAll(be.parts());
		}
		EditorState.doc = new ModelDocument(initial);
        mc.gui.setScreen(new EditorScreen());
	}

	public static void save() {
		ModelDocument d = EditorState.doc;
		Minecraft mc = Minecraft.getInstance();
		if (d == null || mc.level == null) {
			return;
		}
		List<ModelPart> copy = d.copyParts();
		ClientPlayNetworking.send(new SaveModelPayload(EditorState.anchor, copy));
		// Сразу обновляем клиентский блок, чтобы не мигала старая модель до ответа сервера
		if (mc.level.getBlockEntity(EditorState.anchor) instanceof WorkspaceBlockEntity be) {
			be.setParts(copy);
		}
		d.markSaved();
	}

	/** Закрыть сессию (автосохранение, если есть несохранённые правки). */
	public static void end(Minecraft mc) {
		if (EditorState.doc != null && EditorState.doc.isDirty()) {
			save();
		}
		EditorState.doc = null;
		FreecamController.stop(mc);
	}
}
