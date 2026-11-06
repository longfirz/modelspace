package com.example.modelspace.client;

import com.example.modelspace.ModBlockEntities;
import com.example.modelspace.block.WorkspaceBlock;
import com.example.modelspace.client.render.WorkspaceRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public class ModelSpaceClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModelSpaceKeys.register();
		ModelSpaceHud.register();

		WorkspaceBlock.clientStartHook = pos -> EditorSession.begin(Minecraft.getInstance(), pos);

		BlockEntityRenderers.register(ModBlockEntities.WORKSPACE, WorkspaceRenderer::new);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ModelSpaceKeys.tick(client);
			FreecamController.tick(client);
		});
	}
}
