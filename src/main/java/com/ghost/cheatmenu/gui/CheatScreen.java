package com.ghost.cheatmenu.gui;

import com.ghost.cheatmenu.Cheats;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/**
 * GhostMenu 主界面
 * 设计要点：
 *  - 纯 fill 绘制的圆角矩形（无贴图依赖）
 *  - 开屏弹簧缓动（Q弹 pop-in）
 *  - 滑轨式开关（knob 位移带缓动）
 *  - 悬停柔光高亮 + 左侧色条
 *  - 滚动带惯性缓动
 */
public class CheatScreen extends Screen {

    // 布局
    private static final int PANEL_W = 340;
    private static final int PAD = 10;
    private static final int ROW_H = 20;
    private static final int ROW_GAP = 2;
    private static final int RADIUS = 8;

    // 配色（ARGB）
    private static final int C_PANEL_BG      = 0xE6101216;
    private static final int C_PANEL_BORDER  = 0xFF2A3550;
    private static final int C_HEADER_BG     = 0xFF141B2B;
    private static final int C_ACCENT        = 0xFF6FE3FF;
    private static final int C_ACCENT_DIM    = 0xFF2E6C80;
    private static final int C_ROW_BG        = 0x14FFFFFF;
    private static final int C_ROW_HOVER     = 0x2A6FE3FF;
    private static final int C_TEXT          = 0xFFE8F0FF;
    private static final int C_TEXT_DIM      = 0xFF7C8AA5;
    private static final int C_ON            = 0xFF52E39A;
    private static final int C_OFF           = 0xFF3A4256;
    private static final int C_SECTION       = 0xFFFFC861;

    // 动画状态
    private float openT = 0f;          // 0→1 开屏进度
    private float scrollY = 0f;        // 平滑滚动位置
    private float scrollTarget = 0f;

    // 每个开关的 knob 缓动 0..1
    private final float[] knob = new float[Cheats.TOGGLES.length];
    // 每个行的悬停缓动
    private final float[] hoverT = new float[Cheats.TOGGLES.length + Cheats.ACTIONS.length];

    private int panelX, panelY, panelH;
    private int viewTop, viewBottom;

    public CheatScreen() {
        super(new LiteralText("GhostMenu"));
    }

    @Override
    protected void init() {
        panelX = this.width / 2 - PANEL_W / 2;
        panelY = Math.max(10, this.height / 12);
        panelH = this.height - panelY * 2;
        viewTop = panelY + 34;
        viewBottom = panelY + panelH - PAD;
        openT = 0f;
        // knob 初始对齐当前状态，避免开屏突跳
        for (int i = 0; i < Cheats.TOGGLES.length; i++) {
            knob[i] = Cheats.TOGGLES[i].on ? 1f : 0f;
        }
    }

    // ---------------- 动画工具 ----------------

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** 弹簧缓动：越过 1 再回落，产生 Q 弹感 */
    private static float spring(float x) {
        if (x <= 0f) return 0f;
        if (x >= 1f) return 1f;
        return (float) (1 - Math.pow(1 - x, 3) * Math.cos(x * Math.PI * 1.6));
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    // ---------------- 圆角绘制 ----------------

    /**
     * 圆角矩形：用多条水平细条（每条内部再按半径收边）拼出来，
     * 视觉上是抗锯齿圆角，且完全基于 fill，无需贴图。
     */
    private void roundRect(MatrixStack ms, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w / 2, h / 2));
        if (r <= 0) { fill(ms, x, y, x + w, y + h, color); return; }

        // 中间主体（上下各让出圆角高度）
        fill(ms, x, y + r, x + w, y + h - r, color);

