package com.ghost.cheatmenu.helper;

import com.ghost.cheatmenu.Cheats;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
 * X-Ray 表面线框 v2
 *
 * v1 缺陷：只画"上方是空气"的方块顶面 → 下层、竖井、洞穴壁全丢失，没法导航。
 * v2：每个方块检查 6 个面，只要该面朝向空气就画出这一面的 4 条边，
 *     形成完整 3D 表面网格（地面 / 墙 / 天花板都在）。
 *
 * 可站立顶面 = 绿色，其它表面 = 青色。
 * 性能：半径 6（13³ = 2197 格），状态一次性缓存到数组，邻居判断走数组不查世界；
 *       每 6 tick 或位移超 1.5 格才重建。
 */
public class XrayOutline {

    private static final int R = 6;
    private static final int SIZE = R * 2 + 1;

    private static final List<float[]> EDGES = new ArrayList<>();
    private static final BlockState[] CACHE = new BlockState[SIZE * SIZE * SIZE];

    private static int tickCounter = 0;
    private static double lastX = 0, lastY = 0, lastZ = 0;
    private static boolean built = false;
    private static int ox, oy, oz;

    private static final float[] COL_WALK = {0.45f, 0.95f, 0.55f, 0.60f};
    private static final float[] COL_WALL = {0.35f, 0.72f, 1.00f, 0.30f};

    public static void tick(MinecraftClient mc) {
        if (!Cheats.XRAY.on || !Cheats.XRAY_OUTLINE.on || mc.player == null || mc.world == null) {
            built = false;
            EDGES.clear();
            return;
        }

        tickCounter++;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        boolean moved = Math.abs(px - lastX) > 1.5
                || Math.abs(py - lastY) > 1.5
                || Math.abs(pz - lastZ) > 1.5;

        if (!built || moved || tickCounter >= 6) {
            tickCounter = 0;
            lastX = px; lastY = py; lastZ = pz;
            rebuild(mc, px, py, pz);
            built = true;
        }
    }

    private static int idx(int dx, int dy, int dz) {
        return ((dx + R) * SIZE + (dy + R)) * SIZE + (dz + R);
    }

    private static BlockState at(int dx, int dy, int dz) {
        if (dx < -R || dx > R || dy < -R || dy > R || dz < -R || dz > R) return null;
        return CACHE[idx(dx, dy, dz)];
    }

    private static boolean airy(BlockState st) {
        return st == null || st.isAir();
    }

