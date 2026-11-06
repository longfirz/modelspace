package com.example.modelspace.client.render;

import com.example.modelspace.block.WorkspaceBlockEntity;
import com.example.modelspace.client.EditorState;
import com.example.modelspace.model.ModelPart;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import net.minecraft.world.level.block.entity.BoundingBoxRenderable;

/** Рисует части модели Workspace как блоки с произвольной трансформацией. */
public class WorkspaceRenderer implements BlockEntityRenderer<WorkspaceBlockEntity, WorkspaceRenderState> {
	private static final BlockDisplayContext DISPLAY_CONTEXT = BlockDisplayContext.create();

	private final BlockModelResolver blockModelResolver;

	public WorkspaceRenderer(BlockEntityRendererProvider.Context context) {
		this.blockModelResolver = context.blockModelResolver();
	}

	@Override
	public WorkspaceRenderState createRenderState() {
		return new WorkspaceRenderState();
	}

    @Override
    public void extractRenderState(WorkspaceBlockEntity be, WorkspaceRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, state, crumbling);

        // ====== ДОБАВЬ ЭТУ СТРОКУ СЮДА ======
		// Рамка отсечения: 48 блоков в каждую сторону от позиции блока
		// Рамка отсечения: 48 блоков в каждую сторону от позиции блока
		BlockPos pos = be.getBlockPos();
		state.box = BoundingBoxRenderable.RenderableBox.fromCorners(
				pos.getX() - 48, pos.getY() - 48, pos.getZ() - 48,
				pos.getX() + 48, pos.getY() + 48, pos.getZ() + 48);
		state.mode = BoundingBoxRenderable.Mode.BOX;
		state.isVisible = true;
        // =======================================

        // Сам блок Workspace непрозрачный, поэтому свет в его клетке нулевой — берём свет сверху.
        Level level = be.getLevel();
        if (level != null) {
            state.lightCoords = LightCoordsUtil.getLightCoords(level, be.getBlockPos().above());
        }

        int n = 0;
        // Во время сессии показываем рабочую копию редактора, а не сохранённые данные
        for (ModelPart p : EditorState.partsFor(be.getBlockPos(), be.parts())) {
			if (!p.visible) {
				continue;
			}
			if (n == state.models.size()) {
				state.models.add(new BlockModelRenderState());
				state.matrices.add(new Matrix4f());
			}
			this.blockModelResolver.update(state.models.get(n), p.state, DISPLAY_CONTEXT);
			state.matrices.get(n).set(p.matrix());
			n++;
		}
		state.count = n;
	}

	@Override
	public void submit(WorkspaceRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		for (int i = 0; i < state.count; i++) {
			poseStack.pushPose();
			// Pose#mulPose(Matrix4fc) не менялся между версиями (PoseStack#mulPose(Quaternion) в 26.3 стал rotate)
			poseStack.last().mulPose(state.matrices.get(i));
			state.models.get(i).submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}


}
