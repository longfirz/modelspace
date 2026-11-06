package com.example.modelspace.net;

import com.example.modelspace.ModelSpace;
import com.example.modelspace.model.ModelPart;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Клиент -> сервер: сохранить список частей модели в Workspace по координатам pos. */
public record SaveModelPayload(BlockPos pos, List<ModelPart> parts) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SaveModelPayload> TYPE =
		new CustomPacketPayload.Type<>(ModelSpace.id("save_model"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SaveModelPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, SaveModelPayload::pos,
		ModelPart.LIST_STREAM_CODEC, SaveModelPayload::parts,
		SaveModelPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
