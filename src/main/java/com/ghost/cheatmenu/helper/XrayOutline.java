package com.ghost.cheatmenu.helper;

import com.ghost.cheatmenu.Cheats;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * X-Ray 辅助线框：在方块被隐藏后，把周围地形的方块顶面轮廓画出来，
 * 让你能看清脚下踩的是什么，不至于掉进虚空。
 *
 * 性能：每 6 tick（约 120ms）重建一次缓存，渲染时只遍历一个几千项的列表。
 * 只在半径 7 格内生成，最多约 2000 条目。
 */
public class XrayOutline {

    private static final int RADIUS = 7;
    private static final int REBUILD_INTERVAL = 6;   // ticks

    private static final List<float[]> EDGES = new ArrayList<>();
    private static int tickCounter = 0;
    private static double lastX = 0, lastY = 0, lastZ = 0;
    private static boolean built = false;

    public static void tick(MinecraftClient mc) {
        if (!Cheats.XRAY.on || mc.player == null || mc.world == null) {
            built = false;
            EDGES.clear();
            return;
        }

        tickCounter++;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        boolean moved = Math.abs(px - lastX) > 1.5 || Math.abs(py - lastY) > 1.5 || Math.abs(pz - lastZ) > 1.5;

        if (!built || moved || tickCounter >= REBUILD_INTERVAL) {
            tickCounter = 0;
            lastX = px; lastY = py; lastZ = pz;
            rebuild(mc, px, py, pz);
            built = true;
        }
    }

    private static void rebuild(MinecraftClient mc, double px, double py, double pz) {
        EDGES.clear();
        BlockPos origin = new BlockPos(px, py, pz);
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);

                    BlockState st;
                    try {
                        st = mc.world.getBlockState(pos);
                    } catch (Throwable t) {
                        continue;
                    }
                    if (st == null || st.isAir()) continue;

                    Block b = st.getBlock();
                    // 矿石/箱子等保留原样渲染，不需要额外轮廓
                    if (isKept(b)) continue;

                    // 只给"上方是空气"的方块画顶面（地表轮廓）
                    pos.set(origin.getX() + dx, origin.getY() + dy + 1, origin.getZ() + dz);
                    BlockState above;
                    try {
                        above = mc.world.getBlockState(pos);
                    } catch (Throwable t) {
                        continue;
                    }
                    if (above != null && !above.isAir()) continue;   // 上方有方块 → 不是表面

                    // 顶面 4 条边
                    double x0 = origin.getX() + dx;
                    double y0 = origin.getY() + dy + 1.0;
                    double z0 = origin.getZ() + dz;

                    addEdge(x0, y0, z0, x0 + 1, y0, z0);
                    addEdge(x0 + 1, y0, z0, x0 + 1, y0, z0 + 1);
                    addEdge(x0 + 1, y0, z0 + 1, x0, y0, z0 + 1);
                    addEdge(x0, y0, z0 + 1, x0, y0, z0);
                }
            }
        }
    }

    private static boolean isKept(Block b) {
        return b == Blocks.DIAMOND_ORE || b == Blocks.IRON_ORE || b == Blocks.GOLD_ORE
                || b == Blocks.COAL_ORE || b == Blocks.REDSTONE_ORE || b == Blocks.EMERALD_ORE
                || b == Blocks.LAPIS_ORE || b == Blocks.NETHER_GOLD_ORE || b == Blocks.ANCIENT_DEBRIS
                || b == Blocks.CHEST || b == Blocks.ENDER_CHEST || b == Blocks.FURNACE
                || b == Blocks.SPAWNER || b == Blocks.WATER || b == Blocks.LAVA;
    }

    private static void addEdge(double x1, double y1, double z1, double x2, double y2, double z2) {
        EDGES.add(new float[]{(float) x1, (float) y1, (float) z1, (float) x2, (float) y2, (float) z2});
    }

    /** 由 GhostMenu 的 WorldRenderEvents 调用 */
    public static void render(MatrixStack ms, VertexConsumerProvider consumers, Vec3d cam) {
        if (!Cheats.XRAY.on || EDGES.isEmpty() || consumers == null || ms == null) return;

        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLines());
        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);

        MatrixStack.Entry entry = ms.peek();
        float r = 0.42f, g = 0.95f, b = 1.0f, a = 0.35f;   // 淡青，不刺眼

        for (float[] e : EDGES) {
            vc.vertex(entry.getModel(), e[0], e[1], e[2]).color(r, g, b, a).next();
            vc.vertex(entry.getModel(), e[3], e[4], e[5]).color(r, g, b, a).next();
        }

        ms.pop();
        ((VertexConsumerProvider.Immediate) consumers).draw(RenderLayer.getLines());
    }
}