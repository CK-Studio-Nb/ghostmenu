package com.ghost.cheatmenu;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Cheats {

    public static class Toggle {
        public final String name;
        public boolean on;
        public Toggle(String name) { this.name = name; }
    }

    public static class Action {
        public final String name;
        public final Runnable run;
        public Action(String name, Runnable run) { this.name = name; this.run = run; }
    }

    // ===== 移动类 =====
    public static final Toggle FLY = new Toggle("飞行");
    public static final Toggle SPEED = new Toggle("加速");
    public static final Toggle NO_FALL = new Toggle("无摔落");
    public static final Toggle NO_CLIP = new Toggle("穿墙");
    public static final Toggle SPIDER = new Toggle("爬墙");
    public static final Toggle BUNNY_HOP = new Toggle("自动连跳");
    public static final Toggle AUTO_RESPAWN = new Toggle("自动复活");
    public static final Toggle FULL_BRIGHT = new Toggle("夜视");

    // ===== 透视/ESP =====
    public static final Toggle XRAY = new Toggle("真透视X-Ray");
    public static final Toggle ESP_PLAYER = new Toggle("透视-玩家");
    public static final Toggle ESP_MOB = new Toggle("透视-怪物");
    public static final Toggle LASER = new Toggle("激光射线(视觉)");
    public static final Toggle LASER_ATTACK = new Toggle("激光攻击(按住右键)");

    // ===== 攻击性 =====
    public static final Toggle KILL_AURA = new Toggle("杀戮光环");
    public static final Toggle INFINITE_CHARGE = new Toggle("无尽蓄力(满攻速)");
    public static final Toggle REACH = new Toggle("长手(攻击距离)");

    // ===== 合成/图鉴 =====
    public static final Toggle UNLOCK_RECIPES = new Toggle("解锁全部图鉴");

    // ===== 抽象/搞事 =====
    public static final Toggle SPIN = new Toggle("原地转圈");
    public static final Toggle JITTER = new Toggle("抽搐瞄准");
    public static final Toggle CHAT_SPAM = new Toggle("喵喵刷屏");

    public static final Toggle[] TOGGLES = {
            SPEED, FLY, NO_FALL, NO_CLIP, SPIDER, BUNNY_HOP, AUTO_RESPAWN, FULL_BRIGHT,
            ESP_PLAYER, ESP_MOB, LASER, LASER_ATTACK, XRAY, KILL_AURA, INFINITE_CHARGE,
            UNLOCK_RECIPES,
            SPIN, JITTER, CHAT_SPAM
    };

    public static final Action[] ACTIONS = {
            new Action("杀戮光环 全清一次", Cheats::killAuraOnce),
            new Action("刷 10 只僵尸", () -> spawnMobs("zombie", 10)),
            new Action("刷 5 只苦力怕", () -> spawnMobs("creeper", 5)),
            new Action("刷 3 只末影人", () -> spawnMobs("enderman", 3)),
            new Action("刷 10 个钻石块", () -> giveItem("diamond_block", 10)),
            new Action("刷 64 个金苹果", () -> giveItem("golden_apple", 64)),
            new Action("刷 64 个 TNT", () -> giveItem("tnt", 64)),
            new Action("卡墙上升 +5", () -> vclip(5)),
            new Action("卡墙下降 -5", () -> vclip(-5)),
            new Action("发坐标", Cheats::sendCoords),
            new Action("视角归零", Cheats::resetLook),
            new Action("一键全关", Cheats::allOff)
    };

    private static final Random RAND = new Random();
    private static final String[] SPAM_LINES = {
            "喵～", "114514", "我开挂了 报警吧", "这个服好卡喵", "(=^･ω･^=)"
    };

    private static int spamTimer = 0;
    private static boolean flyApplied = false;
    private static boolean speedApplied = false;
    private static boolean gammaSaved = false;
    private static double savedGamma = 1.0;
    private static boolean noclipApplied = false;

    /** 「囍」触发的待打开标记：由 tick 在下一帧打开菜单 */
    private static volatile boolean pendingOpenMenu = false;
    private static int pendingDelay = 0;

    /** 追踪 player 实例：死亡重生后是新对象，必须重置所有已应用标记 */
    private static Object lastPlayerRef = null;

    public static void requestOpenMenu() {
        pendingOpenMenu = true;
        pendingDelay = 3;   // 等几 tick，让 ChatScreen 先把界面关掉
    }

    /** 死亡/重生/换维度时调用：把"已应用"状态全部清空，让开关能重新生效 */
    private static void resetAppliedState() {
        flyApplied = false;
        speedApplied = false;
        noclipApplied = false;
        gammaSaved = false;
        savedGamma = 1.0;
        spamTimer = 0;
    }

    // ================= tick =================

    public static void tick(MinecraftClient mc) {
        // ===== 处理「囍」的待打开菜单（必须在 player==null 提前返回之前）=====
        if (pendingOpenMenu) {
            if (pendingDelay > 0) {
                pendingDelay--;
            } else if (mc.currentScreen == null) {
                // 聊天界面已经关掉，这时开菜单才不会被顶掉
                pendingOpenMenu = false;
                mc.openScreen(new com.ghost.cheatmenu.gui.CheatScreen());
            }
        }

        if (mc.player == null) return;

        // 检测 player 实例变化（死亡重生 / 换维度）→ 清空已应用状态
        if (lastPlayerRef != mc.player) {
            lastPlayerRef = mc.player;
            resetAppliedState();
        }

        PlayerAbilities ab = mc.player.abilities;

        // ---------- 无摔落 ----------
        // 关键：飞行状态下绝对不能发 onGround=true 的假包！
        // 否则服务端看到"站在地上但坐标在下降"，会判定异常位移 → 反复拉回 → 卡方块窒息。
        boolean flying = ab.flying || ab.allowFlying;
        if (NO_FALL.on && !flying) {
            if (mc.player.fallDistance > 0f) {
                mc.player.fallDistance = 0f;
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionOnly(
                            mc.player.getX(), mc.player.getY(), mc.player.getZ(), true));
                }
            }
        } else if (NO_FALL.on && flying) {
            // 飞行时只清本地计数，不发包
            mc.player.fallDistance = 0f;
        }

        if (FLY.on) {
            // 只在第一次开启时授予飞行权限，之后不再每 tick 强制 flying=true，
            // 否则按住蹲下键会被强行拉起，反而一直往下掉。
            if (!flyApplied) {
                ab.allowFlying = true;
                ab.setFlySpeed(0.5f);
                mc.player.sendAbilitiesUpdate();
                flyApplied = true;
            }
        } else if (flyApplied) {
            ab.allowFlying = false;
            ab.flying = false;
            ab.setFlySpeed(0.05f);
            mc.player.sendAbilitiesUpdate();
            flyApplied = false;
        }

        if (SPEED.on) {
            ab.setWalkSpeed(0.5f);
            mc.player.sendAbilitiesUpdate();
            speedApplied = true;
        } else if (speedApplied) {
            ab.setWalkSpeed(0.1f);
            mc.player.sendAbilitiesUpdate();
            speedApplied = false;
        }

        if (FULL_BRIGHT.on) {
            if (!gammaSaved) { savedGamma = mc.options.gamma; gammaSaved = true; }
            mc.options.gamma = 10.0;
        } else if (gammaSaved) {
            mc.options.gamma = savedGamma;
            gammaSaved = false;
        }

        if (NO_CLIP.on) {
            mc.player.noClip = true;
            noclipApplied = true;
        } else if (noclipApplied) {
            mc.player.noClip = false;
            noclipApplied = false;
        }

        if (SPIDER.on && mc.player.horizontalCollision) {
            Vec3d v = mc.player.getVelocity();
            mc.player.setVelocity(v.x, 0.25, v.z);
        }

        if (BUNNY_HOP.on && mc.player.isOnGround()) {
            Vec3d v = mc.player.getVelocity();
            mc.player.setVelocity(v.x, 0.42, v.z);
        }

        if (AUTO_RESPAWN.on && !mc.player.isAlive()) {
            mc.player.requestRespawn();
        }

        // 杀戮光环：4.5 格内最近的活体，不限类型，每 tick 打一个
        if (KILL_AURA.on) killAuraOnce();

        // 激光攻击：按住右键持续打（用 keyUse.isPressed 才能"按住"，wasRightButtonClicked 只在按下那一 tick 为 true）
        if (LASER_ATTACK.on && mc.options.keyUse.isPressed()) {
            laserHit();
        }

        if (SPIN.on) mc.player.yaw += 18f;

        if (JITTER.on) {
            mc.player.yaw += (RAND.nextFloat() - 0.5f) * 12f;
            mc.player.pitch += (RAND.nextFloat() - 0.5f) * 12f;
        }

        if (CHAT_SPAM.on) {
            spamTimer++;
            if (spamTimer >= 60 && mc.getNetworkHandler() != null) {
                spamTimer = 0;
                mc.getNetworkHandler().sendPacket(new ChatMessageC2SPacket(
                        SPAM_LINES[RAND.nextInt(SPAM_LINES.length)]));
            }
        } else spamTimer = 0;
    }

    // ================= 世界渲染：ESP + 激光 =================

    public static void renderWorld(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!ESP_PLAYER.on && !ESP_MOB.on && !LASER.on) return;

        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider consumers = ctx.consumers();
        if (ms == null || consumers == null) return;

        Vec3d cam = ctx.camera().getPos();
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLines());

        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);

        List<Entity> targets = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            if (!(e instanceof LivingEntity)) continue;

            boolean isPlayer = e instanceof PlayerEntity;
            if (isPlayer && !ESP_PLAYER.on) continue;
            if (!isPlayer && !ESP_MOB.on) continue;
            if (e.squaredDistanceTo(mc.player) > 128 * 128) continue;

            float r, g, b;
            if (isPlayer) { r = 1f; g = 0.2f; b = 0.2f; }
            else if (e instanceof HostileEntity) { r = 1f; g = 0.55f; b = 0f; }
            else { r = 0.3f; g = 1f; b = 0.4f; }

            Box box = e.getBoundingBox().expand(0.08);
            drawBoxLines(ms, vc, box, r, g, b, 1f);
            targets.add(e);
        }

        // 激光：从眼睛射向最近的几个目标
        if (LASER.on) {
            Vec3d eye = new Vec3d(mc.player.getX(), mc.player.getY() + mc.player.getStandingEyeHeight(), mc.player.getZ());
            int drawn = 0;
            for (Entity e : targets) {
                if (drawn >= 3) break;
                Box bb = e.getBoundingBox();
                Vec3d p = new Vec3d(bb.getCenter().x, bb.getCenter().y, bb.getCenter().z);
                line(ms, vc, eye, p, 0.1f, 1f, 1f, 0.9f);
                drawn++;
            }
        }

        ms.pop();
        ((VertexConsumerProvider.Immediate) consumers).draw(RenderLayer.getLines());
    }

    private static void drawBoxLines(MatrixStack ms, VertexConsumer vc, Box b,
                                     float r, float g, float bl, float a) {
        MatrixStack.Entry entry = ms.peek();
        float x1 = (float) b.minX, y1 = (float) b.minY, z1 = (float) b.minZ;
        float x2 = (float) b.maxX, y2 = (float) b.maxY, z2 = (float) b.maxZ;

        // 12 条棱
        edge(vc, entry, x1, y1, z1, x2, y1, z1, r, g, bl, a);
        edge(vc, entry, x2, y1, z1, x2, y1, z2, r, g, bl, a);
        edge(vc, entry, x2, y1, z2, x1, y1, z2, r, g, bl, a);
        edge(vc, entry, x1, y1, z2, x1, y1, z1, r, g, bl, a);

        edge(vc, entry, x1, y2, z1, x2, y2, z1, r, g, bl, a);
        edge(vc, entry, x2, y2, z1, x2, y2, z2, r, g, bl, a);
        edge(vc, entry, x2, y2, z2, x1, y2, z2, r, g, bl, a);
        edge(vc, entry, x1, y2, z2, x1, y2, z1, r, g, bl, a);

        edge(vc, entry, x1, y1, z1, x1, y2, z1, r, g, bl, a);
        edge(vc, entry, x2, y1, z1, x2, y2, z1, r, g, bl, a);
        edge(vc, entry, x2, y1, z2, x2, y2, z2, r, g, bl, a);
        edge(vc, entry, x1, y1, z2, x1, y2, z2, r, g, bl, a);
    }

    private static void line(MatrixStack ms, VertexConsumer vc, Vec3d a, Vec3d b,
                             float r, float g, float bl, float al) {
        edge(vc, ms.peek(),
                (float) a.x, (float) a.y, (float) a.z,
                (float) b.x, (float) b.y, (float) b.z, r, g, bl, al);
    }

    private static void edge(VertexConsumer vc, MatrixStack.Entry e,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        vc.vertex(e.getModel(), x1, y1, z1).color(r, g, b, a).next();
        vc.vertex(e.getModel(), x2, y2, z2).color(r, g, b, a).next();
    }

    // ================= 攻击 =================

    public static void killAuraOnce() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        Entity best = null;
        double bestDist = 4.5 * 4.5;

        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            if (!(e instanceof LivingEntity)) continue;
            if (!e.isAlive()) continue;
            double d = e.squaredDistanceTo(mc.player);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }

        if (best != null) {
            mc.interactionManager.attackEntity(mc.player, best);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }
    }

    /**
     * 激光攻击：从眼睛沿视线方向做自己算的射线（最远 64 格），
     * 命中最近的活体就打它。不依赖 crosshairTarget（那个只有 4.5 格）。
     */
    public static void laserHit() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        Vec3d eye = new Vec3d(mc.player.getX(),
                mc.player.getY() + mc.player.getStandingEyeHeight(),
                mc.player.getZ());
        Vec3d dir = mc.player.getRotationVec(1.0f).normalize();

        Entity best = null;
        double bestT = 5.5;    // 服务端硬校验：攻击距离超过约 6 格会被拒绝，取 5.5 稳妥

        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            if (!(e instanceof LivingEntity)) continue;
            if (!e.isAlive()) continue;

            Vec3d center = e.getBoundingBox().getCenter();
            Vec3d to = center.subtract(eye);
            double t = to.dotProduct(dir);          // 沿视线的投影距离
            if (t < 0 || t > 5.5) continue;

            Vec3d closest = eye.add(dir.multiply(t));
            double distToAxis = closest.distanceTo(center);
            double radius = (e.getBoundingBox().maxX - e.getBoundingBox().minX) * 0.55 + 1.2;

            if (distToAxis <= radius && t < bestT) {
                bestT = t;
                best = e;
            }
        }

        if (best != null) {
            mc.interactionManager.attackEntity(mc.player, best);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            lastLaserTarget = best;
        }
    }

    /** 供渲染用：激光打中的目标 */
    public static Entity lastLaserTarget = null;

    // ================= 刷东西（需要 OP/创造） =================

    public static void spawnMobs(String type, int count) {
        for (int i = 0; i < count; i++) {
            sendCmd("/summon " + type + " ~ ~ ~");
        }
    }

    public static void giveItem(String id, int count) {
        sendCmd("/give @s " + id + " " + count);
    }

    // ================= 工具 =================

    public static void vclip(double dy) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.setPosition(mc.player.getX(), mc.player.getY() + dy, mc.player.getZ());
        mc.player.setVelocity(0, 0, 0);
    }

    public static void sendCoords() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.sendMessage(new LiteralText(String.format(
                "\u00a7b坐标 \u00a7fX=%.1f Y=%.1f Z=%.1f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ())), false);
    }

    public static void resetLook() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.yaw = 0f;
        mc.player.pitch = 0f;
    }

    private static void sendCmd(String cmd) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().sendPacket(new ChatMessageC2SPacket(cmd));
    }

    public static void allOff() {
        for (Toggle t : TOGGLES) t.on = false;
    }
}