package com.ghost.cheatmenu.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构生成器：把一堆指令排进队列，每 tick 只发几条，
 * 避免瞬间发几百条指令被服务端踢。
 *
 * 所有指令都用相对玩家脚下坐标生成。
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

    public static int remaining() {
        return QUEUE.size();
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
    //  刷铁机
    // =========================================================

    /**
     * 刷铁机（僵尸恐吓 + 熔岩处死 + 漏斗回收）
     *
     * 布局说明（bx,by,bz = 玩家脚下）：
     *   - 9x9 石台，y = by-1
     *   - 中心 3x3 村民仓（玻璃围栏），y = by .. by+2
     *   - 村民仓北侧 (bz-3) 一个 1x2x1 僵尸笼，村民透玻璃可见 → panic
     *   - 台面四角放水源，水往中心流；中心下方是收集井
     *   - 铁傀儡在台面生成后被水推向中心 → 掉进井里
     *   - 井底 y = by-20 熔岩，熔岩下漏斗 + 箱子
     */
    public static void ironFarm(MinecraftClient mc) {
        if (mc.player == null) return;
        int bx = (int) Math.floor(mc.player.getX());
        int by = (int) Math.floor(mc.player.getY());
        int bz = (int) Math.floor(mc.player.getZ());

        begin("刷铁机");

        // ---- 1. 清空工作区（避免埋进山体）----
        fill(bx - 5, by, bz - 5, bx + 5, by + 6, bz + 5, "minecraft:air");

        // ---- 2. 石台地板 9x9 ----
        fill(bx - 4, by - 1, bz - 4, bx + 4, by - 1, bz + 4, "minecraft:stone");

        // ---- 3. 中心村民仓 3x3x3 玻璃围栏 ----
        // 先清内部
        fill(bx - 1, by, bz - 1, bx + 1, by + 2, bz + 1, "minecraft:air");
        // 四面玻璃墙（留出入门先不管，直接封死靠 summon 塞进去）
        fill(bx - 2, by, bz - 2, bx - 2, by + 2, bz + 2, "minecraft:glass");   // 西
        fill(bx + 2, by, bz - 2, bx + 2, by + 2, bz + 2, "minecraft:glass");   // 东
        fill(bx - 2, by, bz - 2, bx + 2, by + 2, bz - 2, "minecraft:glass");   // 北
        fill(bx - 2, by, bz + 2, bx + 2, by + 2, bz + 2, "minecraft:glass");   // 南
        // 顶盖
        fill(bx - 2, by + 3, bz - 2, bx + 2, by + 3, bz + 2, "minecraft:stone");

        // ---- 4. 村民仓内部：3 张床 + 3 个工作台 ----
        set(bx - 1, by, bz - 1, "minecraft:red_bed[part=head,facing=east]");
        set(bx, by, bz - 1, "minecraft:red_bed[part=foot,facing=east]");
        set(bx + 1, by, bz - 1, "minecraft:smithing_table");
        set(bx - 1, by, bz + 1, "minecraft:red_bed[part=head,facing=west]");
        set(bx, by, bz + 1, "minecraft:red_bed[part=foot,facing=west]");
        set(bx + 1, by, bz + 1, "minecraft:smithing_table");

        // ---- 5. 三只村民（农夫/工具匠，能提供工作站条件）----
        String vFarmer = "{PersistenceRequired:1b,CustomName:\"\\\"\\u53d8\\u5f02\\u6751\\u6c11\\\"\",VillagerData:{profession:\"minecraft:farmer\",level:2,type:\"minecraft:plains\"}}";
        summon("minecraft:villager", bx, by, bz, vFarmer);
        summon("minecraft:villager", bx, by, bz - 1, null);
        summon("minecraft:villager", bx, by, bz + 1, null);

        // ---- 6. 僵尸笼（北侧，村民透玻璃能看到）----
        fill(bx, by, bz - 4, bx, by + 1, bz - 4, "minecraft:air");
        set(bx, by - 1, bz - 4, "minecraft:stone");
        fill(bx, by, bz - 5, bx, by + 1, bz - 5, "minecraft:glass");
        fill(bx + 1, by, bz - 4, bx + 1, by + 1, bz - 4, "minecraft:glass");
        fill(bx - 1, by, bz - 4, bx - 1, by + 1, bz - 4, "minecraft:glass");
        set(bx, by + 2, bz - 4, "minecraft:glass");
        summon("minecraft:zombie", bx, by, bz - 4,
                "{PersistenceRequired:1b,Silent:1b,IsBaby:0b}");

        // ---- 7. 台面水源（四边各 3 个，朝中心流）----
        for (int i = -3; i <= 3; i += 3) {
            set(bx + i, by, bz - 4, "minecraft:water");
            set(bx + i, by, bz + 4, "minecraft:water");
            set(bx - 4, by, bz + i, "minecraft:water");
            set(bx + 4, by, bz + i, "minecraft:water");
        }

        // ---- 8. 收集井（从台面中心东侧往下 20 格）----
        int hx = bx + 3, hz = bz;
        fill(hx, by - 1, hz, hx, by, hz, "minecraft:air");            // 井口
        fill(hx - 1, by - 20, hz, hx + 1, by - 20, hz, "minecraft:stone"); // 井底
        // 井壁
        fill(hx - 1, by - 20, hz, hx - 1, by, hz, "minecraft:stone");
        fill(hx + 1, by - 20, hz, hx + 1, by, hz, "minecraft:stone");
        fill(hx, by - 20, hz - 1, hx, by, hz - 1, "minecraft:stone");
        fill(hx, by - 20, hz + 1, hx, by, hz + 1, "minecraft:stone");
        fill(hx, by - 20, hz, hx, by, hz, "minecraft:air");

        // ---- 9. 井底处死层：熔岩 + 漏斗 + 箱子 ----
        set(hx, by - 19, hz, "minecraft:lava");
        set(hx, by - 20, hz, "minecraft:hopper[facing=down]");
        set(hx, by - 21, hz, "minecraft:chest");
        // 熔岩上方加一层告示牌挡一下（可选，防掉落物被烧光前弹走）

        // ---- 10. 台面封边（防铁傀儡跑掉）----
        fill(bx - 4, by, bz - 4, bx + 4, by + 1, bz - 4, "minecraft:glass");
        fill(bx - 4, by, bz + 4, bx + 4, by + 1, bz + 4, "minecraft:glass");
        fill(bx - 4, by, bz - 4, bx - 4, by + 1, bz + 4, "minecraft:glass");
        fill(bx + 4, by, bz - 4, bx + 4, by + 1, bz + 4, "minecraft:glass");

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 正在生成刷铁机，共 " + QUEUE.size() + " 条指令，请稍等…"), false);
    }

    // =========================================================
    //  刷怪塔
    // =========================================================

    /**
     * 刷怪塔：高 24 格的黑暗房，水流把怪推到中心，掉落摔死，漏斗收集。
     */
    public static void mobTower(MinecraftClient mc) {
        if (mc.player == null) return;
        int bx = (int) Math.floor(mc.player.getX());
        int by = (int) Math.floor(mc.player.getY());
        int bz = (int) Math.floor(mc.player.getZ());

        begin("刷怪塔");

        // 地面收集
        set(bx, by, bz, "minecraft:hopper[facing=down]");
        set(bx, by - 1, bz, "minecraft:chest");
        fill(bx - 1, by, bz - 1, bx + 1, by, bz + 1, "minecraft:hopper");

        // 掉落井（3x3 x 24 高）
        fill(bx - 1, by + 1, bz - 1, bx + 1, by + 23, bz + 1, "minecraft:air");
        fill(bx - 2, by + 1, bz - 2, bx - 2, by + 23, bz + 2, "minecraft:stone");
        fill(bx + 2, by + 1, bz - 2, bx + 2, by + 23, bz + 2, "minecraft:stone");
        fill(bx - 2, by + 1, bz - 2, bx + 2, by + 23, bz - 2, "minecraft:stone");
        fill(bx - 2, by + 1, bz + 2, bx + 2, by + 23, bz + 2, "minecraft:stone");

        // 生成室：11x11，两层高
        fill(bx - 5, by + 24, bz - 5, bx + 5, by + 25, bz + 5, "minecraft:air");
        fill(bx - 6, by + 24, bz - 6, bx + 6, by + 26, bz + 6, "minecraft:stone");
        fill(bx - 5, by + 24, bz - 5, bx + 5, by + 25, bz + 5, "minecraft:air");

        // 屋顶封死
        fill(bx - 6, by + 27, bz - 6, bx + 6, by + 27, bz + 6, "minecraft:stone");

        // 水流：从四边往中心
        fill(bx - 5, by + 25, bz - 5, bx + 5, by + 25, bz - 5, "minecraft:water");
        fill(bx - 5, by + 25, bz + 5, bx + 5, by + 25, bz + 5, "minecraft:water");
        fill(bx - 5, by + 25, bz - 5, bx - 5, by + 25, bz + 5, "minecraft:water");
        fill(bx + 5, by + 25, bz - 5, bx + 5, by + 25, bz + 5, "minecraft:water");

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 正在生成刷怪塔…"), false);
    }

    // =========================================================
    //  刷石机
    // =========================================================

    /** 刷石机：熔岩 + 水 + 活塞自动推 */
    public static void cobbleGen(MinecraftClient mc) {
        if (mc.player == null) return;
        int bx = (int) Math.floor(mc.player.getX());
        int by = (int) Math.floor(mc.player.getY());
        int bz = (int) Math.floor(mc.player.getZ());

        begin("刷石机");

        fill(bx - 2, by - 1, bz - 1, bx + 2, by - 1, bz + 1, "minecraft:stone");
        // 水槽
        set(bx - 1, by, bz - 1, "minecraft:water");
        // 熔岩槽
        set(bx + 1, by, bz - 1, "minecraft:lava");
        // 中间产物位
        set(bx, by, bz - 1, "minecraft:cobblestone");
        // 活塞
        set(bx, by, bz, "minecraft:piston[facing=north]");
        // 收集
        set(bx, by, bz + 1, "minecraft:hopper[facing=down]");
        set(bx, by - 1, bz + 1, "minecraft:chest");

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 正在生成刷石机…"), false);
    }

    // =========================================================
    //  小木屋
    // =========================================================

    /** 9x9 小木屋，带床、工作台、熔炉、箱子 */
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
        fill(bx - 5, by + 4, bz - 5, bx + 5, by + 4, bz + 5, "minecraft:oak_stairs");

        set(bx, by, bz + 4, "minecraft:oak_door[facing=north]");
        set(bx, by + 1, bz + 4, "minecraft:oak_door[facing=north,half=upper]");

        set(bx - 3, by, bz - 3, "minecraft:red_bed[part=head,facing=south]");
        set(bx - 2, by, bz - 3, "minecraft:red_bed[part=foot,facing=south]");
        set(bx + 3, by, bz - 3, "minecraft:crafting_table");
        set(bx + 3, by, bz - 2, "minecraft:furnace[facing=south]");
        set(bx + 3, by, bz - 1, "minecraft:chest[facing=south]");

        mc.player.sendMessage(new net.minecraft.text.LiteralText(
                "\u00a7e[GhostMenu] 正在生成小木屋…"), false);
    }
}