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
 * 逼它恒 true → 客户端认为任何布局都满足配方。
 *
 * 注意：真正的合成是在服务端执行的。
 *   - 单人存档 / 你自己开服：服务端也在你的进程里，这个注入对服务端同样生效
 *   - 别人开的服务器：服务端有自己的判定，这里只能改客户端的预览/手册高亮，不会凭空造出物品
 */
@Mixin(Recipe.class)
public interface RecipeFreeMixin {

    @Inject(method = "matches(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/world/World;)Z",
            at = @At("HEAD"), cancellable = true)
    private void ghostmenu_freeCraft(Inventory inv, World world, CallbackInfoReturnable<Boolean> cir) {
        if (Cheats.FREE_CRAFT.on) {
            cir.setReturnValue(true);
        }
    }
}