        // 上下圆角区：逐行按圆弧收边
        for (int i = 0; i < r; i++) {
            // 距离圆心 dy
            double dy = r - i - 0.5;
            double dx = Math.sqrt(Math.max(0.0, r * r - dy * dy));
            int inset = (int) Math.round(r - dx);
            int lx = x + inset;
            int rx = x + w - inset;
            if (rx <= lx) continue;
            // 上沿
            fill(ms, lx, y + i, rx, y + i + 1, color);
            // 下沿
            fill(ms, lx, y + h - 1 - i, rx, y + h - i, color);
        }
    }

    /** 圆角描边（只画边框，不填充） */
    private void roundRectOutline(MatrixStack ms, int x, int y, int w, int h, int r, int color) {
        // 上/下直边
        fill(ms, x + r, y, x + w - r, y + 1, color);
        fill(ms, x + r, y + h - 1, x + w - r, y + h, color);
        // 左/右直边
        fill(ms, x, y + r, x + 1, y + h - r, color);
        fill(ms, x + w - 1, y + r, x + w, y + h - r, color);
        // 四角弧
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            double dx = Math.sqrt(Math.max(0.0, r * r - dy * dy));
            int inset = (int) Math.round(r - dx);
            // 左上
            fill(ms, x + inset, y + i, x + inset + 1, y + i + 1, color);
            // 右上
            fill(ms, x + w - 1 - inset, y + i, x + w - inset, y + i + 1, color);
            // 左下
            fill(ms, x + inset, y + h - 1 - i, x + inset + 1, y + h - i, color);
            // 右下
            fill(ms, x + w - 1 - inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    /** 竖直渐变填充（用于面板与标题条） */
    private void vGradient(MatrixStack ms, int x, int y, int w, int h, int top, int bottom) {
        for (int i = 0; i < h; i++) {
            float t = h <= 1 ? 0f : (float) i / (h - 1);
            int c = blend(top, bottom, t);
            fill(ms, x, y + i, x + w, y + i + 1, c);
        }
    }

    private static int blend(int c1, int c2, float t) {
        int a = (int) lerp((c1 >>> 24) & 0xFF, (c2 >>> 24) & 0xFF, t);
        int r = (int) lerp((c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF, t);
        int g = (int) lerp((c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF, t);
        int b = (int) lerp(c1 & 0xFF, c2 & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int color, float alpha) {
        int a = (int) (((color >>> 24) & 0xFF) * clamp01(alpha));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    // ---------------- 文本 ----------------

    private void text(MatrixStack ms, String s, int x, int y, int color) {
        this.textRenderer.drawWithShadow(ms, new LiteralText(s), (float) x, (float) y, color);
    }

    private void textNoShadow(MatrixStack ms, String s, int x, int y, int color) {
        this.textRenderer.draw(ms, new LiteralText(s), (float) x, (float) y, color);
    }

    private void textCentered(MatrixStack ms, String s, int cx, int y, int color) {
        int w = this.textRenderer.getWidth(s);
        this.textRenderer.drawWithShadow(ms, new LiteralText(s), (float) (cx - w / 2), (float) y, color);
    }

    // ---------------- 行几何 ----------------

    private int contentHeight() {
        return Cheats.TOGGLES.length * (ROW_H + ROW_GAP)
                + 26
                + Cheats.ACTIONS.length * (ROW_H + ROW_GAP)
                + PAD;
    }

    private float maxScroll() {
        int viewH = viewBottom - viewTop;
        return Math.max(0f, (float) (contentHeight() - viewH));
    }

    private int rowY(int index) {
        int base = viewTop - (int) scrollY;
        if (index < Cheats.TOGGLES.length) {
            return base + index * (ROW_H + ROW_GAP);
        }
        int actionIndex = index - Cheats.TOGGLES.length;
        return base + Cheats.TOGGLES.length * (ROW_H + ROW_GAP) + 26 + actionIndex * (ROW_H + ROW_GAP);
    }

    private boolean rowVisible(int y) {
        return y + ROW_H > viewTop && y < viewBottom;
    }

    private boolean rowHovered(int index, int y, int mouseX, int mouseY) {
        return mouseX >= panelX + PAD && mouseX <= panelX + PANEL_W - PAD
                && mouseY >= Math.max(y, viewTop) && mouseY < Math.min(y + ROW_H, viewBottom)
                && mouseY >= viewTop && mouseY < viewBottom;
    }

    // ---------------- 主渲染 ----------------

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float delta) {
        // 时间步进（用 delta 累加近似）
        float dt = Math.min(0.1f, delta / 20f + 0.0166f);

        // 开屏弹簧
        if (openT < 1f) {
            openT = clamp01(openT + dt * 3.2f);
        }
        float pop = spring(openT);

        // 滚动惯性缓动
        scrollTarget = Math.max(0f, Math.min(scrollTarget, maxScroll()));
        if (Math.abs(scrollY - scrollTarget) < 0.06f) {
            scrollY = scrollTarget;
        } else {
            scrollY = lerp(scrollY, scrollTarget, clamp01(dt * 18f));
        }

        // 开关与悬停缓动
        for (int i = 0; i < Cheats.TOGGLES.length; i++) {
            float target = Cheats.TOGGLES[i].on ? 1f : 0f;
            if (Math.abs(knob[i] - target) < 0.01f) knob[i] = target;
            else knob[i] = lerp(knob[i], target, clamp01(dt * 14f));
        }
        int totalRows = Cheats.TOGGLES.length + Cheats.ACTIONS.length;
        for (int i = 0; i < totalRows; i++) {
            int y = rowY(i);
            boolean hv = rowVisible(y) && rowHovered(i, y, mouseX, mouseY);
            float target = hv ? 1f : 0f;
            if (Math.abs(hoverT[i] - target) < 0.01f) hoverT[i] = target;
            else hoverT[i] = lerp(hoverT[i], target, clamp01(dt * 12f));
        }

        // 背景暗化（随开屏渐入）
        renderBackground(ms);

        // 面板几何（开屏缩放：高度从 60% 弹到 100%）
        int baseH = panelH;
        int animH = (int) (baseH * (0.62f + 0.38f * pop));
        int animY = panelY + (baseH - animH) / 2;
        float fadeIn = clamp01(pop * 1.15f);

        // 面板阴影（几层外扩，制造柔和投影）
        for (int i = 5; i >= 1; i--) {
            int sh = (int) (0x30 * (6 - i) / 5f * fadeIn);
            roundRect(ms, panelX - i, animY - i + 2, PANEL_W + i * 2, animH + i * 2, RADIUS + i,
                    (sh << 24));
        }

        // 面板主体
        roundRect(ms, panelX, animY, PANEL_W, animH, RADIUS, C_PANEL_BG);

        // ---- 先画内容行，再用标题条与底栏盖住上下溢出，天然形成裁剪 ----
        drawRows(ms, mouseX, mouseY);

        // 顶部标题条
        int headerH = 30;
        roundRect(ms, panelX, animY, PANEL_W, headerH + RADIUS, RADIUS, C_HEADER_BG);
        vGradient(ms, panelX + 1, animY + 1, PANEL_W - 2, headerH,
                blend(C_HEADER_BG, C_ACCENT_DIM, 0.28f), C_HEADER_BG);
        // 顶部高光线
        fill(ms, panelX + RADIUS, animY + 1, panelX + PANEL_W - RADIUS, animY + 2,
                withAlpha(C_ACCENT, 0.55f * fadeIn));
        // 标题分隔线
        fill(ms, panelX + 1, animY + headerH, panelX + PANEL_W - 1, animY + headerH + 1,
                withAlpha(C_ACCENT, 0.18f * fadeIn));

        textCentered(ms, "GHOST MENU", panelX + PANEL_W / 2, animY + 9, withAlpha(C_ACCENT, fadeIn));
        textNoShadow(ms, "v1.0", panelX + PANEL_W - PAD - 20, animY + 18, withAlpha(C_TEXT_DIM, fadeIn));

        // 底部底栏：盖住行溢出 + 底部圆角
        int footH = PAD + 6;
        roundRect(ms, panelX, animY + animH - footH - RADIUS, PANEL_W, footH + RADIUS, RADIUS, C_PANEL_BG);
        fill(ms, panelX + 1, animY + animH - footH - RADIUS, panelX + PANEL_W - 1,
                animY + animH - footH - RADIUS + 1, withAlpha(C_ACCENT, 0.12f * fadeIn));
        textNoShadow(ms, "右键Shift 开关   ·   滚轮 翻页",
                panelX + PAD, animY + animH - 14, withAlpha(C_TEXT_DIM, fadeIn));

        // 边框
        roundRectOutline(ms, panelX, animY, PANEL_W, animH, RADIUS, withAlpha(C_PANEL_BORDER, fadeIn));

        // 滚动条
        drawScrollbar(ms, animY, animH, fadeIn);

        super.render(ms, mouseX, mouseY, delta);
    }

    private void drawRows(MatrixStack ms, int mouseX, int mouseY) {
        int totalRows = Cheats.TOGGLES.length + Cheats.ACTIONS.length;

        for (int i = 0; i < totalRows; i++) {
            int y = rowY(i);
            if (!rowVisible(y)) continue;

            int rx = panelX + PAD;
            int rw = PANEL_W - PAD * 2;

            // 行背景
            int bgc = C_ROW_BG;
            float hv = hoverT[i];
            if (hv > 0.01f) bgc = blend(C_ROW_BG, C_ROW_HOVER, hv);
            roundRect(ms, rx, y, rw, ROW_H, 6, bgc);

            // 左侧强调条（悬停时亮起）
            if (hv > 0.01f) {
                roundRect(ms, rx, y + 3, 2, ROW_H - 6, 1, withAlpha(C_ACCENT, hv));
            }

            if (i < Cheats.TOGGLES.length) {
                Cheats.Toggle t = Cheats.TOGGLES[i];
                // 开关底色
                boolean on = t.on;
                int trackColor = on ? withAlpha(C_ON, 0.85f) : C_OFF;
                roundRect(ms, rx + rw - 34, y + 5, 28, 10, 5, trackColor);
                // knob 位置缓动
                int knobX = rx + rw - 34 + (int) (knob[i] * 18);
                int knobR = on ? 6 : 5;
                roundRect(ms, knobX, y + ROW_H / 2 - knobR / 2, knobR, knobR, knobR / 2, 0xFFFFFFFF);

                text(ms, t.name, rx + 8, y + 6, on ? C_TEXT : C_TEXT_DIM);
            } else {
                Cheats.Action a = Cheats.ACTIONS[i - Cheats.TOGGLES.length];
                text(ms, "» " + a.name, rx + 8, y + 6, C_TEXT);
            }
        }

        // 中间分区标题（一次性操作）
        int sepY = viewTop - (int) scrollY
                + Cheats.TOGGLES.length * (ROW_H + ROW_GAP) + 6;
        if (sepY + 14 > viewTop && sepY < viewBottom) {
            int lx = panelX + PAD;
            int rx2 = panelX + PANEL_W - PAD;
            fill(ms, lx, sepY + 7, lx + 24, sepY + 8, withAlpha(C_SECTION, 0.35f));
            fill(ms, rx2 - 24, sepY + 7, rx2, sepY + 8, withAlpha(C_SECTION, 0.35f));
            textCentered(ms, "一 次 性 操 作", panelX + PANEL_W / 2, sepY, C_SECTION);
        }
    }

    private void drawScrollbar(MatrixStack ms, int animY, int animH, float fadeIn) {
        float ms2 = maxScroll();
        if (ms2 <= 0.5f) return;
        int viewH = viewBottom - viewTop;
        int trackX = panelX + PANEL_W - 5;
        int barH = Math.max(16, (int) (viewH * (viewH / (float) contentHeight())));
        int travel = viewH - barH;
        int barY = viewTop + (int) (travel * (scrollY / ms2));
        roundRect(ms, trackX, viewTop, 2, viewH, 1, withAlpha(0xFFFFFF, 0.13f * fadeIn));
        roundRect(ms, trackX, barY, 2, barH, 1, withAlpha(C_ACCENT, 0.75f * fadeIn));
    }

    // ---------------- 交互 ----------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx >= panelX && mx <= panelX + PANEL_W && my >= panelY && my <= panelY + panelH) {
            int totalRows = Cheats.TOGGLES.length + Cheats.ACTIONS.length;
            for (int i = 0; i < totalRows; i++) {
                int y = rowY(i);
                if (my >= Math.max(y, viewTop) && my < Math.min(y + ROW_H, viewBottom)
                        && my >= viewTop && my < viewBottom) {
                    if (i < Cheats.TOGGLES.length) {
                        Cheats.TOGGLES[i].on = !Cheats.TOGGLES[i].on;
                    } else {
                        Cheats.ACTIONS[i - Cheats.TOGGLES.length].run.run();
                    }
                    return true;
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        scrollTarget -= (float) amount * 26f;
        scrollTarget = Math.max(0f, Math.min(scrollTarget, maxScroll()));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}