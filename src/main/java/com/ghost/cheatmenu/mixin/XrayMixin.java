package com.ghost.cheatmenu.mixin;

import com.ghost.cheatmenu.Cheats;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * X-Ray：把非矿物的方块渲染类型改成 INVISIBLE。
 * 只影响渲染，不影响碰撞/破坏/服务端逻辑。
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public class XrayMixin {

    @Inject(method = "getRenderType()Lnet/minecraft/block/BlockRenderType;", at = @At("HEAD"), cancellable = true)
    private void ghostmenu_getRenderType(CallbackInfoReturnable<BlockRenderType> cir) {
        if (!Cheats.XRAY.on) return;

        AbstractBlock.AbstractBlockState self = (AbstractBlock.AbstractBlockState) (Object) this;
        BlockState state = (BlockState) self;
        Block block = state.getBlock();

        boolean keep = block == Blocks.DIAMOND_ORE
                || block == Blocks.IRON_ORE
                || block == Blocks.GOLD_ORE
                || block == Blocks.COAL_ORE
                || block == Blocks.REDSTONE_ORE
                || block == Blocks.EMERALD_ORE
                || block == Blocks.LAPIS_ORE
                || block == Blocks.NETHER_GOLD_ORE
                || block == Blocks.ANCIENT_DEBRIS
                || block == Blocks.CHEST
                || block == Blocks.ENDER_CHEST
                || block == Blocks.FURNACE
                || block == Blocks.SPAWNER
                || block == Blocks.WATER
                || block == Blocks.LAVA;

        if (!keep) {
            cir.setReturnValue(BlockRenderType.INVISIBLE);
        }
    }
}