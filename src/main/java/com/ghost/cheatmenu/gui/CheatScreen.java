package com.ghost.cheatmenu.gui;

import com.ghost.cheatmenu.Cheats;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/**
 * 轻量菜单：
 *  - 圆角保留（圆弧 insets 在 init 时预计算一次，render 直接用数组）
 *  - 无缓动、无渐变、无多层阴影
 *  - 每帧 fill 次数 ≈ 行数×3
 */
public class CheatScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PAD = 8;
    private static final int ROW_H = 18;
    private static final int RADIUS = 6;

    private static final int C_BG       = 0xEE0E1218;
    private static final int C_HEAD     = 0xFF17202E;
    private static final int C_BORDER   = 0xFF2E4058;
    private static final int C_ACCENT   = 0xFF6FC8FF;
    private static final int C_TXT      = 0xFFE6EEF8;
    private static final int C_TXT_DIM  = 0xFF8795AB;
    private static final int C_ON       = 0xFF4FE08E;
    private static final int C_OFF      = 0xFF39424F;
    private static final int C_SECTION  = 0xFFF2C45E;
    private static final int C_ROWHOT   = 0x336FC8FF;
    private static final int C_ROW      = 0x14FFFFFF;

    private int panelX, panelY, panelH;
    private int viewTop, viewBottom;
    private int scrollRow = 0;
    private int hoveredRow = -1;

    // 预计算的圆角内缩量（半径 RADIUS 条）
    private static final int[] INSET = new int[RADIUS];

    static {
        for (int i = 0; i < RADIUS; i++) {
            double dy = RADIUS - i - 0.5;
            double dx = Math.sqrt(Math.max(0.0, (double) RADIUS * RADIUS - dy * dy));
            INSET[i] = (int) Math.round(RADIUS - dx);
        }
    }

    public CheatScreen() {
        super(new LiteralText("GhostMenu"));
    }

    @Override
    protected void init() {
        panelX = this.width / 2 - PANEL_W / 2;
        panelY = Math.max(8, this.height / 10);
        panelH = this.height - panelY * 2;
        viewTop = panelY + 26;
        viewBottom = panelY + panelH - 8;
    }

    // ---------- 圆角 ----------

    private void roundRect(MatrixStack ms, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(RADIUS, Math.min(w / 2, h / 2));
        fill(ms, x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int ins = (i < INSET.length) ? INSET[i] : 0;
            fill(ms, x + ins, y + i, x + w - ins, y + i + 1, color);
            fill(ms, x + ins, y + h - 1 - i, x + w - ins, y + h - i, color);
        }
    }

    private void roundOutline(MatrixStack ms, int x, int y, int w, int h, int color) {
        int r = Math.min(RADIUS, Math.min(w / 2, h / 2));
        fill(ms, x + r, y, x + w - r, y + 1, color);
        fill(ms, x + r, y + h - 1, x + w - r, y + h, color);
        fill(ms, x, y + r, x + 1, y + h - r, color);
        fill(ms, x + w - 1, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int ins = (i < INSET.length) ? INSET[i] : 0;
            fill(ms, x + ins, y + i, x + ins + 1, y + i + 1, color);
            fill(ms, x + w - 1 - ins, y + i, x + w - ins, y + i + 1, color);
            fill(ms, x + ins, y + h - 1 - i, x + ins + 1, y + h - i, color);
            fill(ms, x + w - 1 - ins, y + h - 1 - i, x + w - ins, y + h - i, color);
        }
    }

    private void txt(MatrixStack ms, String s, int x, int y, int c) {
        this.textRenderer.drawWithShadow(ms, new LiteralText(s), (float) x, (float) y, c);
    }

    private void txtCenter(MatrixStack ms, String s, int cx, int y, int c) {
        int w = this.textRenderer.getWidth(s);
        this.textRenderer.drawWithShadow(ms, new LiteralText(s), (float) (cx - w / 2), (float) y, c);
    }

    // ---------- 布局 ----------

    private static final int SEC_H = 18;

    private int rowY(int i) {
        int base = viewTop - scrollRow * ROW_H;
        if (i < Cheats.TOGGLES.length) return base + i * ROW_H;
        return base + Cheats.TOGGLES.length * ROW_H + SEC_H + (i - Cheats.TOGGLES.length) * ROW_H;
    }

    private int totalRows() {
        return Cheats.TOGGLES.length + Cheats.ACTIONS.length;
    }

    private int maxScroll() {
        int content = Cheats.TOGGLES.length * ROW_H + SEC_H + Cheats.ACTIONS.length * ROW_H;
        int viewH = viewBottom - viewTop;
        int maxByPixel = Math.max(0, (content - viewH + ROW_H - 1) / ROW_H);
        return Math.min(maxByPixel, totalRows());
    }

    // ---------- 渲染 ----------

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float delta) {
        renderBackground(ms);

        roundRect(ms, panelX, panelY, PANEL_W, panelH, C_BG);

        // 行（先画，再被标题/底栏盖住，天然裁剪）
        hoveredRow = -1;
        for (int i = 0; i < totalRows(); i++) {
            int y = rowY(i);
            if (y + ROW_H <= viewTop || y >= viewBottom) continue;

            int rx = panelX + PAD;
            int rw = PANEL_W - PAD * 2;
            boolean hot = mouseX >= rx && mouseX <= rx + rw
                    && mouseY >= Math.max(y, viewTop) && mouseY < Math.min(y + ROW_H, viewBottom);
            if (hot) {
                hoveredRow = i;
                roundRect(ms, rx, y, rw, ROW_H - 2, C_ROWHOT);
            } else {
                roundRect(ms, rx, y, rw, ROW_H - 2, C_ROW);
            }

            if (i < Cheats.TOGGLES.length) {
                Cheats.Toggle t = Cheats.TOGGLES[i];
                txt(ms, t.name, rx + 6, y + 4, t.on ? C_TXT : C_TXT_DIM);
                // 开关：小圆角块 + 状态色
                int swX = rx + rw - 26;
                roundRect(ms, swX, y + 4, 20, 10, t.on ? C_ON : C_OFF);
                // 指示点
                fill(ms, t.on ? swX + 12 : swX + 2, y + 6, t.on ? swX + 18 : swX + 8, y + 12, 0xFFFFFFFF);
            } else {
                Cheats.Action a = Cheats.ACTIONS[i - Cheats.TOGGLES.length];
                txt(ms, "\u00bb " + a.name, rx + 6, y + 4, C_TXT);
            }
        }

        // 分区标题
        int secY = viewTop - scrollRow * ROW_H + Cheats.TOGGLES.length * ROW_H;
        if (secY >= viewTop - SEC_H && secY < viewBottom) {
            txtCenter(ms, "-- 一次性操作 --", panelX + PANEL_W / 2, secY + 4, C_SECTION);
        }

        // 标题栏（盖住上方溢出）
        roundRect(ms, panelX, panelY, PANEL_W, 26 + RADIUS, C_HEAD);
        fill(ms, panelX + 1, panelY + 25, panelX + PANEL_W - 1, panelY + 26, C_ACCENT & 0xFF3FFFFFFF);
        txtCenter(ms, "GHOST MENU", panelX + PANEL_W / 2, panelY + 8, C_ACCENT);
        txt(ms, "v1.1", panelX + PANEL_W - PAD - 18, panelY + 16, C_TXT_DIM);

        // 底栏（盖住下方溢出）
        int footY = panelY + panelH - 18;
        roundRect(ms, panelX, footY - RADIUS, PANEL_W, 18 + RADIUS, C_BG);
        fill(ms, panelX + 1, footY - RADIUS, panelX + PANEL_W - 1, footY - RADIUS + 1, C_ACCENT & 0xFF3FFFFFFF);
        txt(ms, "点行切换 · 滚轮翻页 · " + (scrollRow + 1) + "/" + (maxScroll() + 1),
                panelX + PAD, footY - 9, C_TXT_DIM);

        // 边框
        roundOutline(ms, panelX, panelY, PANEL_W, panelH, C_BORDER);

        super.render(ms, mouseX, mouseY, delta);
    }

    // ---------- 交互 ----------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (hoveredRow >= 0) {
            if (hoveredRow < Cheats.TOGGLES.length) {
                Cheats.Toggle t = Cheats.TOGGLES[hoveredRow];
                t.on = !t.on;
            } else {
                Cheats.ACTIONS[hoveredRow - Cheats.TOGGLES.length].run.run();
            }
            return true;
        }
        // 点面板外关掉
        if (mx < panelX || mx > panelX + PANEL_W || my < panelY || my > panelY + panelH) {
            this.onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        scrollRow -= (int) Math.signum(amount);
        if (scrollRow < 0) scrollRow = 0;
        if (scrollRow > maxScroll()) scrollRow = maxScroll();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}