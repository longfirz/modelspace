package com.example.modelspace;

import com.example.modelspace.block.WorkspaceBlock;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
	public static final Block WORKSPACE = register(
		"workspace",
		WorkspaceBlock::new,
		BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL));

	private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties props) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, ModelSpace.id(name));
		T block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(props.setId(blockKey)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, ModelSpace.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	public static void init() {}

	private ModBlocks() {}
}