    private static void rebuild(MinecraftClient mc, double px, double py, double pz) {
        EDGES.clear();
        BlockPos origin = new BlockPos(px, py, pz);
        ox = origin.getX(); oy = origin.getY(); oz = origin.getZ();

        BlockPos.Mutable p = new BlockPos.Mutable();

        // 1) 把周围方块状态全部缓存下来（这一次查询是有代价的，所以要缓存）
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    p.set(ox + dx, oy + dy, oz + dz);
                    BlockState st = null;
                    try {
                        st = mc.world.getBlockState(p);
                    } catch (Throwable ignored) {
                    }
                    CACHE[idx(dx, dy, dz)] = st;
                }
            }
        }

        // 2) 逐方块逐面生成边线（邻居全部走缓存数组）
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    BlockState st = at(dx, dy, dz);
                    if (airy(st)) continue;

                    Block b = st.getBlock();
                    if (isKept(b)) continue;   // 矿石等保留原样，不画线

                    // ---- 边界淡出：距离越远越透明，到半径处消失 ----
                    // 这样不会出现"网格突然截断"的硬边界
                    float d = (float) Math.sqrt((double) (dx * dx + dz * dz));
                    float fade;
                    if (d <= R - 3f) {
                        fade = 1f;
                    } else if (d >= R) {
                        fade = 0f;
                    } else {
                        fade = (R - d) / 3f;
                    }
                    if (fade <= 0.04f) continue;   // 边缘几乎不可见，干脆不画

                    float[] cWalk = {COL_WALK[0], COL_WALK[1], COL_WALK[2], COL_WALK[3] * fade};
                    float[] cWall = {COL_WALL[0], COL_WALL[1], COL_WALL[2], COL_WALL[3] * fade};

                    double x0 = ox + dx;
                    double y0 = oy + dy;
                    double z0 = oz + dz;

                    // 顶面（+Y）
                    if (airy(at(dx, dy + 1, dz))) {
                        quadY(x0, y0 + 1, z0, cWalk);
                    }
                    // 底面（-Y）
                    if (airy(at(dx, dy - 1, dz))) {
                        quadY(x0, y0, z0, cWall);
                    }
                    // 北面（-Z）
                    if (airy(at(dx, dy, dz - 1))) {
                        quadZ(x0, y0, z0, cWall);
                    }
                    // 南面（+Z）
                    if (airy(at(dx, dy, dz + 1))) {
                        quadZ(x0, y0, z0 + 1, cWall);
                    }
                    // 西面（-X）
                    if (airy(at(dx - 1, dy, dz))) {
                        quadX(x0, y0, z0, cWall);
                    }
                    // 东面（+X）
                    if (airy(at(dx + 1, dy, dz))) {
                        quadX(x0 + 1, y0, z0, cWall);
                    }
                }
            }
        }
    }

    /** 水平面（顶/底）的 4 条边 */
    private static void quadY(double x, double y, double z, float[] c) {
        e(x, y, z,     x + 1, y, z,     c);
        e(x + 1, y, z, x + 1, y, z + 1, c);
        e(x + 1, y, z + 1, x, y, z + 1, c);
        e(x, y, z + 1, x, y, z,         c);
    }

    /** 竖直面（沿 Z 法线）的 4 条边 */
    private static void quadZ(double x, double y, double z, float[] c) {
        e(x, y, z,         x + 1, y, z,     c);
        e(x + 1, y, z,     x + 1, y + 1, z, c);
        e(x + 1, y + 1, z, x, y + 1, z,     c);
        e(x, y + 1, z,     x, y, z,         c);
    }

    /** 竖直面（沿 X 法线）的 4 条边 */
    private static void quadX(double x, double y, double z, float[] c) {
        e(x, y, z,         x, y, z + 1,     c);
        e(x, y, z + 1,     x, y + 1, z + 1, c);
        e(x, y + 1, z + 1, x, y + 1, z,     c);
        e(x, y + 1, z,     x, y, z,         c);
    }

    private static void e(double x1, double y1, double z1,
                          double x2, double y2, double z2, float[] c) {
        EDGES.add(new float[]{
                (float) x1, (float) y1, (float) z1,
                (float) x2, (float) y2, (float) z2,
                c[0], c[1], c[2], c[3]
        });
    }

    private static boolean isKept(Block b) {
        // v2 语义反转：现在"不画线"的是那些被隐藏的石头（用线框代替它们），
        // 而矿石等保留原样的方块不需要线框。
        return !isHidden(b);
    }

    /** 与 XrayMixin 保持完全一致的黑名单 */
    private static boolean isHidden(Block b) {
        return b == Blocks.STONE
                || b == Blocks.COBBLESTONE
                || b == Blocks.DIRT
                || b == Blocks.GRASS_BLOCK
                || b == Blocks.GRAVEL
                || b == Blocks.SAND
                || b == Blocks.SANDSTONE
                || b == Blocks.ANDESITE
                || b == Blocks.DIORITE
                || b == Blocks.GRANITE
                || b == Blocks.NETHERRACK
                || b == Blocks.END_STONE
                || b == Blocks.BASALT
                || b == Blocks.BLACKSTONE
                || b == Blocks.SNOW_BLOCK
                || b == Blocks.CLAY
                || b == Blocks.TERRACOTTA
                || b == Blocks.SOUL_SAND
                || b == Blocks.SOUL_SOIL
                || b == Blocks.MAGMA_BLOCK
                || b == Blocks.BEDROCK
                || b == Blocks.WATER
                || b == Blocks.LAVA
                || b == Blocks.OAK_LEAVES
                || b == Blocks.SPRUCE_LEAVES
                || b == Blocks.BIRCH_LEAVES
                || b == Blocks.JUNGLE_LEAVES
                || b == Blocks.ACACIA_LEAVES
                || b == Blocks.DARK_OAK_LEAVES;
    }

    public static void render(MatrixStack ms, VertexConsumerProvider consumers, Vec3d cam) {
        if (!Cheats.XRAY.on || !Cheats.XRAY_OUTLINE.on || EDGES.isEmpty() || consumers == null || ms == null) return;

        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLines());
        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);

        MatrixStack.Entry entry = ms.peek();
        for (float[] g : EDGES) {
            vc.vertex(entry.getModel(), g[0], g[1], g[2]).color(g[6], g[7], g[8], g[9]).next();
            vc.vertex(entry.getModel(), g[3], g[4], g[5]).color(g[6], g[7], g[8], g[9]).next();
        }

        ms.pop();
        ((VertexConsumerProvider.Immediate) consumers).draw(RenderLayer.getLines());
    }
}