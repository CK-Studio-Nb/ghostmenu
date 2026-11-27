package com.ghost.cheatmenu.mixin;

import com.ghost.cheatmenu.Cheats;
import com.ghost.cheatmenu.gui.CheatScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ChatTriggerMixin {

    /**
     * 拦截 Screen.sendMessage(String, boolean)。
     * 在 ChatScreen 里按回车，会走这个方法把文本发往服务端。
     * 只要文本是「囍」，就吞掉不发，转开作弊菜单。
     */
    @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void ghostmenu_onSendMessage(String text, boolean addToHistory, CallbackInfo ci) {
        if (text == null) return;
        String t = text.trim();
        if (t.equals("\u56cd") || t.equalsIgnoreCase("\u56cd")) {
            MinecraftClient.getInstance().openScreen(new CheatScreen());
            ci.cancel();
        }
    }
}