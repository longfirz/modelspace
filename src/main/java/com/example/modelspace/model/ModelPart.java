package com.example.modelspace.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Одна часть модели: блок + трансформация в координатах Workspace-блока
 * (начало координат — угол блока Workspace, единица — 1 блок).
 *
 * Преобразование точки v (v в [0,1]^3 — локальный куб блока):
 *   M(v) = pos + pivot + R * S * (v - pivot)
 * где R = Rz*Ry*Rx (углы в градусах), S = diag(scale).
 * Значит pos + pivot — это мировая позиция опорной точки (pivot).
 */
public final class ModelPart {
	public static final int MAX_PARTS = 512;
	public static final float MAX_ABS = 256.0F;

	public static final Codec<ModelPart> CODEC = RecordCodecBuilder.create(i -> i.group(
		BlockState.CODEC.fieldOf("state").forGetter(p -> p.state),
		ExtraCodecs.VECTOR3F.optionalFieldOf("pos", new Vector3f()).forGetter(p -> p.pos),
		ExtraCodecs.VECTOR3F.optionalFieldOf("rot", new Vector3f()).forGetter(p -> p.rot),
		ExtraCodecs.VECTOR3F.optionalFieldOf("scale", new Vector3f(1, 1, 1)).forGetter(p -> p.scale),
		ExtraCodecs.VECTOR3F.optionalFieldOf("pivot", new Vector3f(0.5F, 0.5F, 0.5F)).forGetter(p -> p.pivot),
		Codec.BOOL.optionalFieldOf("visible", true).forGetter(p -> p.visible)
	).apply(i, ModelPart::new));

	public static final Codec<List<ModelPart>> LIST_CODEC = CODEC.listOf();

	/**
	 * Компактный сетевой формат (~52 байта на часть вместо ~200 в NBT).
	 * Serverbound-пакет ограничен 32767 байтами: 512 частей в NBT-формате его превышали,
	 * и сервер разрывал соединение. 512 * 52 + заголовок ~ 26.7 КБ — влезает.
	 */
	public static final StreamCodec<ByteBuf, ModelPart> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), p -> p.state,
		ByteBufCodecs.VECTOR3F, p -> p.pos,
		ByteBufCodecs.VECTOR3F, p -> p.rot,
		ByteBufCodecs.VECTOR3F, p -> p.scale,
		ByteBufCodecs.VECTOR3F, p -> p.pivot,
		ByteBufCodecs.BOOL, p -> p.visible,
		ModelPart::new);

	public static final StreamCodec<ByteBuf, List<ModelPart>> LIST_STREAM_CODEC =
		STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PARTS));

	public BlockState state;
	public final Vector3f pos = new Vector3f();
	/** Эйлеровы углы в градусах (X, Y, Z). */
	public final Vector3f rot = new Vector3f();
	public final Vector3f scale = new Vector3f(1, 1, 1);
	/** Опорная точка в локальных координатах блока (по умолчанию центр). */
	public final Vector3f pivot = new Vector3f(0.5F, 0.5F, 0.5F);
	public boolean visible = true;

	public ModelPart(BlockState state) {
		this.state = state;
	}

	public ModelPart(BlockState state, Vector3fc pos, Vector3fc rot, Vector3fc scale, Vector3fc pivot, boolean visible) {
		this.state = state;
		this.pos.set(pos);
		this.rot.set(rot);
		this.scale.set(scale);
		this.pivot.set(pivot);
		this.visible = visible;
	}

	public ModelPart copy() {
		return new ModelPart(state, pos, rot, scale, pivot, visible);
	}

	public void set(ModelPart o) {
		state = o.state;
		pos.set(o.pos);
		rot.set(o.rot);
		scale.set(o.scale);
		pivot.set(o.pivot);
		visible = o.visible;
	}

	/** 0 = pos, 1 = rot, 2 = scale, 3 = pivot. */
	public Vector3f vec(int group) {
		return switch (group) {
			case 0 -> pos;
			case 1 -> rot;
			case 2 -> scale;
			default -> pivot;
		};
	}

	public Matrix3f rotationMatrix() {
		return new Matrix3f().rotateZYX(
			(float) Math.toRadians(rot.z), (float) Math.toRadians(rot.y), (float) Math.toRadians(rot.x));
	}

	/** Матрица "локальный куб блока -> координаты Workspace". */
	public Matrix4f matrix() {
		return new Matrix4f()
			.translate(pos)
			.translate(pivot)
			.rotateZYX((float) Math.toRadians(rot.z), (float) Math.toRadians(rot.y), (float) Math.toRadians(rot.x))
			.scale(scale)
			.translate(-pivot.x, -pivot.y, -pivot.z);
	}

	/** Мировая позиция опорной точки. */
	public Vector3f worldPivot() {
		return new Vector3f(pos).add(pivot);
	}

	/** Центр куба блока в координатах Workspace. */
	public Vector3f worldCenter() {
		return matrix().transformPosition(0.5F, 0.5F, 0.5F, new Vector3f());
	}

	/** Перенести pivot в локальную точку, не меняя внешний вид части. */
	public void movePivotToLocal(Vector3f newPivot) {
		Vector3f world = matrix().transformPosition(newPivot, new Vector3f());
		pivot.set(newPivot);
		pos.set(world).sub(pivot);
	}

	/** Перенести pivot в мировую точку, не меняя внешний вид части. */
	public void movePivotToWorld(Vector3f world) {
		Vector3f local = matrix().invert().transformPosition(world, new Vector3f());
		pivot.set(local);
		pos.set(world).sub(pivot);
	}

	public void roundAll() {
		round(pos);
		round(rot);
		round(scale);
		round(pivot);
	}

	private static void round(Vector3f v) {
		v.set(Math.round(v.x * 10000f) / 10000f, Math.round(v.y * 10000f) / 10000f, Math.round(v.z * 10000f) / 10000f);
	}

	/** Серверная проверка: убрать NaN/Inf и ограничить диапазоны. */
	public void sanitize() {
		clean(pos, -MAX_ABS, MAX_ABS, 0);
		clean(rot, -100000F, 100000F, 0);
		clean(scale, 0.01F, 64.0F, 1);
		clean(pivot, -MAX_ABS, MAX_ABS, 0.5F);
	}

	private static void clean(Vector3f v, float min, float max, float fallback) {
		v.x = cl(v.x, min, max, fallback);
		v.y = cl(v.y, min, max, fallback);
		v.z = cl(v.z, min, max, fallback);
	}

	private static float cl(float f, float min, float max, float fallback) {
		if (!Float.isFinite(f)) {
			return fallback;
		}
		return Math.max(min, Math.min(max, f));
	}
}
