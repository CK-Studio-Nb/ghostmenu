package com.ghost.cheatmenu.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 解锁多人联机按钮。
 *
 * 1.16.5 里 TitleScreen 会根据 MinecraftClient.isMultiplayerEnabled() 决定
 * "多人游戏"按钮是否可用（离线账号/未登录时为 false → 按钮灰色点不动）。
 *
 * 这个注入常开，不需要进菜单开开关 —— 因为要能用多人联机，得先在主界面
 * 点得到那个按钮，那时候菜单还开不了。
 */
@Mixin(MinecraftClient.class)
public class MultiplayerUnlockMixin {

    @Inject(method = "isMultiplayerEnabled()Z", at = @At("HEAD"), cancellable = true)
    private void ghostmenu_forceMultiplayer(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}