package com.ghost.cheatmenu.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构生成器 v5
 *
 * 本版按原版 1.16.5 真实机制推演（用户指正后修正）：
 *
 * ── 水的流动距离 ──
 *   水源在水平方向最多流 7 格（第 8 格消失）。
 *   所以"平台半径 ≥ 8"时，从墙边放的水流到中心前就自然断了，中心洞口保持干燥。
 *   → 生成平台取 17×17（半径 8），水源贴墙内缘，水最远到距墙 7 格处停止，
 *     中心 1×1 洞口永远是干的，熔岩块全程有效。
 *   不需要 waterlogged 半砖，也不会有水柱往下灌。
 *
 * ── 摔落伤害 ──
 *   1.16.5: 伤害 = 落差 - 3。僵尸 20 血。
 *   落差 21 → 18 伤害 → 剩 2 血。井深取 21。
 *
 * ── 岩浆块 ──
 *   只对"站在上面"的实体造成伤害；泡在水里不受伤。
 *   因为水在中心前就断流了，井底干燥，岩浆块正常生效。
 *
 * ── 照明抑制刷怪 ──
 *   塔外所有可站立表面插火把（光照 ≥8 不刷怪），只留塔内黑暗。
 *   平台外沿一圈 + 地面一圈 + 屋顶。
 *
 * ── 床 ──
 *   facing = 玩家躺下时"脚朝向头"的方向。head 必须在 facing 方向的下一格。
 *   facing=east → head 在 x+1、foot 在 x。
 */
public class StructureBuilder {

    private static int[] center(MinecraftClient mc) {
        double ex = mc.player.getX();
        double ez = mc.player.getZ();
        Vec3d dir = mc.player.getRotationVec(1.0f);
        Vec3d h = new Vec3d(dir.x, 0, dir.z);
        if (h.lengthSquared() < 1.0E-4) h = new Vec3d(0, 0, -1);
        h = h.normalize().multiply(8);
        return new int[]{
                (int) Math.floor(ex + h.x),
                (int) Math.floor(mc.player.getY()),
                (int) Math.floor(ez + h.z)
        };
    }

    private static final List<String> QUEUE = new ArrayList<>();
    private static final int PER_TICK = 4;
    private static String label = "";
    private static int total = 0;

    public static void tick(MinecraftClient mc) {
        if (QUEUE.isEmpty()) return;
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        int n = Math.min(PER_TICK, QUEUE.size());
        for (int i = 0; i < n; i++) {
            mc.getNetworkHandler().sendPacket(new ChatMessageC2SPacket(QUEUE.remove(0)));
        }
        if (QUEUE.isEmpty() && mc.player != null) {
            mc.player.sendMessage(new net.minecraft.text.LiteralText(
                    "\u00a7a[GhostMenu] " + label + " 生成完毕（" + total + " 条指令）"), false);
        }
    }

    public static boolean busy() { return !QUEUE.isEmpty(); }
    public static void clear() { QUEUE.clear(); label = ""; total = 0; }

    private static void begin(String n) { QUEUE.clear(); label = n; total = 0; }
    private static void add(String c) { QUEUE.add(c); total++; }

    private static void fill(int x1, int y1, int z1, int x2, int y2, int z2, String b) {
        add(String.format("/fill %d %d %d %d %d %d %s", x1, y1, z1, x2, y2, z2, b));
    }
    private static void set(int x, int y, int z, String b) {
        add(String.format("/setblock %d %d %d %s", x, y, z, b));
    }
    private static void summon(String e, int x, int y, int z, String nbt) {
        add(nbt == null || nbt.isEmpty()
                ? String.format("/summon %s %d %d %d", e, x, y, z)
                : String.format("/summon %s %d %d %d %s", e, x, y, z, nbt));
    }

