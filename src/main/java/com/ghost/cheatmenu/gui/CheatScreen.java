package com.ghost.cheatmenu.gui;

import com.ghost.cheatmenu.Cheats;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

public class CheatScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int ROW_H = 14;

    private int panelX;
    private int panelY;
    private int panelH;
    private int scroll = 0;

    public CheatScreen() {
        super(new LiteralText("Ghost Menu"));
    }

    @Override
    protected void init() {
        panelX = this.width / 2 - PANEL_W / 2;
        panelY = 16;
        panelH = this.height - 32;
    }

    private int totalHeight() {
        return Cheats.TOGGLES.length * ROW_H + Cheats.ACTIONS.length * ROW_H + 24;
    }

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float delta) {
        renderBackground(ms);
        fill(ms, panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xC0101010);
        fill(ms, panelX, panelY, panelX + PANEL_W, panelY + 16, 0xFF202020);
        drawCenteredString(ms, "§d幽灵菜单 §7v1.0 §8| §7右键Shift开关", this.width / 2, panelY + 4, 0xFFFFFFFF);

        int y = panelY + 20 - scroll;

        for (Cheats.Toggle t : Cheats.TOGGLES) {
            if (y >= panelY + 16 && y <= panelY + panelH - ROW_H) {
                boolean hover = mouseX >= panelX + 4 && mouseX <= panelX + PANEL_W - 4
                        && mouseY >= y && mouseY < y + ROW_H - 2;
                if (hover) fill(ms, panelX + 4, y, panelX + PANEL_W - 4, y + ROW_H - 2, 0x40FFFFFF);
                String label = (t.on ? "§a[ 开 ] " : "§8[ 关 ] ") + "§f" + t.name;
                drawString(ms, label, panelX + 10, y + 3, 0xFFFFFF);
            }
            y += ROW_H;
        }

        y += 6;
        if (y >= panelY + 16 && y <= panelY + panelH) {
            drawString(ms, "§e—— 一次性操作 ——", panelX + 10, y, 0xFFFF55);
        }
        y += 10;

        for (Cheats.Action a : Cheats.ACTIONS) {
            if (y >= panelY + 16 && y <= panelY + panelH - ROW_H) {
                boolean hover = mouseX >= panelX + 4 && mouseX <= panelX + PANEL_W - 4
                        && mouseY >= y && mouseY < y + ROW_H - 2;
                if (hover) fill(ms, panelX + 4, y, panelX + PANEL_W - 4, y + ROW_H - 2, 0x40FFFFFF);
                drawString(ms, "§b» §f" + a.name, panelX + 10, y + 3, 0xFFFFFF);
            }
            y += ROW_H;
        }

        super.render(ms, mouseX, mouseY, delta);
    }

    private int maxScroll() {
        return Math.max(0, totalHeight() - panelH + 20);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx >= panelX && mx <= panelX + PANEL_W && my >= panelY && my <= panelY + panelH) {
            int y = panelY + 20 - scroll;
            for (Cheats.Toggle t : Cheats.TOGGLES) {
                if (mx >= panelX + 4 && mx <= panelX + PANEL_W - 4 && my >= y && my < y + ROW_H - 2) {
                    t.on = !t.on;
                    return true;
                }
                y += ROW_H;
            }
            y += 6 + 10;
            for (Cheats.Action a : Cheats.ACTIONS) {
                if (mx >= panelX + 4 && mx <= panelX + PANEL_W - 4 && my >= y && my < y + ROW_H - 2) {
                    a.run.run();
                    return true;
                }
                y += ROW_H;
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        scroll -= (int) (amount * 20);
        if (scroll < 0) scroll = 0;
        if (scroll > maxScroll()) scroll = maxScroll();
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
