package com.example.modelspace.block;

import com.example.modelspace.ModBlockEntities;
import com.example.modelspace.model.ModelPart;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Хранит модель (список частей) внутри блока Workspace. */
public class WorkspaceBlockEntity extends BlockEntity {
	private List<ModelPart> parts = new ArrayList<>();

	public WorkspaceBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.WORKSPACE, pos, state);
	}

	public List<ModelPart> parts() {
		return parts;
	}

	public void setParts(List<ModelPart> newParts) {
		List<ModelPart> copy = new ArrayList<>(newParts.size());
		for (ModelPart p : newParts) {
			copy.add(p.copy());
		}
		this.parts = copy;
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("parts", ModelPart.LIST_CODEC, parts);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.parts = new ArrayList<>(input.read("parts", ModelPart.LIST_CODEC).orElse(List.of()));
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}
}
