package com.ghost.cheatmenu.mixin;

import com.ghost.cheatmenu.Cheats;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 无尽蓄力：把攻击冷却进度强行固定为 1.0（满蓄力）。
 *
 * 原版机制：每次攻击会把「上次攻击时间」重置，蓄力从 0 涨到 1 需要 0.625 秒左右，
 * 没满时伤害按 progress^2 衰减。杀戮光环每 tick 都挥，所以永远在低蓄力 → 打猫都费劲。
 *
 * getAttackCooldownProgress(float) 是唯一的计算入口，直接让它恒返回 1.0 最干净。
 */
@Mixin(PlayerEntity.class)
public class FullChargeMixin {

    @Inject(method = "getAttackCooldownProgress(F)F", at = @At("HEAD"), cancellable = true)
    private void ghostmenu_fullCharge(float baseTime, CallbackInfoReturnable<Float> cir) {
        if (Cheats.INFINITE_CHARGE != null && Cheats.INFINITE_CHARGE.on) {
            cir.setReturnValue(1.0f);
        }
    }
}