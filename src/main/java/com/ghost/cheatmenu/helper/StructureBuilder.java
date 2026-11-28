package com.ghost.cheatmenu.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构生成器 v2 —— 布局按 zh.minecraft.wiki 两篇攻略修正
 *
 * 刷铁机（wiki:Tutorial:刷铁机）：
 *  - 生成面禁止玻璃/冰/树叶等 → 全石头平台
 *  - 床数 ≥ 村民数，村民需 20 分钟内睡过觉 → 灵魂沙气泡柱间歇恐吓
 *  - 僵尸塞矿车限制移动
 *  - 处死用溺水（熔岩会烧掉铁锭，岩浆块下漏斗收不到固体下的掉落物）
 *
 * 刷怪塔（wiki:Tutorial:刷怪塔）：
 *  - 塔体悬空，挂机点远离地面/洞穴（防生物生成在塔外占上限）
 *  - 生成平台大而矮（单层 2 格高）
 *  - 处死用岩浆块（不烧掉落物）+ 水流集中，不用摔落井
 *  - 漏斗直接贴箱子收集
 */
public class StructureBuilder {

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

    // =========================================================
    //  刷铁机 v2（wiki 修正版）
    // =========================================================

    /**
     * 布局（bx,by,bz = 玩家脚下方块）：
     *   y-1   15x15 石头平台（生成面，绝不玻璃）
     *   y     平台表面
     *   仓    中心 5x5 石头墙（玻璃只做北窗），3 床 3 工作站 3 村民
     *   僵尸  仓北侧 3x3 石笼，矿车+僵尸，玻璃窗对村民
     *   气泡  仓内西北角灵魂沙气泡柱，村民周期脱离恐吓睡觉
     *   井    平台东缘外挂 3x3 石井 20 格深
     *   井底  水（溺死）→ 下层漏斗 3x3 → 箱子
     *   屋顶  玻璃（玻璃不能当生成支撑 → 铁傀儡不会生成在屋顶上）
     */
    public static void ironFarm(MinecraftClient mc) {
        if (mc.player == null) return;
        int bx = (int) Math.floor(mc.player.getX());
        int by = (int) Math.floor(mc.player.getY());
        int bz = (int) Math.floor(mc.player.getZ());

        begin("刷铁机v2");

        // ---- 1. 清空工作区 ----
        fill(bx - 8, by - 22, bz - 8, bx + 10, by + 8, bz + 8, "minecraft:air");

        // ---- 2. 平台 15x15 石头（生成面）----
        fill(bx - 7, by - 1, bz - 7, bx + 7, by - 1, bz + 7, "minecraft:stone");

        // ---- 3. 平台挡墙 2 高：石头下部 + 玻璃上部 ----
        //      下层石头挡铁傀儡走，上层玻璃透光且不能生成
        for (int i = -7; i <= 7; i++) {
            set(bx + i, by, bz - 7, "minecraft:stone");
            set(bx + i, by, bz + 7, "minecraft:stone");
            set(bx - 7, by, bz + i, "minecraft:stone");
            set(bx + 7, by, bz + i, "minecraft:stone");
            set(bx + i, by + 1, bz - 7, "minecraft:glass");
            set(bx + i, by + 1, bz + 7, "minecraft:glass");
            set(bx - 7, by + 1, bz + i, "minecraft:glass");
            set(bx + 7, by + 1, bz + i, "minecraft:glass");
        }

        // ---- 4. 村民仓 5x5：石墙，北墙开 3 宽玻璃窗（y+1 层）----
        fill(bx - 2, by, bz - 2, bx + 2, by + 3, bz + 2, "minecraft:air");
        fill(bx - 2, by, bz - 2, bx + 2, by + 3, bz + 2, "minecraft:stone");   // 先整体填实
        fill(bx - 1, by, bz - 1, bx + 1, by + 2, bz + 1, "minecraft:air");     // 挖空内部
        fill(bx - 1, by + 1, bz - 2, bx + 1, by + 1, bz - 2, "minecraft:glass"); // 北窗
        fill(bx - 2, by + 3, bz - 2, bx + 2, by + 3, bz + 2, "minecraft:glass"); // 仓顶玻璃（不能生成）
        // 开门（东墙中部 1x2）
        fill(bx + 2, by, bz, bx + 2, by + 1, bz, "minecraft:air");

        // ---- 5. 内部：3 床 + 3 工作站 ----
        set(bx - 1, by, bz - 1, "minecraft:red_bed[part=head,facing=east]");
        set(bx, by, bz - 1, "minecraft:red_bed[part=foot,facing=east]");
        set(bx + 1, by, bz - 1, "minecraft:composter");
        set(bx - 1, by, bz + 1, "minecraft:red_bed[part=head,facing=west]");
        set(bx, by, bz + 1, "minecraft:red_bed[part=foot,facing=west]");
        set(bx + 1, by, bz + 1, "minecraft:barrel");

        // ---- 6. 三村民 ----
        String vNbt = "{PersistenceRequired:1b,VillagerData:{profession:\"minecraft:farmer\",level:2,type:\"minecraft:plains\"}}";
        summon("minecraft:villager", bx - 1, by, bz, vNbt);
        summon("minecraft:villager", bx, by, bz, vNbt);
        summon("minecraft:villager", bx + 1, by, bz, vNbt);

        // ---- 7. 灵魂沙气泡柱（仓内西墙角）：间歇恐吓 ----
        fill(bx - 1, by, bz + 1, bx - 1, by + 2, bz + 1, "minecraft:water");
        set(bx - 1, by, bz + 1, "minecraft:soul_sand");
        // 床 bz+1 排会和气泡柱位置重叠 → 那格床已在上面 set 会被水覆盖，微调：
        // 把 bz+1 那格床移到 bz（中央）
        set(bx - 1, by, bz + 1, "minecraft:red_bed[part=head,facing=west]");
        set(bx, by, bz + 1, "minecraft:red_bed[part=foot,facing=west]");
        // （water fill 在前面，bed set 在后面覆盖成功）

        // ---- 8. 僵尸笼（平台北缘外贴仓），僵尸坐矿车 ----
        int zx = bx, zy = by, zz = bz - 4;
        fill(zx - 1, zy - 1, zz - 1, zx + 1, zy + 2, zz + 1, "minecraft:air");
        set(zx, zy - 1, zz, "minecraft:stone");
        fill(zx - 1, zy, zz - 1, zx + 1, zy + 2, zz - 1, "minecraft:stone");
        fill(zx - 1, zy, zz + 1, zx + 1, zy + 2, zz + 1, "minecraft:stone");
        fill(zx - 1, zy, zz, zx - 1, zy + 2, zz, "minecraft:stone");
        fill(zx + 1, zy, zz, zx + 1, zy + 2, zz, "minecraft:stone");
        fill(zx - 1, zy + 2, zz - 1, zx + 1, zy + 2, zz + 1, "minecraft:stone");
        set(zx, zy + 1, zz + 1, "minecraft:glass");   // 对村民那面留窗
        set(zx, zy, zz, "minecraft:rail");
        summon("minecraft:minecart", zx, zy, zz, null);
        add(String.format(
            "/execute as @e[type=minecraft:minecart,limit=1,sort=nearest,x=%d,y=%d,z=%d] run summon minecraft:zombie ~ ~ ~ {PersistenceRequired:1b,Silent:1b}",
            zx, zy, zz));

        // ---- 9. 水流集中：平台表面从四周流向中心附近的井口 ----
        // 井口在平台东缘 (bx+5, bz+5)，水流从西/北/南三边推向井口
        for (int i = -6; i <= 4; i++) {
            set(bx + i, by, bz + 6, "minecraft:water");     // 南边水流向北
            set(bx + i, by, bz - 6, "minecraft:water");     // 北边水流向南
        }
        for (int j = -6; j <= 6; j++) {
            set(bx - 6, by, bz + j, "minecraft:water");     // 西边水流向东
        }

        // ---- 10. 收集井：3x3 石井，深 20，位于 (bx+5, bz+5) ----
        int hx = bx + 5, hz = bz + 5;
        fill(hx - 1, by - 1, hz - 1, hx + 1, by, hz + 1, "minecraft:air");    // 井口
        fill(hx - 1, by - 21, hz - 1, hx + 1, by - 1, hz + 1, "minecraft:stone"); // 实心柱
        fill(hx - 1, by - 21, hz - 1, hx + 1, by - 21, hz + 1, "minecraft:stone");
        // 挖出井腔
        fill(hx - 1, by - 20, hz - 1, hx + 1, by - 2, hz + 1, "minecraft:air");
        // 井底水层（溺死处死，不烧掉落物）
        fill(hx - 1, by - 20, hz - 1, hx + 1, by - 20, hz + 1, "minecraft:water");

        // ---- 11. 收集：水下漏斗不可行 → 水下一格漏斗 + 箱子 ----
        // 漏斗能从上方水中的掉落物吸（漏斗吸 1 格范围内掉落物，包括隔水）
        fill(hx - 1, by - 21, hz - 1, hx + 1, by - 21, hz + 1, "minecraft:hopper");
        set(hx, by - 22, hz, "minecraft:chest");
        // 三个漏斗都指向中央箱子：设置朝向
        set(hx - 1, by - 21, hz, "minecraft:hopper[facing=east]");
        set(hx + 1, by - 21, hz, "minecraft:hopper[facing=west]");
        set(hx, by - 21, hz - 1, "minecraft:hopper[facing=south]");
        set(hx, by - 21, hz + 1, "minecraft:hopper[facing=north]");
        set(hx, by - 21, hz, "minecraft:hopper[facing=down]");

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 刷铁机v2 指令入队完毕（" + QUEUE.size()
                        + " 条，约 1 分钟建完）。铁锭在井底箱子里！"), false);
    }

    // =========================================================
    //  刷怪塔 v2（wiki 修正版：岩浆块处死 + 水流集中，不摔落）
    // =========================================================

    /**
     * 布局（bx,by,bz = 玩家脚下方块）：
     *   生成层  y+20：9x9 石平台，四周水槽推向中心洞口
     *   洞口    中心 1x1 → 怪落入竖井
     *   处死层  y+2：岩浆块地面上怪物持续受灼烧死亡
     *              （岩浆块不烧掉落物）
     *   收集层  y+1：漏斗 3x3 → 中央箱子
     *   挂机点  塔旁 24+ 格外站高处（玩家自己选）
     */
    public static void mobTower(MinecraftClient mc) {
        if (mc.player == null) return;
        int bx = (int) Math.floor(mc.player.getX());
        int by = (int) Math.floor(mc.player.getY());
        int bz = (int) Math.floor(mc.player.getZ());

        begin("刷怪塔v2");

        int topY = by + 20;   // 生成层地板 y

        // ---- 1. 清空竖直区 ----
        fill(bx - 6, by, bz - 6, bx + 6, topY + 8, bz + 6, "minecraft:air");

        // ---- 2. 收集层：漏斗 3x3 + 中央箱子 ----
        fill(bx - 1, by, bz - 1, bx + 1, by, bz + 1, "minecraft:hopper");
        set(bx, by - 1, bz, "minecraft:chest");
        set(bx - 1, by, bz, "minecraft:hopper[facing=east]");
        set(bx + 1, by, bz, "minecraft:hopper[facing=west]");
        set(bx, by, bz - 1, "minecraft:hopper[facing=south]");
        set(bx, by, bz + 1, "minecraft:hopper[facing=north]");
        set(bx, by, bz, "minecraft:hopper[facing=down]");

        // ---- 3. 处死层：岩浆块 3x3 地板（对生物持续伤害，不烧掉落物）----
        // 岩浆块每秒扣 1 血，怪会残血但不立刻死 → 玩家可补刀收经验
        fill(bx - 1, by + 1, bz - 1, bx + 1, by + 1, bz + 1, "minecraft:magma_block");

        // ---- 3.5 修脚位：南墙开 1x2 洞（玩家站在洞里补刀收经验）----
        // 玩家双脚在处死室内踩岩浆块外侧的石头台阶上，身体在墙外
        // 怪打不到玩家身体，玩家挥剑够得着怪 → 安全收经验
        fill(bx, by + 1, bz + 2, bx, by + 2, bz + 2, "minecraft:air");   // 南墙开洞
        set(bx, by + 1, bz + 3, "minecraft:stone");                       // 玩家站立台阶（墙外）
        fill(bx, by + 2, bz + 3, bx, by + 3, bz + 3, "minecraft:air");    // 玩家头部空间

        // ---- 4. 处死室围墙（石头，1 格高 + 顶留空让怪掉进来）----
        fill(bx - 2, by + 2, bz - 2, bx + 2, by + 3, bz + 2, "minecraft:stone");
        fill(bx - 1, by + 2, bz - 1, bx + 1, by + 3, bz + 1, "minecraft:air");
        // 中心竖井口（收集层到生成层之间全通）
        fill(bx, by + 1, bz, bx, topY - 1, bz, "minecraft:air");

        // ---- 5. 生成层：9x9 平台 + 2 高空间 + 石墙 + 屋顶玻璃 ----
        fill(bx - 4, topY, bz - 4, bx + 4, topY, bz + 4, "minecraft:stone");
        fill(bx - 4, topY + 1, bz - 4, bx + 4, topY + 2, bz + 4, "minecraft:air");
        fill(bx - 5, topY + 1, bz - 5, bx + 5, topY + 2, bz + 5, "minecraft:stone");
        fill(bx - 4, topY + 1, bz - 4, bx + 4, topY + 2, bz + 4, "minecraft:air");
        fill(bx - 5, topY + 3, bz - 5, bx + 5, topY + 3, bz + 5, "minecraft:glass");  // 玻璃顶：无光 + 不能生成

        // ---- 6. 生成层中心洞口（1x1，接竖井）----
        set(bx, topY, bz, "minecraft:air");

        // ---- 7. 生成层水槽：四周水推向中心 ----
        for (int i = -4; i <= 4; i++) {
            if (Math.abs(i) <= 1) continue;   // 中心附近留给井口
            set(bx + i, topY + 1, bz - 4, "minecraft:water");
            set(bx + i, topY + 1, bz + 4, "minecraft:water");
            set(bx - 4, topY + 1, bz + i, "minecraft:water");
            set(bx + 4, topY + 1, bz + i, "minecraft:water");
        }

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 刷怪塔v2 指令入队完毕（" + QUEUE.size()
                        + " 条）。挂机点：离塔 24 格外，塔必须悬空！"), false);
    }

    // =========================================================
    //  刷石机
    // =========================================================

    public static void cobbleGen(MinecraftClient mc) {
        if (mc.player == null) return;
        int bx = (int) Math.floor(mc.player.getX());
        int by = (int) Math.floor(mc.player.getY());
        int bz = (int) Math.floor(mc.player.getZ());

        begin("刷石机");

        fill(bx - 2, by - 1, bz - 1, bx + 2, by - 1, bz + 1, "minecraft:stone");
        set(bx - 1, by, bz - 1, "minecraft:water");
        set(bx + 1, by, bz - 1, "minecraft:lava");
        set(bx, by, bz - 1, "minecraft:cobblestone");
        set(bx, by, bz, "minecraft:piston[facing=north]");
        set(bx, by, bz + 1, "minecraft:hopper[facing=down]");
        set(bx, by - 1, bz + 1, "minecraft:chest");

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 刷石机指令入队完毕。站在活塞后手动激活即可循环刷圆石。"), false);
    }

    // =========================================================
    //  小木屋
    // =========================================================

    public static void cabin(MinecraftClient mc) {
        if (mc.player == null) return;
        int bx = (int) Math.floor(mc.player.getX());
        int by = (int) Math.floor(mc.player.getY());
        int bz = (int) Math.floor(mc.player.getZ());

        begin("小木屋");

        fill(bx - 4, by - 1, bz - 4, bx + 4, by - 1, bz + 4, "minecraft:oak_planks");
        fill(bx - 4, by, bz - 4, bx + 4, by + 3, bz + 4, "minecraft:air");
        fill(bx - 4, by, bz - 4, bx + 4, by + 3, bz - 4, "minecraft:oak_log");
        fill(bx - 4, by, bz + 4, bx + 4, by + 3, bz + 4, "minecraft:oak_log");
        fill(bx - 4, by, bz - 4, bx - 4, by + 3, bz + 4, "minecraft:oak_log");
        fill(bx + 4, by, bz - 4, bx + 4, by + 3, bz + 4, "minecraft:oak_log");
        fill(bx - 4, by + 4, bz - 4, bx + 4, by + 4, bz + 4, "minecraft:oak_planks");
        set(bx, by, bz + 4, "minecraft:oak_door[facing=north,half=lower]");
        set(bx, by + 1, bz + 4, "minecraft:oak_door[facing=north,half=upper]");
        set(bx - 3, by, bz - 3, "minecraft:red_bed[part=head,facing=south]");
        set(bx - 2, by, bz - 3, "minecraft:red_bed[part=foot,facing=south]");
        set(bx + 3, by, bz - 3, "minecraft:crafting_table");
        set(bx + 3, by, bz - 2, "minecraft:furnace[facing=south]");
        set(bx + 3, by, bz - 1, "minecraft:chest[facing=south]");
        set(bx, by + 2, bz - 4, "minecraft:glass");  // 采光窗

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 小木屋指令入队完毕。"), false);
    }
}