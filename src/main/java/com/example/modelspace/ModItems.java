package com.example.modelspace;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final Item WORKSPACE_KEY = register(
		"workspace_key", Item::new, new Item.Properties().stacksTo(1));

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ModelSpace.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}

	public static void init() {}

	private ModItems() {}
}
