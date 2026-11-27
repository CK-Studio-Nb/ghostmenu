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
    public static final Toggle NO_FALL = new Toggle("无摔落");
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
            new Action("清屏聊天", Cheats::clearChat),
            new Action("视角归零", Cheats::resetLook),
            new Action("原地自杀", Cheats::suicide),
            new Action("一键全关", Cheats::allOff),
            new Action("换白天", () -> sendCmd("/time set day")),
            new Action("换黑夜", () -> sendCmd("/time set midnight")),
            new Action("天气放晴", () -> sendCmd("/weather clear")),
            new Action("大声认罪", () -> say("我是挂狗 报警吧"))
    };

    private static final Random RAND = new Random();
    private static final String[] SPAM_LINES = {
            "喵～", "我是一只猫娘~", "114514", "这个服好卡喵",
            "我开挂了 你报警吧", "(=^･ω･^=)", "1919810"
    };

    private static int spamTimer = 0;
    private static boolean flyApplied = false;
    private static boolean speedApplied = false;
    private static boolean gammaSaved = false;
    private static double savedGamma = 1.0;
    private static boolean noclipApplied = false;

    public static void tick(MinecraftClient mc) {
        if (mc.player == null) return;

        PlayerAbilities ab = mc.player.abilities;

        if (FLY.on) {
            ab.allowFlying = true;
            ab.flying = true;
            ab.setFlySpeed(0.5f);
            mc.player.sendAbilitiesUpdate();
            flyApplied = true;
        } else if (flyApplied) {
            ab.allowFlying = false;
            ab.flying = false;
            ab.setFlySpeed(0.05f);
            mc.player.sendAbilitiesUpdate();
            flyApplied = false;
        }

        if (SPEED.on) {
            ab.setWalkSpeed(0.4f);
            mc.player.sendAbilitiesUpdate();
            speedApplied = true;
        } else if (speedApplied) {
            ab.setWalkSpeed(0.1f);
            mc.player.sendAbilitiesUpdate();
            speedApplied = false;
        }

        if (NO_FALL.on && mc.player.fallDistance > 1.5f && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionOnly(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(), true));
            mc.player.fallDistance = 0f;
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

        if (SPIN.on) {
            mc.player.yaw += 20f;
        }

        if (JITTER.on) {
            mc.player.yaw += (RAND.nextFloat() - 0.5f) * 15f;
            mc.player.pitch += (RAND.nextFloat() - 0.5f) * 15f;
        }

        if (FLIP_PITCH.on) {
            mc.player.pitch = -mc.player.pitch;
        }

        if (CHAT_SPAM.on) {
            spamTimer++;
            if (spamTimer >= 40 && mc.getNetworkHandler() != null) {
                spamTimer = 0;
                mc.getNetworkHandler().sendPacket(new ChatMessageC2SPacket(
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
        String s = String.format("\u00a7b坐标 \u00a7fX=%.1f Y=%.1f Z=%.1f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ());
        mc.player.sendMessage(new LiteralText(s), false);
    }

    public static void clearChat() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) mc.inGameHud.getChatHud().clear(true);
    }

    public static void resetLook() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.yaw = 0f;
        mc.player.pitch = 0f;
    }

    public static void suicide() {
        sendCmd("/kill");
    }

    private static void sendCmd(String cmd) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().sendPacket(new ChatMessageC2SPacket(cmd));
    }

    private static void say(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.sendChatMessage(msg);
    }

    public static void allOff() {
        for (Toggle t : TOGGLES) t.on = false;
    }
}