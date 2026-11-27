package com.ghost.cheatmenu.gui;

import com.ghost.cheatmenu.Cheats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/**
 * 悬浮球 v2：扁平化圆角胶囊样式（与菜单同一视觉语言）。
 * 说明：游戏中光标被锁定，球不能直接点（这是 MC 的机制）。
 *       它是状态指示器 + 快捷键提醒。开菜单请用「囍」。
 */
public class BallRenderer {

    private static final int W = 66;   // 胶囊宽
    private static final int H = 20;   // 胶囊高
    private static final int R = 10;   // 圆角半径（= H/2，胶囊形）

    private static final int C_BG     = 0xE60E1620;
    private static final int C_BORDER = 0xFF3D5A80;
    private static final int C_TEXT   = 0xFFB4D8FF;
    private static final int C_DOT_ON = 0xFF4FE08E;

    private static int x = -1, y = -1;

    private static final int[] INSET = new int[R];
    static {
        for (int i = 0; i < R; i++) {
            double dy = R - i - 0.5;
            double dx = Math.sqrt(Math.max(0.0, (double) R * R - dy * dy));
            INSET[i] = (int) Math.round(R - dx);
        }
    }

    public static void render(MatrixStack ms) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.currentScreen instanceof CheatScreen) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        if (x < 0 || y < 0) {
            x = sw - W - 6;
            y = sh / 3;
        }
        x = Math.max(0, Math.min(x, sw - W));
        y = Math.max(0, Math.min(y, sh - H));

        // 胶囊底
        roundRect(ms, x, y, W, H, C_BG);

        // 左边状态点（有任意功能开启时变绿）
        boolean anyOn = false;
        for (Cheats.Toggle t : Cheats.TOGGLES) if (t.on) { anyOn = true; break; }
        int dotColor = anyOn ? C_DOT_ON : 0xFF4A5568;
        roundRect(ms, x + 7, y + H / 2 - 3, 6, 6, dotColor);

        // 文字
        mc.textRenderer.drawWithShadow(ms, new LiteralText("\u56cd 开菜单"),
                (float) (x + 18), (float) (y + 6), C_TEXT);

        // 描边
        roundOutline(ms, x, y, W, H, C_BORDER);
    }

    private static void roundRect(MatrixStack ms, int x, int y, int w, int h, int color) {
        int r = Math.min(R, Math.min(w / 2, h / 2));
        fill(ms, x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int ins = i < INSET.length ? INSET[i] : 0;
            fill(ms, x + ins, y + i, x + w - ins, y + i + 1, color);
            fill(ms, x + ins, y + h - 1 - i, x + w - ins, y + h - i, color);
        }
    }

    private static void roundOutline(MatrixStack ms, int x, int y, int w, int h, int color) {
        int r = Math.min(R, Math.min(w / 2, h / 2));
        fill(ms, x + r, y, x + w - r, y + 1, color);
        fill(ms, x + r, y + h - 1, x + w - r, y + h, color);
        fill(ms, x, y + r, x + 1, y + h - r, color);
        fill(ms, x + w - 1, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int ins = i < INSET.length ? INSET[i] : 0;
            fill(ms, x + ins, y + i, x + ins + 1, y + i + 1, color);
            fill(ms, x + w - 1 - ins, y + i, x + w - ins, y + i + 1, color);
            fill(ms, x + ins, y + h - 1 - i, x + ins + 1, y + h - i, color);
            fill(ms, x + w - 1 - ins, y + h - 1 - i, x + w - ins, y + h - i, color);
        }
    }

    private static void fill(MatrixStack ms, int x1, int y1, int x2, int y2, int color) {
        net.minecraft.client.gui.DrawableHelper.fill(ms, x1, y1, x2, y2, color);
    }

    /** 保留空实现，供 GhostMenu 调用，避免改动入口 */
    public static void pollInput(MinecraftClient mc) {
        // 球不接收点击（游戏内光标被锁），开菜单请用「囍」
    }
}