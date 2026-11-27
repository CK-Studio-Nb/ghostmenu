package com.ghost.cheatmenu.gui;

import com.ghost.cheatmenu.Cheats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/**
 * 悬浮球：HUD 层常驻，可拖动，点击开菜单。
 * 性能：每帧只画 1 个圆 + 1 个环 + 1 段文字，无缓动计算。
 */
public class BallRenderer {

    private static final int R = 18;          // 球半径
    private static final int TEXTSZIE = 2;    // 描边厚度

    private static int x = -1;
    private static int y = -1;

    private static boolean dragging = false;
    private static int dragOffX = 0;
    private static int dragOffY = 0;
    private static long downTime = 0L;

    public static void render(MatrixStack ms) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;
        if (mc.currentScreen instanceof CheatScreen) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        // 首次定位：右侧中部
        if (x < 0 || y < 0) {
            x = sw - R * 2 - 8;
            y = sh / 3;
        }
        // 屏幕旋转后夹回可视区内
        x = Math.max(0, Math.min(x, sw - R * 2));
        y = Math.max(0, Math.min(y, sh - R * 2));

        // 拖动跟随（用当前鼠标位置）
        if (dragging) {
            double mx = mc.mouse.getX() * sw / (double) mc.getWindow().getWidth();
            double my = mc.mouse.getY() * sh / (double) mc.getWindow().getHeight();
            x = (int) (mx - dragOffX);
            y = (int) (my - dragOffY);
            x = Math.max(0, Math.min(x, sw - R * 2));
            y = Math.max(0, Math.min(y, sh - R * 2));
        }

        int cx = x + R;
        int cy = y + R;

        // 外发光（一圈半透明）
        circle(ms, cx, cy, R + 2, 0x332B6BFF);
        // 球体
        circle(ms, cx, cy, R, 0xCC0D1424);
        // 圆环描边
        ring(ms, cx, cy, R, 0xFF4FA8FF);
        // 中心小点
        circle(ms, cx, cy, 4, 0xFF4FA8FF);

        // 球下方小标签
        String label = "GM";
        int tw = mc.textRenderer.getWidth(label);
        mc.textRenderer.drawWithShadow(ms, new LiteralText(label),
                cx - tw / 2f, cy + R + 2, 0xFF7CC4FF);
    }

    /** 实心圆：逐行 fill，行数 = 直径，最坏 36 次 */
    private static void circle(MatrixStack ms, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(Math.max(0.0, (double) r * r - (double) dy * dy));
            if (dx < 0) continue;
            fillRaw(ms, cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** 圆环：只画边缘两像素 */
    private static void ring(MatrixStack ms, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(Math.max(0.0, (double) r * r - (double) dy * dy));
            // 左右各一个点（2px 厚）
            fillRaw(ms, cx - dx, cy + dy, cx - dx + TEXTSZIE, cy + dy + 1, color);
            fillRaw(ms, cx + dx - TEXTSZIE + 1, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static void fillRaw(MatrixStack ms, int x1, int y1, int x2, int y2, int color) {
        net.minecraft.client.gui.DrawableHelper.fill(ms, x1, y1, x2, y2, color);
    }

    // ================= 触摸/鼠标 轮询 =================

    /**
     * 由 ClientTickEvents 每 tick 调用一次。
     * 1.16.5 鼠标是"点击锁存"模型：wasLeftButtonClicked 只在按下的那一 tick 为 true。
     */
    public static void pollInput(MinecraftClient mc) {
        if (mc.player == null || mc.currentScreen != null) return;
        if (mc.mouse == null) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        if (x < 0) return;

        // 逻辑鼠标坐标缩放
        double mx = mc.mouse.getX() * sw / (double) mc.getWindow().getWidth();
        double my = mc.mouse.getY() * sh / (double) mc.getWindow().getHeight();

        int cx = x + R, cy = y + R;

        if (mc.mouse.wasLeftButtonClicked()) {
            double dx = mx - cx, dy = my - cy;
            if (dx * dx + dy * dy <= (R + 6) * (R + 6)) {
                downTime = System.currentTimeMillis();
                dragOffX = (int) (mx - x);
                dragOffY = (int) (my - y);
                dragging = true;
                return;
            }
        }

        // 拖动中：手指还按着就继续跟随
        if (dragging) {
            if (mc.mouse.wasLeftButtonClicked()) {
                // 仍处于按下状态（每 tick 都会 true），跟随
                x = (int) (mx - dragOffX);
                y = (int) (my - dragOffY);
                x = Math.max(0, Math.min(x, sw - R * 2));
                y = Math.max(0, Math.min(y, sh - R * 2));
            } else {
                // 松手
                dragging = false;
                long held = System.currentTimeMillis() - downTime;
                if (held < 300) {
                    mc.openScreen(new CheatScreen());
                }
            }
        }
    }

    // ================= 触摸/鼠标 =================

    /** 返回 true 表示事件已被球吃掉 */
    public static boolean onMouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null) return false;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        if (x < 0) return false;

        int cx = x + R, cy = y + R;
        double dx = mx - cx, dy = my - cy;
        if (dx * dx + dy * dy <= (R + 4) * (R + 4)) {
            downTime = System.currentTimeMillis();
            dragOffX = (int) (mx - x);
            dragOffY = (int) (my - y);
            dragging = true;
            return true;
        }
        return false;
    }

    public static boolean onMouseReleased(double mx, double my, int button) {
        if (!dragging) return false;
        dragging = false;
        long held = System.currentTimeMillis() - downTime;
        // 短按=开菜单，长按=只算拖拽
        if (held < 250) {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.openScreen(new CheatScreen());
        }
        return true;
    }

    public static boolean onMouseDragged(double mx, double my, int button, double dx, double dy) {
        return dragging;
    }
}