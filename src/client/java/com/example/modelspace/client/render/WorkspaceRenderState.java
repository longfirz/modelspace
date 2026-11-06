package com.example.modelspace.client.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityWithBoundingBoxRenderState;
import org.joml.Matrix4f;

/**
 * Снимок данных для отрисовки. С 26.1 блоки рисуются через {@link BlockModelRenderState}
 * (его заполняет BlockModelResolver), а не через SubmitNodeCollector#submitBlock.
 */
public class WorkspaceRenderState extends BlockEntityWithBoundingBoxRenderState {
	/** Пул состояний: переиспользуем объекты между кадрами, чтобы не плодить мусор. */
	public final List<BlockModelRenderState> models = new ArrayList<>();
	public final List<Matrix4f> matrices = new ArrayList<>();
	/** Сколько первых элементов {@link #models}/{@link #matrices} актуально в этом кадре. */
	public int count;
}
