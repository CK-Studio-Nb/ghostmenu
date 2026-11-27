package com.ghost.cheatmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.Vec3d;

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

    public static final Toggle FLY = new Toggle("飞行");
    public static final Toggle SPEED = new Toggle("加速");
    public static final Toggle NO_FALL = new Toggle("无摔落伤害");
    public static final Toggle FULL_BRIGHT = new Toggle("夜视");
    public static final Toggle NO_CLIP = new Toggle("穿墙");
    public static final Toggle SPIDER = new Toggle("蜘蛛爬墙");
    public static final Toggle AUTO_RESPAWN = new Toggle("自动复活");
    public static final Toggle BUNNY_HOP = new Toggle("自动连跳");
    public static final Toggle SPIN = new Toggle("原地转圈");
    public static final Toggle JITTER = new Toggle("抽搐瞄准");
    public static final Toggle FLIP_PITCH = new Toggle("倒立视角");
    public static final Toggle CHAT_SPAM = new Toggle("喵喵刷屏");

    public static final Toggle[] TOGGLES = {
            FLY, SPEED, NO_FALL, FULL_BRIGHT, NO_CLIP, SPIDER,
            AUTO_RESPAWN, BUNNY_HOP, SPIN, JITTER, FLIP_PITCH, CHAT_SPAM
    };

    public static final Action[] ACTIONS = {
            new Action("卡墙上升 +5", () -> vclip(5)),
            new Action("卡墙下降 -5", () -> vclip(-5)),
            new Action("发坐标", Cheats::sendCoords),
            new Action("清屏聊天", () -> {
                if (MinecraftClient.getInstance().inGameHud != null)
                    MinecraftClient.getInstance().inGameHud.getChatHud().clear(true);
            }),
            new Action("视角归零", () -> {
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.yaw = 0f;
                    MinecraftClient.getInstance().player.pitch = 0f;
                }
            }),
            new Action("原地自杀", () -> {
                if (MinecraftClient.getInstance().player != null)
                    MinecraftClient.getInstance().player.sendChatMessage("/kill");
            }),
            new Action("一秒关门(全关)", Cheats::allOff)
    };

    private static final Random RAND = new Random();
    private static final String[] SPAM_LINES = {
            "喵～", "我是一只猫娘~", "主人让我刷屏qwq", "114514", "这个服好卡喵",
            "我开挂了 你报警吧", "(=^･ω･^=)"
    };

    private static int spamTimer = 0;
    private static boolean flyApplied = false;
    private static boolean speedApplied = false;
    private static boolean gammaSaved = false;
    private static double savedGamma = 1.0;
    private static boolean noclipApplied = false;

    public static void tick(MinecraftClient mc) {
        if (mc.player == null) return;

        // 飞行
        if (FLY.on) {
            PlayerAbilities a = mc.player.getAbilities();
            a.allowFlying = true;
            a.flying = true;
            a.setFlySpeed(0.5f);
            mc.player.sendAbilitiesUpdate();
            flyApplied = true;
        } else if (flyApplied) {
            PlayerAbilities a = mc.player.getAbilities();
            a.allowFlying = false;
            a.flying = false;
            a.setFlySpeed(0.05f);
            mc.player.sendAbilitiesUpdate();
            flyApplied = false;
        }

        // 加速
        if (SPEED.on) {
            mc.player.getAbilities().setWalkSpeed(0.6f);
            speedApplied = true;
        } else if (speedApplied) {
            mc.player.getAbilities().setWalkSpeed(0.1f);
            speedApplied = false;
        }

        // 无摔落
        if (NO_FALL.on && mc.player.fallDistance > 2.0f && mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
            mc.player.fallDistance = 0f;
        }

        // 夜视
        if (FULL_BRIGHT.on) {
            if (!gammaSaved) { savedGamma = mc.options.gamma; gammaSaved = true; }
            mc.options.gamma = 10.0;
        } else if (gammaSaved) {
            mc.options.gamma = savedGamma;
            gammaSaved = false;
        }

        // 穿墙
        if (NO_CLIP.on) {
            mc.player.noClip = true;
            noclipApplied = true;
        } else if (noclipApplied) {
            mc.player.noClip = false;
            noclipApplied = false;
        }

        // 蜘蛛爬墙
        if (SPIDER.on && mc.player.horizontalCollision) {
            Vec3d v = mc.player.getVelocity();
            mc.player.setVelocity(v.x, 0.25, v.z);
        }

        // 自动连跳
        if (BUNNY_HOP.on && mc.player.isOnGround()) {
            Vec3d v = mc.player.getVelocity();
            mc.player.setVelocity(v.x, 0.42, v.z);
        }

        // 自动复活
        if (AUTO_RESPAWN.on && !mc.player.isAlive()) {
            mc.player.requestRespawn();
        }

        // 转圈
        if (SPIN.on) {
            mc.player.yaw += 20f;
        }

        // 抽搐瞄准
        if (JITTER.on) {
            mc.player.yaw += (RAND.nextFloat() - 0.5f) * 15f;
            mc.player.pitch += (RAND.nextFloat() - 0.5f) * 15f;
        }

        // 倒立视角
        if (FLIP_PITCH.on) {
            mc.player.pitch = -mc.player.pitch;
        }

        // 喵喵刷屏
        if (CHAT_SPAM.on) {
            spamTimer++;
            if (spamTimer >= 40 && mc.player.networkHandler != null) {
                spamTimer = 0;
                mc.player.networkHandler.sendPacket(new ChatMessageC2SPacket(
                        SPAM_LINES[RAND.nextInt(SPAM_LINES.length)]));
            }
        } else {
            spamTimer = 0;
        }
    }

    public static void vclip(double dy) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.setPosition(mc.player.getX(), mc.player.getY() + dy, mc.player.getZ());
        mc.player.setVelocity(0, 0, 0);
    }

    public static void sendCoords() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        String s = String.format("我在 X=%.1f Y=%.1f Z=%.1f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ());
        mc.player.sendMessage(new LiteralText(s), false);
    }

    public static void allOff() {
        for (Toggle t : TOGGLES) t.on = false;
    }
}
