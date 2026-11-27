package com.ghost.cheatmenu.mixin;

import com.ghost.cheatmenu.Cheats;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ChatTriggerMixin {

    /**
     * 拦截 Screen.sendMessage(String, boolean)。
     * 注意：不能在这里直接 openScreen —— ChatScreen 按回车后，
     * 上层还会继续执行 openScreen(null) 关闭聊天框，会把新界面顶掉。
     * 所以这里只打一个标记，由 tick 在下一帧（此时聊天框已关）再打开。
     */
    @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void ghostmenu_onSendMessage(String text, boolean addToHistory, CallbackInfo ci) {
        if (text == null) return;
        String t = text.trim();
        if (t.startsWith("\u56cd") || t.startsWith("\u56cd")) {
            Cheats.requestOpenMenu();
            ci.cancel();
        }
    }
}