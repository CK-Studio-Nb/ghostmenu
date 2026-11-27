package com.ghost.cheatmenu.gui;

import com.ghost.cheatmenu.Cheats;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/**
 * 手机友好菜单 v1.3
 *  - 手指上下拖拽滚动（mouseDragged）
 *  - 底部 ◀ ▶ 翻页按钮（每按一次滚一屏）
 *  - 保留滚轮（桌面端可用）
 *  - 扁平圆角，无动画
 */
public class CheatScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PAD = 8;
    private static final int ROW_H = 20;
    private static final int RADIUS = 6;
    private static final int HEAD_H = 26;
    private static final int FOOT_H = 30;

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
    private static final int C_DIM_ACCENT = 0x3F6FC8FF;
    private static final int C_BTN      = 0x33FFFFFF;
    private static final int C_BTN_HOT  = 0x556FC8FF;

    private int panelX, panelY, panelH;
    private int viewTop, viewBottom;
    private int scroll = 0;          // 像素滚动
    private int hoveredRow = -1;

    // 拖拽状态
    private boolean dragging = false;
    private double dragStartY = 0;
    private int dragStartScroll = 0;

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
        panelY = Math.max(8, this.height / 12);
        panelH = this.height - panelY * 2;
        viewTop = panelY + HEAD_H;
        viewBottom = panelY + panelH - FOOT_H;
        scroll = 0;
    }

    // ---------- 圆角 ----------

    private void roundRect(MatrixStack ms, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(RADIUS, Math.min(w / 2, h / 2));
        fill(ms, x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int ins = i < INSET.length ? INSET[i] : 0;
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
            int ins = i < INSET.length ? INSET[i] : 0;
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
        int base = viewTop - scroll;
        if (i < Cheats.TOGGLES.length) return base + i * ROW_H;
        return base + Cheats.TOGGLES.length * ROW_H + SEC_H + (i - Cheats.TOGGLES.length) * ROW_H;
    }

    private int totalRows() {
        return Cheats.TOGGLES.length + Cheats.ACTIONS.length;
    }

    private int contentHeight() {
        return Cheats.TOGGLES.length * ROW_H + SEC_H + Cheats.ACTIONS.length * ROW_H;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (viewBottom - viewTop));
    }

    // ---------- 渲染 ----------

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float delta) {
        renderBackground(ms);
        roundRect(ms, panelX, panelY, PANEL_W, panelH, C_BG);

        // 行
        hoveredRow = -1;
        for (int i = 0; i < totalRows(); i++) {
            int y = rowY(i);
            if (y + ROW_H <= viewTop || y >= viewBottom) continue;

            int rx = panelX + PAD;
            int rw = PANEL_W - PAD * 2;
            boolean inside = mouseY >= Math.max(y, viewTop) && mouseY < Math.min(y + ROW_H, viewBottom);
            boolean hot = inside && !dragging && mouseX >= rx && mouseX <= rx + rw;
            if (hot) hoveredRow = i;

            roundRect(ms, rx, y, rw, ROW_H - 2, hot ? C_ROWHOT : C_ROW);

            if (i < Cheats.TOGGLES.length) {
                Cheats.Toggle t = Cheats.TOGGLES[i];
                txt(ms, t.name, rx + 6, y + 5, t.on ? C_TXT : C_TXT_DIM);
                int swX = rx + rw - 26;
                roundRect(ms, swX, y + 5, 20, 10, t.on ? C_ON : C_OFF);
                fill(ms, t.on ? swX + 12 : swX + 2, y + 7, t.on ? swX + 18 : swX + 8, y + 13, 0xFFFFFFFF);
            } else {
                Cheats.Action a = Cheats.ACTIONS[i - Cheats.TOGGLES.length];
                txt(ms, "\u00bb " + a.name, rx + 6, y + 5, C_TXT);
            }
        }

        // 分区标题
        int secY = viewTop - scroll + Cheats.TOGGLES.length * ROW_H;
        if (secY >= viewTop - SEC_H && secY < viewBottom) {
            txtCenter(ms, "-- 一次性操作 --", panelX + PANEL_W / 2, secY + 4, C_SECTION);
        }

        // 标题栏
        roundRect(ms, panelX, panelY, PANEL_W, HEAD_H + RADIUS, C_HEAD);
        fill(ms, panelX + 1, panelY + HEAD_H - 1, panelX + PANEL_W - 1, panelY + HEAD_H, C_DIM_ACCENT);
        txtCenter(ms, "GHOST MENU", panelX + PANEL_W / 2, panelY + 8, C_ACCENT);
        txt(ms, "v1.3", panelX + PANEL_W - PAD - 18, panelY + 16, C_TXT_DIM);

        // 底栏：翻页按钮 + 状态
        int footY = panelY + panelH - FOOT_H;
        roundRect(ms, panelX, footY - RADIUS, PANEL_W, FOOT_H + RADIUS, C_BG);
        fill(ms, panelX + 1, footY - RADIUS, panelX + PANEL_W - 1, footY - RADIUS + 1, C_DIM_ACCENT);

        // 上翻按钮
        int btnW = 44, btnH = 18;
        int upX = panelX + PAD, upY = footY + 6;
        boolean upHot = mouseX >= upX && mouseX <= upX + btnW && mouseY >= upY && mouseY <= upY + btnH;
        roundRect(ms, upX, upY, btnW, btnH, upHot ? C_BTN_HOT : C_BTN);
        txtCenter(ms, "\u25b2 上翻", upX + btnW / 2, upY + 5, C_TXT);

        // 下翻按钮
        int dnX = panelX + PANEL_W - PAD - btnW, dnY = upY;
        boolean dnHot = mouseX >= dnX && mouseX <= dnX + btnW && mouseY >= dnY && mouseY <= dnY + btnH;
        roundRect(ms, dnX, dnY, btnW, btnH, dnHot ? C_BTN_HOT : C_BTN);
        txtCenter(ms, "\u25bc 下翻", dnX + btnW / 2, dnY + 5, C_TXT);

        // 中间提示
        int pct = maxScroll() == 0 ? 100 : (int) (scroll * 100f / maxScroll());
        txtCenter(ms, pct + "%  手指拖动可滚", panelX + PANEL_W / 2, upY + 5, C_TXT_DIM);

        roundOutline(ms, panelX, panelY, PANEL_W, panelH, C_BORDER);

        super.render(ms, mouseX, mouseY, delta);
    }

    // ---------- 交互 ----------

    private boolean inPanel(double mx, double my) {
        return mx >= panelX && mx <= panelX + PANEL_W && my >= panelY && my <= panelY + panelH;
    }

    private boolean inView(double my) {
        return my >= viewTop && my < viewBottom;
    }

    /** 上翻/下翻按钮区域检测 */
    private int buttonAt(double mx, double my) {
        int btnW = 44, btnH = 18;
        int footY = panelY + panelH - FOOT_H;
        int upX = panelX + PAD, upY = footY + 6;
        if (mx >= upX && mx <= upX + btnW && my >= upY && my <= upY + btnH) return -1; // 上翻
        int dnX = panelX + PANEL_W - PAD - btnW;
        if (mx >= dnX && mx <= dnX + btnW && my >= upY && my <= upY + btnH) return 1;   // 下翻
        return 0;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!inPanel(mx, my)) {
            this.onClose();
            return true;
        }

        // 翻页按钮优先
        int btn = buttonAt(mx, my);
        int pageH = viewBottom - viewTop - ROW_H;
        if (btn == -1) {
            scroll = Math.max(0, scroll - pageH);
            return true;
        }
        if (btn == 1) {
            scroll = Math.min(maxScroll(), scroll + pageH);
            return true;
        }

        // 行点击
        if (inView(my) && hoveredRow >= 0) {
            if (hoveredRow < Cheats.TOGGLES.length) {
                Cheats.Toggle t = Cheats.TOGGLES[hoveredRow];
                t.on = !t.on;
            } else {
                Cheats.ACTIONS[hoveredRow - Cheats.TOGGLES.length].run.run();
            }
            return true;
        }

        // 空白区按下：准备拖拽
        if (inView(my)) {
            dragging = true;
            dragStartY = my;
            dragStartScroll = scroll;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            int delta = (int) (dragStartY - my);
            scroll = Math.max(0, Math.min(maxScroll(), dragStartScroll + delta));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (amount * 24)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}