package com.example.modelspace.client.mixin;

import com.example.modelspace.client.FreecamController;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItems.class)
public abstract class HeldItemHideMixin {

    @Inject(
            method = "extractRenderState",
            at = @At("HEAD"),
            cancellable = true
    )
    private void modelspace$hideFirstPersonHands(
            LocalPlayer player,
            float partialTicks,
            FirstPersonHandsAndItemsRenderState state,
            CallbackInfo ci
    ) {
        if (!FreecamController.isActive()) {
            return;
        }

        // Пытаемся найти значение "нет рук", если оно есть в enum.
        // Если его нет — просто обнуляем предметы/высоты ниже.
        try {
            for (FirstPersonHandsAndItemsRenderState.HandRenderSelection s :
                    FirstPersonHandsAndItemsRenderState.HandRenderSelection.values()) {

                String name = s.name();

                if (
                        name.equals("NONE") ||
                                name.equals("NO_HANDS") ||
                                name.equals("NONE_HANDS") ||
                                name.equals("HIDDEN") ||
                                name.equals("INVISIBLE")
                ) {
                    state.handRenderSelection = s;
                    break;
                }
            }
        } catch (Throwable ignored) {
            // Если enum недоступен или структуры нет — продолжаем без этого.
        }

        // Очищаем предметы в руках.
        state.mainHandItem = ItemStack.EMPTY;
        state.offHandItem = ItemStack.EMPTY;

        // Обнуляем анимацию поднятия рук.
        state.mainHandHeight = 0.0F;
        state.offHandHeight = 0.0F;
        state.oldMainHandHeight = 0.0F;
        state.oldOffHandHeight = 0.0F;

        // Очищаем модели рук/предметов.
        state.mainHandRenderState.clear();
        state.offHandRenderState.clear();

        // Выключаем карты в руках.
        state.hasMainHandMapData = false;
        state.hasOffHandMapData = false;

        // Отменяем оригинальный метод, чтобы ваниль не заполнила состояние заново.
        ci.cancel();
    }
}