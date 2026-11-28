package com.ghost.cheatmenu.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构生成器 v3
 *  - 中心点：玩家视线前方 8 格（玩家不站进建筑里）
 *  - 每个建筑第一步都是"地基整平"：footprint 往下 2 层填实 + 上方清空
 *  - 坐标全部逐层推演过（见各方法注释的布局图）
 *  - 撤销见 StructureUndo（快照含空气，能完全还原地形）
 */
public class StructureBuilder {

    /** 玩家视线前方 8 格的水平投影中心 */
    private static int[] center(MinecraftClient mc) {
        double ex = mc.player.getX();
        double ey = mc.player.getY() + mc.player.getStandingEyeHeight();
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
            String cmd = QUEUE.remove(0);
            mc.getNetworkHandler().sendPacket(new ChatMessageC2SPacket(cmd));
        }

        if (QUEUE.isEmpty() && mc.player != null) {
            mc.player.sendMessage(new net.minecraft.text.LiteralText(
                    "\u00a7a[GhostMenu] " + label + " 生成完毕，共 " + total + " 条指令"), false);
        }
    }

    public static boolean busy() {
        return !QUEUE.isEmpty();
    }

    public static void clear() {
        QUEUE.clear();
        label = "";
        total = 0;
    }

    private static void begin(String name) {
        QUEUE.clear();
        label = name;
        total = 0;
    }

    private static void add(String cmd) {
        QUEUE.add(cmd);
        total++;
    }

    private static void fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        add(String.format("/fill %d %d %d %d %d %d %s", x1, y1, z1, x2, y2, z2, block));
    }

    private static void set(int x, int y, int z, String block) {
        add(String.format("/setblock %d %d %d %s", x, y, z, block));
    }

    private static void summon(String entity, int x, int y, int z, String nbt) {
        if (nbt == null || nbt.isEmpty()) {
            add(String.format("/summon %s %d %d %d", entity, x, y, z));
        } else {
            add(String.format("/summon %s %d %d %d %s", entity, x, y, z, nbt));
        }
    }

    // =====================================================================
    //  刷铁机 v3 —— 坐标系（cx,cy,cz = 中心，玩家前方 8 格）
    //
    //  cy-3..cy-2 : 17×17 石头地基两层（把地面打平）
    //  cy-1       : 15×15 石头平台 = 铁傀儡生成面（wiki: 禁玻璃）
    //  cy         : 平台表面层
    //               ├─ 四周挡墙（石头1层，防傀儡走丢）
    //               ├─ 中心村民仓 5×6（外墙 x∈[-2..2], z∈[-3..2]）
    //               │    内腔 3×4（x∈[-1..1], z∈[-2..1]）
    //               │    北墙 z=-3 是玻璃窗（y+1 层）
    //               │    内部：3 床 + 3 工作站 + 灵魂沙气泡柱
    //               └─ 井口 (cx-1..cx+1, cz+3..cz+5)
    //  cy+1       : 挡墙玻璃层（防跳）/ 仓顶玻璃
    //  僵尸笼      : (cx, cz-5) 1×1 石笼高2，南面玻璃对仓窗
    //  收集井      : 3×3 从 cy 下挖 17 格；cy-16 水层溺死
    //               cy-17 漏斗3×3 → 中央漏斗 → (cx, cy-18, cz+4) 箱子
    //  水流       : 平台表面：西带 x=-6..-3 全 z、东带 x=3..6 全 z、
    //               南角 (cx-1..cx+1, cz+6) —— 全部源水，自然流向井口
    // =====================================================================
    public static void ironFarm(MinecraftClient mc) {
        if (mc.player == null) return;
        int[] c = center(mc);
        int cx = c[0], cy = c[1], cz = c[2];

        begin("刷铁机v3");

        // ---- 0. 快照（撤销用）----
        StructureUndo.snapshot(mc, cx - 8, cy - 20, cz - 8, cx + 8, cy + 4, cz + 8);

        // ---- 1. 地基整平：17×17 区域，上清空、下两层填实 ----
        fill(cx - 8, cy + 1, cz - 8, cx + 8, cy + 6, cz + 8, "minecraft:air");   // 上方清空
        fill(cx - 8, cy - 3, cz - 8, cx + 8, cy - 1, cz + 8, "minecraft:stone"); // 打平+地基

        // ---- 2. 生成平台：15×15（即地基顶面本身，cy-1）----
        //     （上面 fill 已把 cy-1 层填成石头，无需重复）

        // ---- 3. 挡墙：cy 层石头，cy+1 层玻璃（周圈 15×15 边缘）----
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

        // ---- 4. 村民仓 5×6 外壳（x -2..2, z -3..2，墙高 cy..cy+2，顶 cy+3）----
        fill(cx - 2, cy, cz - 3, cx + 2, cy + 2, cz + 2, "minecraft:stone");
        fill(cx - 1, cy, cz - 2, cx + 1, cy + 2, cz + 1, "minecraft:air");       // 内腔 3×4
        fill(cx - 1, cy + 1, cz - 3, cx + 1, cy + 1, cz - 3, "minecraft:glass"); // 北窗（y+1 层 3 宽）
        fill(cx - 2, cy + 3, cz - 3, cx + 2, cy + 3, cz + 2, "minecraft:glass"); // 仓顶玻璃
        // 东墙开门（1×2，供玩家喂食/查看；不需要可封）
        fill(cx + 2, cy, cz, cx + 2, cy + 1, cz, "minecraft:air");

        // ---- 5. 仓内：3 床（沿内腔北两排）+ 3 工作站 + 气泡柱 ----
        // 床1：头 (cx-1, cz-2) 脚 (cx, cz-2) 朝东
        set(cx - 1, cy, cz - 2, "minecraft:red_bed[part=head,facing=east]");
        set(cx,     cy, cz - 2, "minecraft:red_bed[part=foot,facing=east]");
        // 床2：头 (cx-1, cz-1) 脚 (cx, cz-1) 朝东
        set(cx - 1, cy, cz - 1, "minecraft:red_bed[part=head,facing=east]");
        set(cx,     cy, cz - 1, "minecraft:red_bed[part=foot,facing=east]");
        // 工作站（南排）：堆肥桶 (cx+1, cz-2)、桶 (cx+1, cz-1)、堆肥桶 (cx+1, cz)
        set(cx + 1, cy, cz - 2, "minecraft:composter");
        set(cx + 1, cy, cz - 1, "minecraft:barrel");
        set(cx + 1, cy, cz,     "minecraft:composter");
        // 气泡柱：内腔西南角 (cx-1, cz+1)，水柱 3 高 + 底部灵魂沙
        fill(cx - 1, cy, cz + 1, cx - 1, cy + 2, cz + 1, "minecraft:water");
        set(cx - 1, cy, cz + 1, "minecraft:soul_sand");
        // 第3张床：放在气泡柱对面（cx+1 列被工作站占了，放 (cx, cz+1)? 那是水柱旁）
        // → 第 3 床放内腔中央行 (cx-1..cx, cz)：头 (cx-1,cz) 脚 (cx,cz)
        set(cx - 1, cy, cz, "minecraft:red_bed[part=head,facing=east]");
        set(cx,     cy, cz, "minecraft:red_bed[part=foot,facing=east]");
        // 3 床 3 民压满条件

        // ---- 6. 三村民 ----
        String vNbt = "{PersistenceRequired:1b,VillagerData:{profession:\"minecraft:farmer\",level:2,type:\"minecraft:plains\"}}";
        summon("minecraft:villager", cx - 1, cy, cz - 1, vNbt);
        summon("minecraft:villager", cx,     cy, cz - 1, vNbt);
        summon("minecraft:villager", cx,     cy, cz,     vNbt);

        // ---- 7. 僵尸笼 (cx, cz-5)：1×1 石笼，南面玻璃对窗 ----
        int zx = cx, zy = cy, zz = cz - 5;
        set(zx, zy - 1, zz, "minecraft:stone");                                   // 笼底
        fill(zx - 1, zy, zz - 1, zx + 1, zy + 2, zz + 1, "minecraft:stone");      // 笼壳
        fill(zx, zy, zz, zx, zy + 1, zz, "minecraft:air");                        // 笼腔
        set(zx, zy + 1, zz + 1, "minecraft:glass");                               // 南面玻璃窗（对村民）
        set(zx, zy, zz, "minecraft:rail");
        summon("minecraft:minecart", zx, zy, zz, null);
        add(String.format(
            "/execute as @e[type=minecraft:minecart,limit=1,sort=nearest,x=%d,y=%d,z=%d] run summon minecraft:zombie ~ ~ ~ {PersistenceRequired:1b,Silent:1b}",
            zx, zy, zz));

        // ---- 8. 水流集中：推往井口 (cx±1, cz+3..5) ----
        // 西带 x=-6..-3（z -6..+6 全部源水 → 向东流）
        for (int z = -6; z <= 6; z++) {
            for (int x = -6; x >= -3; x--) {
                set(cx + x, cy, cz + z, "minecraft:water");
            }
        }
        // 东带 x=3..6（→ 向西流）
        for (int z = -6; z <= 6; z++) {
            for (int x = 3; x <= 6; x++) {
                set(cx + x, cy, cz + z, "minecraft:water");
            }
        }
        // 南角 (cx-1..cx+1, cz+6)（→ 向北流，直接顶到井口南缘）
        for (int x = -1; x <= 1; x++) {
            set(cx + x, cy, cz + 6, "minecraft:water");
        }

        // ---- 9. 收集井：3×3 (cx-1..cx+1, cz+3..cz+5)，从 cy 挖到 cy-17 ----
        fill(cx - 1, cy, cz + 3, cx + 1, cy, cz + 5, "minecraft:air");            // 井口
        fill(cx - 1, cy - 17, cz + 3, cx + 1, cy - 2, cz + 5, "minecraft:air");   // 井腔
        fill(cx - 1, cy - 17, cz + 3, cx + 1, cy - 17, cz + 5, "minecraft:water");// 井底水层（溺死）

        // ---- 10. 收集：cy-18 漏斗 3×3 → 中央 → 箱子（cy-18, cz+4 前方一格）----
        fill(cx - 1, cy - 18, cz + 3, cx + 1, cy - 18, cz + 5, "minecraft:hopper");
        set(cx - 1, cy - 18, cz + 4, "minecraft:hopper[facing=east]");
        set(cx + 1, cy - 18, cz + 4, "minecraft:hopper[facing=west]");
        set(cx,     cy - 18, cz + 3, "minecraft:hopper[facing=south]");
        set(cx,     cy - 18, cz + 5, "minecraft:hopper[facing=north]");
        set(cx,     cy - 18, cz + 4, "minecraft:hopper[facing=down]");
        set(cx,     cy - 19, cz + 4, "minecraft:chest");

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 刷铁机v3 建好了。等村民睡一觉（≤20 分钟）开始出铁，铁锭在井底箱子。"), false);
    }

    // =====================================================================
    //  刷怪塔 v3 —— 坐标系（cx,cy,cz = 中心）
    //
    //  cy-2..cy-1 : 7×7 石头地基（打平）
    //  cy         : 收集层地面（箱子埋地下）
    //  cy+1..cy+2 : 处死室（5×5）：岩浆块 3×3 + 南侧玩家缺口
    //  cy+3..cy+14: 竖井 1×1（中心）
    //  cy+15      : 生成层地板 9×9
    //  cy+16..17  : 生成室内部（2 格高，全黑）
    //  cy+18      : 石头顶 + bottom 半砖（防塔顶生成怪）
    //  修脚洞     : 南墙 (cx, cy+1..cy+2)，墙外 (cx, cz+3) 石台阶
    // =====================================================================
    public static void mobTower(MinecraftClient mc) {
        if (mc.player == null) return;
        int[] c = center(mc);
        int cx = c[0], cy = c[1], cz = c[2];

        begin("刷怪塔v3");

        // ---- 0. 快照 ----
        StructureUndo.snapshot(mc, cx - 5, cy - 2, cz - 5, cx + 5, cy + 19, cz + 5);

        // ---- 1. 地基整平：7×7，上清空下两层填实 ----
        fill(cx - 5, cy + 1, cz - 5, cx + 5, cy + 18, cz + 5, "minecraft:air");
        fill(cx - 5, cy - 2, cz - 5, cx + 5, cy - 1, cz + 5, "minecraft:stone");

        // ---- 2. 收集层：中心漏斗 → 箱子（埋在 cy-1）----
        set(cx, cy, cz, "minecraft:hopper[facing=down]");
        set(cx, cy - 1, cz, "minecraft:chest");

        // ---- 3. 处死室 5×5 外壳（cy+1..cy+2 石墙），内部清空 ----
        fill(cx - 2, cy + 1, cz - 2, cx + 2, cy + 2, cz + 2, "minecraft:stone");
        fill(cx - 1, cy + 1, cz - 1, cx + 1, cy + 2, cz + 1, "minecraft:air");
        // 岩浆块 3×3，但南侧中间 (cx, cz+1) 缺一块给玩家落脚（玩家脚位）
        set(cx - 1, cy + 1, cz - 1, "minecraft:magma_block");
        set(cx,     cy + 1, cz - 1, "minecraft:magma_block");
        set(cx + 1, cy + 1, cz - 1, "minecraft:magma_block");
        set(cx - 1, cy + 1, cz,     "minecraft:magma_block");
        set(cx + 1, cy + 1, cz,     "minecraft:magma_block");
        set(cx - 1, cy + 1, cz + 1, "minecraft:magma_block");
        set(cx + 1, cy + 1, cz + 1, "minecraft:magma_block");
        // (cx, cz+1) 留空 = 玩家脚站的位置（无岩浆）
        set(cx, cy + 1, cz + 1, "minecraft:stone");   // 玩家落脚石

        // ---- 4. 修脚洞：南墙 (cx, cy+1..cy+2) 开 1×2；墙外台阶 (cx, cz+3) ----
        fill(cx, cy + 1, cz + 2, cx, cy + 2, cz + 2, "minecraft:air");
        set(cx, cy + 1, cz + 3, "minecraft:stone");
        fill(cx, cy + 2, cz + 3, cx, cy + 3, cz + 3, "minecraft:air");

        // ---- 5. 竖井 1×1：cy+3..cy+14 清空 ----
        fill(cx, cy + 3, cz, cx, cy + 14, cz, "minecraft:air");

        // ---- 6. 生成层 9×9 平台（cy+15）----
        fill(cx - 4, cy + 15, cz - 4, cx + 4, cy + 15, cz + 4, "minecraft:stone");
        // 中心井口开 1×1
        set(cx, cy + 15, cz, "minecraft:air");

        // ---- 7. 生成室：cy+16..cy+17 石墙（5..9 圈），内部全黑 ----
        fill(cx - 5, cy + 16, cz - 5, cx + 5, cy + 17, cz + 5, "minecraft:stone");
        fill(cx - 4, cy + 16, cz - 4, cx + 4, cy + 17, cz + 4, "minecraft:air");

        // ---- 8. 屋顶：cy+18 石头 + bottom 半砖（防生成）----
        fill(cx - 5, cy + 18, cz - 5, cx + 5, cy + 18, cz + 5, "minecraft:stone");
        fill(cx - 4, cy + 19, cz - 4, cx + 4, cy + 19, cz + 4, "minecraft:stone_slab[type=bottom]");

        // ---- 9. 水流：4 边推向中心（源在墙边 3 格，流向中心井口）----
        set(cx - 3, cy + 16, cz, "minecraft:water");
        set(cx - 2, cy + 16, cz, "minecraft:water");
        set(cx + 3, cy + 16, cz, "minecraft:water");
        set(cx + 2, cy + 16, cz, "minecraft:water");
        set(cx, cy + 16, cz - 3, "minecraft:water");
        set(cx, cy + 16, cz - 2, "minecraft:water");
        set(cx, cy + 16, cz + 3, "minecraft:water");
        set(cx, cy + 16, cz + 2, "minecraft:water");

        // ---- 10. 四根支撑柱：从 cy-1 到 cy+14（1×1，四角）----
        for (int[] corner : new int[][]{{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) {
            fill(cx + corner[0], cy - 1, cz + corner[1], cx + corner[0], cy + 14, cz + corner[1], "minecraft:stone");
        }

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 刷怪塔v3 完成。挂机：站南墙修脚洞（缺口）处补刀收经验，或离塔 24 格挂机自动岩浆烧死。"), false);
    }

    // =====================================================================
    //  刷石机 v3 —— 带红石时钟（中继器×4 组成的 0.8s 脉冲环）
    //
    //  布局（cx,cy,cz = 中心）：
    //   水槽   (cx-1, cy, cz)
    //   生成位 (cx,   cy, cz)    ← 水熔岩相遇长石头
    //   熔岩槽 (cx+1, cy, cz)
    //   活塞   (cx, cy, cz-1) 面南 —— 推生成位的石头往南掉
    //   红石时钟（cy 层，活塞后方）：
    //     中继器回路：4 个中继器 delay=4 围成圈 + 4 红石粉连接
    //     支路：红石粉 (cx+1, cz-1) → 活塞
    //     触发：最后在 (cx-1, cz-2) 放一格红石粉，时钟起振
    //   收集   : (cx, cy-1, cz+1) 漏斗 → (cx, cy-2, cz+1) 箱子
    // =====================================================================
    public static void cobbleGen(MinecraftClient mc) {
        if (mc.player == null) return;
        int[] c = center(mc);
        int cx = c[0], cy = c[1], cz = c[2];

        begin("刷石机v3");

        // ---- 0. 快照 ----
        StructureUndo.snapshot(mc, cx - 3, cy - 2, cz - 3, cx + 3, cy + 2, cz + 3);

        // ---- 1. 地基整平：7×7 ----
        fill(cx - 3, cy + 1, cz - 3, cx + 3, cy + 3, cz + 3, "minecraft:air");
        fill(cx - 3, cy - 2, cz - 3, cx + 3, cy - 1, cz + 3, "minecraft:stone");

        // ---- 2. 槽位与两侧挡墙 ----
        set(cx - 1, cy, cz, "minecraft:water");
        set(cx + 1, cy, cz, "minecraft:lava");
        // 挡墙：北 (cz-1) 南 (cz+1) 两侧的石头护墙（防液体外流）
        set(cx - 2, cy, cz, "minecraft:stone");
        set(cx + 2, cy, cz, "minecraft:stone");
        set(cx - 1, cy, cz + 1, "minecraft:stone");
        set(cx + 1, cy, cz + 1, "minecraft:stone");

        // ---- 3. 活塞（生成位北面一格，面南）----
        set(cx, cy, cz - 1, "minecraft:piston[facing=south]");

        // ---- 4. 红石时钟回路（cy 层，活塞北侧）----
        //     4 中继器 + 4 红石粉围成 3×3 闭合环：
        //   A(cx-1,cz-2) repeater[east] → B(cx,cz-2) dust → C(cx+1,cz-2) repeater[south]
        //   D(cx+2,cz-1) dust → E(cx+2,cz) repeater[west] → F(cx+1,cz+1) dust
        //   G(cx,cz+1) repeater[north] → H(cx-1,cz) dust → 回到 A
        set(cx - 1, cy, cz - 2, "minecraft:repeater[facing=east,delay=4]");
        set(cx,     cy, cz - 2, "minecraft:redstone_wire[east=side,west=side,north=none,south=none]");
        set(cx + 1, cy, cz - 2, "minecraft:repeater[facing=south,delay=4]");
        set(cx + 2, cy, cz - 1, "minecraft:redstone_wire[east=none,west=none,north=side,south=side]");
        set(cx + 2, cy, cz,     "minecraft:repeater[facing=west,delay=4]");
        set(cx + 1, cy, cz + 1, "minecraft:redstone_wire[east=side,west=side,north=none,south=none]");
        set(cx,     cy, cz + 1, "minecraft:repeater[facing=north,delay=4]");
        set(cx - 1, cy, cz,     "minecraft:redstone_wire[east=none,west=none,north=side,south=side]");
        // 支路：红石粉 (cx+1, cz-1) 连到活塞后方
        set(cx + 1, cy, cz - 1, "minecraft:redstone_wire[east=none,west=none,north=side,south=side]");

        // ---- 5. 起振开关：最后一格红石粉（放上去即起振，拆掉即停）----
        set(cx - 2, cy, cz - 2, "minecraft:redstone_wire[east=side,west=none,north=none,south=none]");

        // ---- 6. 收集：南侧掉落通道 → 漏斗 → 箱子 ----
        set(cx, cy - 1, cz + 1, "minecraft:hopper[facing=down]");
        set(cx, cy - 2, cz + 1, "minecraft:chest");
        set(cx, cy, cz + 1, "minecraft:air");   // 掉落通道

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 刷石机v3 完成，红石时钟已起振，活塞每 0.8 秒推一次。"
                        + "站南边捡圆石即可（挖一下掉一个）。拆掉 (cx-2,cy,cz-2) 那格红石粉可停机。"), false);
    }

    // =====================================================================
    //  小木屋 v3
    // =====================================================================
    public static void cabin(MinecraftClient mc) {
        if (mc.player == null) return;
        int[] c = center(mc);
        int cx = c[0], cy = c[1], cz = c[2];

        begin("小木屋v3");

        // ---- 0. 快照 ----
        StructureUndo.snapshot(mc, cx - 5, cy - 2, cz - 5, cx + 5, cy + 5, cz + 5);

        // ---- 1. 地基整平：11×11 上清空下填实 ----
        fill(cx - 5, cy, cz - 5, cx + 5, cy + 5, cz + 5, "minecraft:air");
        fill(cx - 5, cy - 2, cz - 5, cx + 5, cy - 1, cz + 5, "minecraft:stone");

        // ---- 2. 地板（橡木木板 9×9）----
        fill(cx - 4, cy - 1, cz - 4, cx + 4, cy - 1, cz + 4, "minecraft:oak_planks");

        // ---- 3. 四面原木墙（cy..cy+3）----
        fill(cx - 4, cy, cz - 4, cx + 4, cy + 3, cz - 4, "minecraft:oak_log");
        fill(cx - 4, cy, cz + 4, cx + 4, cy + 3, cz + 4, "minecraft:oak_log");
        fill(cx - 4, cy, cz - 4, cx - 4, cy + 3, cz + 4, "minecraft:oak_log");
        fill(cx + 4, cy, cz - 4, cx + 4, cy + 3, cz + 4, "minecraft:oak_log");

        // ---- 4. 门（南墙正中）----
        set(cx, cy, cz + 4, "minecraft:oak_door[facing=north,half=lower]");
        set(cx, cy + 1, cz + 4, "minecraft:oak_door[facing=north,half=upper]");

        // ---- 5. 窗（四面各 1 玻璃）----
        set(cx, cy + 1, cz - 4, "minecraft:glass");
        set(cx - 4, cy + 1, cz, "minecraft:glass");
        set(cx + 4, cy + 1, cz, "minecraft:glass");

        // ---- 6. 屋顶（cy+4 木板封顶）----
        fill(cx - 4, cy + 4, cz - 4, cx + 4, cy + 4, cz + 4, "minecraft:oak_planks");

        // ---- 7. 家具 ----
        set(cx - 3, cy, cz - 3, "minecraft:red_bed[part=head,facing=south]");
        set(cx - 2, cy, cz - 3, "minecraft:red_bed[part=foot,facing=south]");
        set(cx + 3, cy, cz - 3, "minecraft:crafting_table");
        set(cx + 3, cy, cz - 2, "minecraft:furnace[facing=south]");
        set(cx + 3, cy, cz - 1, "minecraft:chest[facing=south]");

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 小木屋v3 完成。"), false);
    }
}