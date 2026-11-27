package com.ghost.cheatmenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import com.ghost.cheatmenu.gui.CheatScreen;
import com.ghost.cheatmenu.gui.BallRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public class GhostMenu implements ClientModInitializer {

    public static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ghostmenu.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.ghostmenu"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.openScreen(new CheatScreen());
                }
            }
            // 悬浮球点击轮询（手机触摸也走这条链路：触摸会转换为鼠标事件）
            BallRenderer.pollInput(client);
            Cheats.tick(client);
        });

        WorldRenderEvents.AFTER_ENTITIES.register(Cheats::renderWorld);

        HudRenderCallback.EVENT.register((matrices, tickDelta) -> BallRenderer.render(matrices));
    }
}