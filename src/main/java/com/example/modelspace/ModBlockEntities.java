package com.example.modelspace;

import com.example.modelspace.block.WorkspaceBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<WorkspaceBlockEntity> WORKSPACE = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		ModelSpace.id("workspace"),
		FabricBlockEntityTypeBuilder.create(WorkspaceBlockEntity::new, ModBlocks.WORKSPACE).build());

	public static void init() {}

	private ModBlockEntities() {}
}
