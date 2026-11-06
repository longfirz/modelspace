package com.example.modelspace.net;

import com.example.modelspace.ModItems;
import com.example.modelspace.block.WorkspaceBlockEntity;
import com.example.modelspace.model.ModelPart;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ModNetworking {
	private static final double MAX_DIST_SQR = 48.0 * 48.0;

	private ModNetworking() {}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(SaveModelPayload.TYPE, SaveModelPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SaveModelPayload.TYPE, ModNetworking::handleSave);
	}

	private static void handleSave(SaveModelPayload payload, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		Level level = player.level();

		// Никогда не доверяем клиенту: проверяем дистанцию, ключ и размер данных.
		if (payload.parts().size() > ModelPart.MAX_PARTS) {
			return;
		}
		if (player.distanceToSqr(Vec3.atCenterOf(payload.pos())) > MAX_DIST_SQR) {
			return;
		}
		if (!player.getMainHandItem().is(ModItems.WORKSPACE_KEY) && !player.getOffhandItem().is(ModItems.WORKSPACE_KEY)) {
			return;
		}
		if (!level.hasChunkAt(payload.pos())) {
			return;
		}
		if (!(level.getBlockEntity(payload.pos()) instanceof WorkspaceBlockEntity be)) {
			return;
		}

		List<ModelPart> clean = new ArrayList<>(payload.parts().size());
		for (ModelPart p : payload.parts()) {
			ModelPart c = p.copy();
			c.sanitize();
			clean.add(c);
		}
		be.setParts(clean);
	}
}