    // =====================================================================
    //  刷怪塔 v5 —— 17×17 平台（水自然断流）+ 21 格干井 + 岩浆块
    //
    //  y 全部相对 cy（玩家脚下）
    //
    //  cy-1        : 25×25 石头地基（整平地面 + 挡住周边刷怪）
    //  cy          : 收集层：箱子
    //  cy+1        : 漏斗
    //  cy+2        : 岩浆块（井底，干燥）
    //  cy+3..cy+23 : 干井 1×1（落差 21 格）
    //  cy+24       : 生成层地板 17×17 石头，中心 1×1 落怪口
    //  cy+25..cy+26: 生成室内腔 17×17×2（全黑）
    //  cy+27       : 石顶
    //  cy+28       : 半砖顶（防塔顶刷怪）
    //
    //  ★ 水：贴 cy+25 层四边墙内侧放源水。
    //    墙内缘在 x/z = ±8，中心洞在 0，距离 8 格 > 7 格上限
    //    → 水流到距墙 7 格（即中心旁 1 格）就断了，中心保持干燥
    //
    //  ★ 火把：地基外沿一圈（每 4 格一支）+ 生成室四角内壁（不，生成室要黑）
    //    只在塔外插，塔内保持黑暗
    // =====================================================================
    public static void mobTower(MinecraftClient mc) {
        if (mc.player == null) return;
        int[] c = center(mc);
        int cx = c[0], cy = c[1], cz = c[2];

        begin("刷怪塔v5");
        StructureUndo.snapshot(mc, cx - 13, cy - 1, cz - 13, cx + 13, cy + 29, cz + 13);

        // ---------- 1. 地基：25×25 石头（整平 + 防周边刷怪） ----------
        fill(cx - 12, cy - 1, cz - 12, cx + 12, cy - 1, cz + 12, "minecraft:stone");

        // ---------- 2. 收集层 ----------
        set(cx, cy, cz, "minecraft:chest");
        set(cx, cy + 1, cz, "minecraft:hopper[facing=down]");

        // ---------- 3. 处死层：岩浆块（井底，干燥） ----------
        set(cx, cy + 2, cz, "minecraft:magma_block");
        // 处死室 5×5 外壳（cy+2..cy+3），内腔 3×3
        fill(cx - 2, cy + 2, cz - 2, cx + 2, cy + 3, cz + 2, "minecraft:stone");
        fill(cx - 1, cy + 2, cz - 1, cx + 1, cy + 3, cz + 1, "minecraft:air");
        set(cx, cy + 2, cz, "minecraft:magma_block");
        // 修脚洞（南墙 cy+3 开 1 格）
        set(cx, cy + 3, cz + 2, "minecraft:air");
        set(cx, cy + 2, cz + 3, "minecraft:stone");   // 站台

        // ---------- 4. 干井 1×1：cy+4 .. cy+23（21 格落差） ----------
        fill(cx, cy + 4, cz, cx, cy + 23, cz, "minecraft:air");
        // 井壁（3×3 包住 1×1，留中心空）
        for (int y = cy + 4; y <= cy + 23; y++) {
            fill(cx - 1, y, cz - 1, cx + 1, y, cz + 1, "minecraft:stone");
            set(cx, y, cz, "minecraft:air");
        }

        // ---------- 5. 生成层地板 17×17（cy+24），中心 1×1 落怪口 ----------
        fill(cx - 8, cy + 24, cz - 8, cx + 8, cy + 24, cz + 8, "minecraft:stone");
        set(cx, cy + 24, cz, "minecraft:air");

        // ---------- 6. 生成室外壳（cy+25..cy+26）+ 内腔 ----------
        fill(cx - 9, cy + 25, cz - 9, cx + 9, cy + 26, cz + 9, "minecraft:stone");
        fill(cx - 8, cy + 25, cz - 8, cx + 8, cy + 26, cz + 8, "minecraft:air");

        // ---------- 7. 顶：cy+27 石头 + cy+28 半砖（防塔顶刷怪） ----------
        fill(cx - 9, cy + 27, cz - 9, cx + 9, cy + 27, cz + 9, "minecraft:stone");
        fill(cx - 8, cy + 28, cz - 8, cx + 8, cy + 28, cz + 8, "minecraft:oak_slab[type=bottom]");

        // ---------- 8. ★水：贴墙内缘放源水，靠 7 格上限自然断流 ----------
        //   墙内缘 = ±8，中心 = 0，距离 8 > 7 → 水到不了中心，洞口保持干燥
        for (int i = -8; i <= 8; i++) {
            set(cx + i, cy + 25, cz - 8, "minecraft:water");
            set(cx + i, cy + 25, cz + 8, "minecraft:water");
            set(cx - 8, cy + 25, cz + i, "minecraft:water");
            set(cx + 8, cy + 25, cz + i, "minecraft:water");
        }

        // ---------- 9. ★火把：塔外所有可站立面插满，防外部刷怪 ----------
        //   地基外沿一圈（每 4 格）+ 四角
        for (int i = -12; i <= 12; i += 4) {
            set(cx + i, cy, cz - 12, "minecraft:torch");
            set(cx + i, cy, cz + 12, "minecraft:torch");
            set(cx - 12, cy, cz + i, "minecraft:torch");
            set(cx + 12, cy, cz + i, "minecraft:torch");
        }
        // 地基上塔身周围（半径 10 一圈，每 3 格）
        for (int i = -10; i <= 10; i += 3) {
            set(cx + i, cy, cz - 10, "minecraft:torch");
            set(cx + i, cy, cz + 10, "minecraft:torch");
            set(cx - 10, cy, cz + i, "minecraft:torch");
            set(cx + 10, cy, cz + i, "minecraft:torch");
        }

        // ---------- 10. 四根支撑柱 ----------
        for (int[] p : new int[][]{{-9, -9}, {9, -9}, {-9, 9}, {9, 9}}) {
            fill(cx + p[0], cy, cz + p[1], cx + p[0], cy + 23, cz + p[1], "minecraft:stone");
        }

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 刷怪塔v5：17×17 平台，水从墙边流 7 格自然断，中心洞口干燥；"
                        + "干井 21 格摔剩 2 血 + 岩浆块烧死；塔外插满火把不刷怪。"), false);
    }

    // =====================================================================
    //  刷铁机 v8 —— 八核水运式（1:1 复刻业主教程，Java 版等价适配）
    //
    //  平面：以玩家锚点为中心 41×41。同心三层：
    //    max 9..18   刷怪带（地板 cy-2，干燥，火把防杂怪）
    //    max 3..8    水道（地板 cy-3，四向含水楼梯供水，水流向中心）
    //    max ≤2      5×5 竖井：空腔 cy-2..cy-13，岩浆池 cy-14，漏斗底 cy-15
    //  井沿 max=3 一圈下半砖：挡水（水不进井）+ 傀儡可直接越过掉井
    //  四向刷怪带中央各一个 3×3 僵尸坑：灵魂沙+水+半砖盖（僵尸永久存活）
    //  八座村民塔（每边左右各一）：柱高 4 + 3×3 台 + 3 床 + 玻璃圈开 2 格 + 半砖封顶
    //  机制：村民白天隔玻璃看见僵尸→夜里睡觉→恐慌→召唤铁傀儡→
    //        傀儡游荡踏上含水楼梯→被水推过水道→越过井沿掉井→岩浆烧死→漏斗收铁进箱
    //  Java 适配：含水方块替代倒水（同效果）、僵尸 PersistenceRequired 防消失、
    //            村民直接 summon 入塔（省引路楼梯）、地下漏斗链+检修井通箱子
    // =====================================================================
    public static void ironFarm(MinecraftClient mc) {
        if (mc.player == null) return;
        int[] c = center(mc);
        int cx = c[0], cy = c[1], cz = c[2];

        begin("刷铁机v8-八核");
        StructureUndo.snapshot(mc, cx - 21, cy - 16, cz - 21, cx + 21, cy + 8, cz + 21);
        String SLAB = "minecraft:smooth_stone_slab[type=bottom]";

        // ---------- 1. 地基与台面 ----------
        fill(cx - 20, cy - 16, cz - 20, cx + 20, cy - 3, cz + 20, "minecraft:stone");
        fill(cx - 20, cy - 2, cz - 20, cx + 20, cy - 2, cz + 20, "minecraft:stone");
        fill(cx - 20, cy - 1, cz - 20, cx + 20, cy - 1, cz + 20, "minecraft:air");
        fill(cx - 20, cy, cz - 20, cx + 20, cy + 9, cz + 20, "minecraft:air");

        // ---------- 2. 水道层挖空（max 3..8 → 站立层 cy-2） ----------
        fill(cx - 8, cy - 2, cz + 3, cx + 8, cy - 2, cz + 8, "minecraft:air");
        fill(cx - 8, cy - 2, cz - 8, cx + 8, cy - 2, cz - 3, "minecraft:air");
        fill(cx + 3, cy - 2, cz - 2, cx + 8, cy - 2, cz + 2, "minecraft:air");
        fill(cx - 8, cy - 2, cz - 2, cx - 3, cy - 2, cz + 2, "minecraft:air");

        // ---------- 3. 竖井 + 岩浆池 + 漏斗底 + 地下收集 ----------
        fill(cx - 2, cy - 2, cz - 2, cx + 2, cy - 13, cz + 2, "minecraft:air");
        fill(cx - 2, cy - 14, cz - 2, cx + 2, cy - 14, cz + 2, "minecraft:lava");
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                set(cx + x, cy - 15, cz + z, "minecraft:hopper[facing=south]");
        for (int x = -1; x <= 1; x++)
            set(cx + x, cy - 15, cz + 3, "minecraft:hopper[facing=south]");
        for (int z = 4; z <= 5; z++)
            for (int x = -1; x <= 1; x++)
                set(cx + x, cy - 15, cz + z, "minecraft:chest");

        // ---------- 4. 井沿下半砖（max=3，挡水放傀儡） ----------
        for (int i = -3; i <= 3; i++) {
            set(cx + i, cy - 3, cz - 3, SLAB);
            set(cx + i, cy - 3, cz + 3, SLAB);
            if (i > -3 && i < 3) {
                set(cx - 3, cy - 3, cz + i, SLAB);
                set(cx + 3, cy - 3, cz + i, SLAB);
            }
        }

        // ---------- 5. 四向含水楼梯（max=8，背朝中心，水向井流） ----------
        for (int i = -8; i <= 8; i++) {
            set(cx + i, cy - 2, cz + 8, "minecraft:stone_brick_stairs[facing=south,half=bottom,waterlogged=true]");
            set(cx + i, cy - 2, cz - 8, "minecraft:stone_brick_stairs[facing=north,half=bottom,waterlogged=true]");
            if (i > -8 && i < 8) {
                set(cx + 8, cy - 2, cz + i, "minecraft:stone_brick_stairs[facing=east,half=bottom,waterlogged=true]");
                set(cx - 8, cy - 2, cz + i, "minecraft:stone_brick_stairs[facing=west,half=bottom,waterlogged=true]");
            }
        }

        // ---------- 6. 四向僵尸坑 + 外墙栅栏 + 刷怪带火把 ----------
        String ZNBT = "{PersistenceRequired:1b,Silent:1b}";
        int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int[] d : dirs) {
            int dx = d[0], dz = d[1];
            if (dx == 0) {
                int z1 = Math.min(16 * dz, 18 * dz), z2 = Math.max(16 * dz, 18 * dz);
                int zo = 18 * dz, zm = 17 * dz;
                fill(cx - 1, cy - 1, cz + z1, cx + 1, cy - 4, cz + z2, "minecraft:air");
                fill(cx - 1, cy - 5, cz + z1, cx + 1, cy - 5, cz + z2, "minecraft:soul_sand");
                fill(cx - 1, cy - 4, cz + z1, cx + 1, cy - 4, cz + z2, "minecraft:water");
                fill(cx - 1, cy - 1, cz + z1, cx + 1, cy - 1, cz + z2, SLAB);
                set(cx - 1, cy, cz + zo, SLAB); set(cx, cy, cz + zo, SLAB); set(cx + 1, cy, cz + zo, SLAB);
                set(cx - 1, cy, cz + zm, SLAB); set(cx + 1, cy, cz + zm, SLAB);
                summon("minecraft:zombie", cx, cy - 4, cz + zm, ZNBT);
            } else {
                int x1 = Math.min(16 * dx, 18 * dx), x2 = Math.max(16 * dx, 18 * dx);
                int xo = 18 * dx, xm = 17 * dx;
                fill(cx + x1, cy - 1, cz - 1, cx + x2, cy - 4, cz + 1, "minecraft:air");
                fill(cx + x1, cy - 5, cz - 1, cx + x2, cy - 5, cz + 1, "minecraft:soul_sand");
                fill(cx + x1, cy - 4, cz - 1, cx + x2, cy - 4, cz + 1, "minecraft:water");
                fill(cx + x1, cy - 1, cz - 1, cx + x2, cy - 1, cz + 1, SLAB);
                set(cx + xo, cy, cz - 1, SLAB); set(cx + xo, cy, cz, SLAB); set(cx + xo, cy, cz + 1, SLAB);
                set(cx + xm, cy, cz - 1, SLAB); set(cx + xm, cy, cz + 1, SLAB);
                summon("minecraft:zombie", cx + xm, cy - 4, cz, ZNBT);
            }
            // 刷怪带火把（防夜间杂怪，铁傀儡生成不受光照影响）
            for (int t = -8; t <= 8; t += 4) {
                if (dx == 0) {
                    set(cx + t, cy - 1, cz + 10 * dz, "minecraft:torch");
                    if (t != 0) set(cx + t, cy - 1, cz + 18 * dz, "minecraft:torch");
                } else {
                    set(cx + 10 * dx, cy - 1, cz + t, "minecraft:torch");
                    if (t != 0) set(cx + 18 * dx, cy - 1, cz + t, "minecraft:torch");
                }
            }
        }
        // 外沿石墙（cy-1）+ 栅栏（cy）
        fill(cx - 19, cy - 1, cz - 19, cx + 19, cy - 1, cz - 19, "minecraft:stone");
        fill(cx - 19, cy - 1, cz + 19, cx + 19, cy - 1, cz + 19, "minecraft:stone");
        fill(cx - 19, cy - 1, cz - 18, cx - 19, cy - 1, cz + 18, "minecraft:stone");
        fill(cx + 19, cy - 1, cz - 18, cx + 19, cy - 1, cz + 18, "minecraft:stone");
        fill(cx - 19, cy, cz - 19, cx + 19, cy, cz - 19, "minecraft:oak_fence");
        fill(cx - 19, cy, cz + 19, cx + 19, cy, cz + 19, "minecraft:oak_fence");
        fill(cx - 19, cy, cz - 18, cx - 19, cy, cz + 18, "minecraft:oak_fence");
        fill(cx + 19, cy, cz - 18, cx + 19, cy, cz + 18, "minecraft:oak_fence");

        // ---------- 7. 八座村民塔（每边左右各一） ----------
        for (int[] d : dirs) {
            int dx = d[0], dz = d[1];
            String face; // 床头朝向（朝农场中心 / 僵尸坑）
            if (dx == 0) face = (dz > 0) ? "north" : "south"; else face = (dx > 0) ? "west" : "east";
            for (int s = -1; s <= 1; s += 2) {
                int tx, tz;
                if (dx == 0) { tx = cx + 4 * s; tz = cz + 20 * dz; }
                else { tx = cx + 20 * dx; tz = cz + 4 * s; }
                set(tx, cy - 1, tz, "minecraft:stone");                    // 基座
                fill(tx, cy, tz, tx, cy + 3, tz, "minecraft:stone");       // 柱（4 高）
                fill(tx - 1, cy + 4, tz - 1, tx + 1, cy + 4, tz + 1, "minecraft:stone"); // 3×3 台
                // 3 张床（头朝僵尸）
                for (int b = -1; b <= 1; b++) {
                    if (dx == 0) {
                        set(tx + b, cy + 5, tz, "minecraft:red_bed[facing=" + face + ",part=foot]");
                        set(tx + b, cy + 5, tz - dz, "minecraft:red_bed[facing=" + face + ",part=head]");
                    } else {
                        set(tx, cy + 5, tz + b, "minecraft:red_bed[facing=" + face + ",part=foot]");
                        set(tx - dx, cy + 5, tz + b, "minecraft:red_bed[facing=" + face + ",part=head]");
                    }
                }
                // 玻璃圈（朝僵尸面开 2 格，白天看得见僵尸）
                if (dx == 0) {
                    set(tx - s, cy + 6, tz - dz, "minecraft:glass");
                    set(tx - 1, cy + 6, tz, "minecraft:glass");
                    set(tx + 1, cy + 6, tz, "minecraft:glass");
                    fill(tx - 1, cy + 6, tz + dz, tx + 1, cy + 6, tz + dz, "minecraft:glass");
                } else {
                    set(tx - dx, cy + 6, tz - s, "minecraft:glass");
                    set(tx, cy + 6, tz - 1, "minecraft:glass");
                    set(tx, cy + 6, tz + 1, "minecraft:glass");
                    fill(tx + dx, cy + 6, tz - 1, tx + dx, cy + 6, tz + 1, "minecraft:glass");
                }
                fill(tx - 1, cy + 7, tz - 1, tx + 1, cy + 7, tz + 1, "minecraft:oak_slab[type=bottom]"); // 半砖封顶
                String VNBT = "{PersistenceRequired:1b,Silent:1b,VillagerData:{profession:\"minecraft:farmer\",level:2,type:\"minecraft:plains\"}}";
                for (int b = -1; b <= 1; b++) {
                    if (dx == 0) summon("minecraft:villager", tx + b, cy + 5, tz, VNBT);
                    else summon("minecraft:villager", tx, cy + 5, tz + b, VNBT);
                }
            }
        }

        // ---------- 8. 检修井：半砖封口，挖开后顺梯子下到箱子取铁 ----------
        fill(cx + 2, cy - 1, cz + 10, cx + 2, cy - 15, cz + 10, "minecraft:air");
        fill(cx + 2, cy - 2, cz + 10, cx + 2, cy - 14, cz + 10, "minecraft:ladder[facing=south]");
        set(cx + 2, cy - 1, cz + 10, SLAB);
        fill(cx + 1, cy - 15, cz + 6, cx + 2, cy - 14, cz + 9, "minecraft:air");

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 刷铁机v8-八核：四向水道+8村民塔+4僵尸坑+岩浆井。"
                        + "天黑村民睡觉后开始出铁；挖开检修井半砖（南侧内圈平台）下梯子取铁。"), false);
    }

    // =====================================================================
    //  刷石机 v5
    // =====================================================================
    public static void cobbleGen(MinecraftClient mc) {
        if (mc.player == null) return;
        int[] c = center(mc);
        int cx = c[0], cy = c[1], cz = c[2];

        begin("刷石机v5");
        StructureUndo.snapshot(mc, cx - 4, cy - 2, cz - 4, cx + 4, cy + 3, cz + 4);

        // 整平
        fill(cx - 4, cy + 1, cz - 4, cx + 4, cy + 4, cz + 4, "minecraft:air");
        fill(cx - 4, cy - 2, cz - 4, cx + 4, cy - 1, cz + 4, "minecraft:stone");

        // 主槽（东西）：水 - 生成位 - 熔岩
        set(cx - 1, cy, cz, "minecraft:water");
        set(cx + 1, cy, cz, "minecraft:lava");
        // 四周围石，只留南面出口
        set(cx - 2, cy, cz, "minecraft:stone");
        set(cx + 2, cy, cz, "minecraft:stone");
        set(cx, cy, cz - 1, "minecraft:stone");
        // 南面 (cz+1) 留空 = 石头被推出去的方向

        // 活塞：放在生成位北面，朝南推
        set(cx, cy, cz - 2, "minecraft:piston[facing=south]");
        // 活塞后方红石块（持续供电 → 活塞常伸）
        set(cx, cy, cz - 3, "minecraft:redstone_block");

        // 收集：南面漏斗 + 箱子
        set(cx, cy - 1, cz + 1, "minecraft:hopper[facing=down]");
        set(cx, cy - 2, cz + 1, "minecraft:chest");

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 刷石机v5：水熔岩相遇长石，活塞常伸推开。"
                        + "挖掉活塞后面红石块再放回，即可做一次脉冲。"), false);
    }

    // =====================================================================
    //  小木屋 v5
    // =====================================================================
    public static void cabin(MinecraftClient mc) {
        if (mc.player == null) return;
        int[] c = center(mc);
        int cx = c[0], cy = c[1], cz = c[2];

        begin("小木屋v5");
        StructureUndo.snapshot(mc, cx - 6, cy - 2, cz - 6, cx + 6, cy + 6, cz + 6);

        // 整平
        fill(cx - 6, cy, cz - 6, cx + 6, cy + 6, cz + 6, "minecraft:air");
        fill(cx - 6, cy - 2, cz - 6, cx + 6, cy - 1, cz + 6, "minecraft:stone");

        // 地板
        fill(cx - 4, cy - 1, cz - 4, cx + 4, cy - 1, cz + 4, "minecraft:oak_planks");

        // 四面墙
        fill(cx - 4, cy, cz - 4, cx + 4, cy + 3, cz - 4, "minecraft:oak_log");
        fill(cx - 4, cy, cz + 4, cx + 4, cy + 3, cz + 4, "minecraft:oak_log");
        fill(cx - 4, cy, cz - 4, cx - 4, cy + 3, cz + 4, "minecraft:oak_log");
        fill(cx + 4, cy, cz - 4, cx + 4, cy + 3, cz + 4, "minecraft:oak_log");

        // 门（南墙正中）
        set(cx, cy, cz + 4, "minecraft:oak_door[facing=north,hinge=left,half=lower]");
        set(cx, cy + 1, cz + 4, "minecraft:oak_door[facing=north,hinge=left,half=upper]");

        // 窗
        set(cx, cy + 1, cz - 4, "minecraft:glass");
        set(cx - 4, cy + 1, cz, "minecraft:glass");
        set(cx + 4, cy + 1, cz, "minecraft:glass");

        // 屋顶
        fill(cx - 4, cy + 4, cz - 4, cx + 4, cy + 4, cz + 4, "minecraft:oak_planks");

        // ★床：facing=south → head 在 z+1、foot 在 z（头朝南）
        set(cx - 3, cy, cz - 3, "minecraft:red_bed[facing=south,part=foot]");
        set(cx - 3, cy, cz - 2, "minecraft:red_bed[facing=south,part=head]");

        // 家具
        set(cx + 3, cy, cz - 3, "minecraft:crafting_table");
        set(cx + 3, cy, cz - 2, "minecraft:furnace[facing=south]");
        set(cx + 3, cy, cz - 1, "minecraft:chest[facing=south]");

        // 火把（防屋内刷怪）
        set(cx, cy + 2, cz - 3, "minecraft:torch");
        set(cx, cy + 2, cz + 3, "minecraft:torch");

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 小木屋v5 完成。"), false);
    }
}