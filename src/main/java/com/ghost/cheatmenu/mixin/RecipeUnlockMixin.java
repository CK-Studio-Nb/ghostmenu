package com.ghost.cheatmenu.mixin;

import com.ghost.cheatmenu.Cheats;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.book.RecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 解锁全部图鉴（配方书）。
 * contains(Recipe) 决定"这个配方我有没有解锁"，逼它恒 true，
 * 客户端就会认为所有配方都已解锁，配方书里不再有锁住的灰色条目。
 */
@Mixin(RecipeBook.class)
public class RecipeUnlockMixin {

    @Inject(method = "contains(Lnet/minecraft/recipe/Recipe;)Z", at = @At("HEAD"), cancellable = true)
    private void ghostmenu_unlockAll(Recipe<?> recipe, CallbackInfoReturnable<Boolean> cir) {
        if (Cheats.UNLOCK_RECIPES.on && recipe != null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "shouldDisplay(Lnet/minecraft/recipe/Recipe;)Z", at = @At("HEAD"), cancellable = true)
    private void ghostmenu_shouldDisplay(Recipe<?> recipe, CallbackInfoReturnable<Boolean> cir) {
        if (Cheats.UNLOCK_RECIPES.on && recipe != null) {
            cir.setReturnValue(true);
        }
    }
}