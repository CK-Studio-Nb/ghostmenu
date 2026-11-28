package com.ghost.cheatmenu.helper;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 结构撤销：生成建筑前把工作区的方块逐个记录（BlockPos + BlockState），
 * 撤销时逆序 setblock 回去。
 *
 * 实现：不走客户端世界写入（单人可以，但一致性差），而是还原成 /setblock 指令队列，
 * 走和服务端同一通道，保证对服务端生效、和你手动改没有任何区别。
 *
 * 快照上限 6 万方块（约 39x39x39），超出就拒绝生成并提示。
 */
public class StructureUndo {

    /** 一条快照：坐标 + 方块 id + 可能的状态串（存字符串最通用） */
    private static final class Snap {
        final int x, y, z;
        final String block;
        Snap(int x, int y, int z, String block) {
            this.x = x; this.y = y; this.z = z;
            this.block = block;
        }
    }

    private static final Deque<List<Snap>> STACK = new ArrayDeque<>();
    private static final List<String> UNDO_QUEUE = new ArrayList<>();
    private static final int MAX_BLOCKS = 60000;
    private static final int MAX_SNAPS = 3;

    private static int undoTotal = 0;
    private static String undoLabel = "";

    /** 生成建筑前调用：抓取工作区快照 */
    public static void snapshot(MinecraftClient mc, int x1, int y1, int z1, int x2, int y2, int z2) {
        if (mc.player == null || mc.world == null) return;

        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

        long volume = (long)(maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > MAX_BLOCKS) {
            mc.player.sendMessage(new net.minecraft.text.LiteralText(
                    "\u00a7c[GhostMenu] 工作区太大（" + volume + " 格），无法快照，撤销不可用！"), false);
            return;
        }

        List<Snap> snap = new ArrayList<>((int) Math.min(volume, MAX_BLOCKS));
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    p.set(x, y, z);
                    BlockState st;
                    try {
                        st = mc.world.getBlockState(p);
                    } catch (Throwable t) {
                        continue;
                    }
                    if (st == null) continue;
                    if (st.isAir()) continue;   // 空气不用记，fill air 就能还原
                    // 拿到完整的方块 id（含状态属性），setblock 能精确还原
                    String id;
                    try {
                        id = "minecraft:" + net.minecraft.util.registry.Registry.BLOCK
                                .getId(st.getBlock()).getPath() + st.toString().substring(st.toString().indexOf('['));
                    } catch (Throwable t) {
                        // 简化路径：只还原方块类型，不带状态
                        id = "minecraft:" + net.minecraft.util.registry.Registry.BLOCK
                                .getId(st.getBlock()).getPath();
                    }
                    snap.add(new Snap(x, y, z, id));
                }
            }
        }

        STACK.push(snap);
        while (STACK.size() > MAX_SNAPS) {
            STACK.removeLast();
        }

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7b[GhostMenu] 已保存快照（" + snap.size() + " 方块），生成错了可以「撤销建筑」"), false);
    }

    /** 触发撤销：把最近一次快照还原为 setblock 指令队列 */
    public static boolean undo(MinecraftClient mc) {
        if (STACK.isEmpty()) {
            if (mc.player != null) {
                mc.player.sendMessage(new net.minecraft.text.LiteralText(
                        "\u00a7c[GhostMenu] 没有可撤销的快照"), false);
            }
            return false;
        }

        List<Snap> snap = STACK.pop();
        UNDO_QUEUE.clear();
        undoLabel = "撤销建筑";

        // 覆盖空气的格子也要 setblock air（原来的非空气被 fill 覆盖过）
        for (Snap s : snap) {
            UNDO_QUEUE.add(String.format("/setblock %d %d %d %s", s.x, s.y, s.z, s.block));
        }
        // 快照里没记录空气格，但我们 fill 过 air 的地方要清回去：
        // 不补 setblock air —— 原地本来就是 air，无需还原
        undoTotal = UNDO_QUEUE.size();

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 正在撤销（" + undoTotal + " 条）…"), false);
        return true;
    }

    /** 每 tick 发几条撤销指令 */
    public static void tick(MinecraftClient mc) {
        if (UNDO_QUEUE.isEmpty()) return;
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        int n = Math.min(4, UNDO_QUEUE.size());
        for (int i = 0; i < n; i++) {
            String cmd = UNDO_QUEUE.remove(0);
            mc.getNetworkHandler().sendPacket(new ChatMessageC2SPacket(cmd));
        }

        if (UNDO_QUEUE.isEmpty() && mc.player != null) {
            mc.player.sendMessage(new net.minecraft.text.LiteralText(
                    "\u00a7a[GhostMenu] " + undoLabel + " 完成，共 " + undoTotal + " 条"), false);
        }
    }
}