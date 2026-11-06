package com.example.modelspace.block;

import com.example.modelspace.ModItems;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Блок Workspace. ПКМ с Workspace Key в руке запускает сессию редактирования.
 * Сама сессия (freecam, панель) живёт на клиенте; клиентский код регистрирует
 * свой обработчик в {@link #clientStartHook}, чтобы общий код не зависел от клиентских классов.
 */
public class WorkspaceBlock extends Block implements EntityBlock {
	/** Ставится из клиентской инициализации. На выделенном сервере остаётся пустым. */
	public static volatile Consumer<BlockPos> clientStartHook = pos -> {};

	public WorkspaceBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(ModItems.WORKSPACE_KEY)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			clientStartHook.accept(pos.immutable());
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WorkspaceBlockEntity(pos, state);
	}
}
