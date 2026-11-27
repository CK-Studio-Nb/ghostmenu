package com.ghost.cheatmenu.mixin;

import com.ghost.cheatmenu.Cheats;
import net.minecraft.inventory.Inventory;
import net.minecraft.recipe.Recipe;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 免材料合成：Recipe.matches(inv, world) 是判定"材料够不够"的唯一入口。
 *
 * 注意：Recipe 是 interface，Mixin 到 interface 的注入方法在 Java 8 下
 * 不能用 private（Java 9 才支持 private interface method），必须是 public。
 *
 * 服务端同理：单人存档/自建服里服务端也在同一进程，注入一样生效。
 */
@Mixin(Recipe.class)
public interface RecipeFreeMixin {

    @Inject(method = "matches(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/world/World;)Z",
            at = @At("HEAD"), cancellable = true)
    default void ghostmenu_freeCraft(Inventory inv, World world, CallbackInfoReturnable<Boolean> cir) {
        if (Cheats.FREE_CRAFT.on) {
            cir.setReturnValue(true);
        }
    }
}