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
    //  刷铁机 v5
    // =====================================================================
    public static void ironFarm(MinecraftClient mc) {
        if (mc.player == null) return;
        int[] c = center(mc);
        int cx = c[0], cy = c[1], cz = c[2];

        begin("刷铁机v5");
        StructureUndo.snapshot(mc, cx - 10, cy - 20, cz - 10, cx + 10, cy + 5, cz + 10);

        // 1. 整平：21×21 地基
        fill(cx - 10, cy + 1, cz - 10, cx + 10, cy + 6, cz + 10, "minecraft:air");
        fill(cx - 10, cy - 2, cz - 10, cx + 10, cy - 1, cz + 10, "minecraft:stone");

        // 2. 挡墙（15×15 边框，cy 层石头 + cy+1 玻璃）
        for (int i = -7; i <= 7; i++) {
            set(cx + i, cy, cz - 7, "minecraft:stone");
            set(cx + i, cy, cz + 7, "minecraft:stone");
            set(cx - 7, cy, cz + i, "minecraft:stone");
            set(cx + 7, cy, cz + i, "minecraft:stone");
            set(cx + i, cy + 1, cz - 7, "minecraft:glass");
            set(cx + i, cy + 1, cz + 7, "minecraft:glass");
            set(cx - 7, cy + 1, cz + i, "minecraft:glass");
            set(cx + 7, cy + 1, cz + i, "minecraft:glass");
        }

        // 3. 村民仓 5×6（x -2..2, z -3..2）
        fill(cx - 2, cy, cz - 3, cx + 2, cy + 2, cz + 2, "minecraft:stone");
        fill(cx - 1, cy, cz - 2, cx + 1, cy + 2, cz + 1, "minecraft:air");
        fill(cx - 1, cy + 1, cz - 3, cx + 1, cy + 1, cz - 3, "minecraft:glass");
        fill(cx - 2, cy + 3, cz - 3, cx + 2, cy + 3, cz + 2, "minecraft:glass");

        // 4. 床：facing=east → head 在 x+1、foot 在 x
        set(cx,     cy, cz - 2, "minecraft:red_bed[facing=east,part=foot]");
        set(cx + 1, cy, cz - 2, "minecraft:red_bed[facing=east,part=head]");
        set(cx,     cy, cz - 1, "minecraft:red_bed[facing=east,part=foot]");
        set(cx + 1, cy, cz - 1, "minecraft:red_bed[facing=east,part=head]");
        set(cx,     cy, cz,     "minecraft:red_bed[facing=east,part=foot]");
        set(cx + 1, cy, cz,     "minecraft:red_bed[facing=east,part=head]");

        // 5. 工作站（内腔西侧）
        set(cx - 1, cy, cz - 2, "minecraft:composter");
        set(cx - 1, cy, cz - 1, "minecraft:barrel");
        set(cx - 1, cy, cz,     "minecraft:smithing_table");

        // 6. 三村民
        String vNbt = "{PersistenceRequired:1b,VillagerData:{profession:\"minecraft:farmer\",level:2,type:\"minecraft:plains\"}}";
        summon("minecraft:villager", cx, cy, cz - 1, vNbt);
        summon("minecraft:villager", cx + 1, cy, cz + 1, vNbt);
        summon("minecraft:villager", cx - 1, cy, cz + 1, vNbt);

        // 7. 僵尸笼 (cz-5)
        set(cx, cy - 1, cz - 5, "minecraft:stone");
        fill(cx - 1, cy, cz - 6, cx + 1, cy + 2, cz - 4, "minecraft:stone");
        fill(cx, cy, cz - 5, cx, cy + 1, cz - 5, "minecraft:air");
        set(cx, cy + 1, cz - 4, "minecraft:glass");
        set(cx, cy, cz - 5, "minecraft:rail");
        summon("minecraft:minecart", cx, cy, cz - 5, null);
        add(String.format(
            "/execute as @e[type=minecraft:minecart,limit=1,sort=nearest,x=%d,y=%d,z=%d] run summon minecraft:zombie ~ ~ ~ {PersistenceRequired:1b,Silent:1b}",
            cx, cy, cz - 5));

        // 8. 收集井 3×3 外挂东北（cx+5..cx+7, cz-4..cz-2），深 17
        int hx0 = cx + 5, hx1 = cx + 7, hz0 = cz - 4, hz1 = cz - 2;
        fill(hx0, cy, hz0, hx1, cy, hz1, "minecraft:air");
        fill(hx0, cy - 16, hz0, hx1, cy - 2, hz1, "minecraft:air");
        // 井壁
        for (int y = cy - 16; y <= cy; y++) {
            for (int x = hx0; x <= hx1; x++) {
                set(x, y, hz0 - 1, "minecraft:stone");
                set(x, y, hz1 + 1, "minecraft:stone");
            }
            for (int z = hz0; z <= hz1; z++) {
                set(hx0 - 1, y, z, "minecraft:stone");
                set(hx1 + 1, y, z, "minecraft:stone");
            }
        }
        // 井底水层（溺死）+ 封底
        fill(hx0, cy - 17, hz0, hx1, cy - 17, hz1, "minecraft:water");
        fill(hx0, cy - 18, hz0, hx1, cy - 18, hz1, "minecraft:stone");

        // 9. 收集：cy-19 漏斗 3×3 → 箱 cy-20
        fill(hx0, cy - 19, hz0, hx1, cy - 19, hz1, "minecraft:hopper");
        set(hx0, cy - 19, hz1 - 1, "minecraft:hopper[facing=east]");
        set(hx1, cy - 19, hz1 - 1, "minecraft:hopper[facing=west]");
        set(hx0 + 1, cy - 19, hz0, "minecraft:hopper[facing=south]");
        set(hx0 + 1, cy - 19, hz1, "minecraft:hopper[facing=north]");
        set(hx0 + 1, cy - 19, hz1 - 1, "minecraft:hopper[facing=down]");
        set(hx0 + 1, cy - 20, hz1 - 1, "minecraft:chest");

        // 10. 平台水流：贴墙内缘，靠 7 格上限自然断流，不会灌进井
        //     墙内缘 x/z = ±6，井口在 cx+5..7 → 西侧水流到井口前就断
        for (int i = -6; i <= 6; i++) {
            // 北/南边（避开村民仓与井口）
            if (!(cz - 6 >= cz - 3 && cz - 6 <= cz + 2)) {
                set(cx + i, cy, cz - 6, "minecraft:water");
            }
            if (!(cz + 6 >= cz - 3 && cz + 6 <= cz + 2)) {
                set(cx + i, cy, cz + 6, "minecraft:water");
            }
            set(cx - 6, cy, cz + i, "minecraft:water");
            // 东边只放 z < hz0 的部分（避开井口上方）
            if (cz + i < hz0) {
                set(cx + 6, cy, cz + i, "minecraft:water");
            }
        }

        // 11. 火把：平台外沿 + 地基外圈，防周边刷怪
        for (int i = -10; i <= 10; i += 4) {
            set(cx + i, cy, cz - 10, "minecraft:torch");
            set(cx + i, cy, cz + 10, "minecraft:torch");
            set(cx - 10, cy, cz + i, "minecraft:torch");
            set(cx + 10, cy, cz + i, "minecraft:torch");
        }

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 刷铁机v5：平台水贴墙、7 格自然断流；井壁封闭，井底水溺死；"
                        + "外围插火把防杂怪。等村民睡觉后出铁，铁锭在井底箱子。"), false);
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