package com.example.modelspace;

import com.example.modelspace.net.ModNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;

public class ModelSpace implements ModInitializer {
	public static final String MOD_ID = "modelspace";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// Порядок важен: блок использует предмет-ключ при взаимодействии
		ModItems.init();
		ModBlocks.init();
		ModBlockEntities.init();
		ModNetworking.register();

		// Меню креатива
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
			.register(tab -> tab.accept(ModBlocks.WORKSPACE.asItem()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(tab -> tab.accept(ModItems.WORKSPACE_KEY));
	}
}
