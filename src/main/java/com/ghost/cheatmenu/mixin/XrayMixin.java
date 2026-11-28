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
 * X-Ray —— 黑名单方案（只隐藏"废石"，其余全部保留）
 *
 * 之前用白名单，漏一个矿就消失一个（下界石英矿就是这么没的）。
 * 现在反过来：只有列出来的垃圾方块才隐藏，矿石 / 箱子 / 任何 mod 方块天然保留。
 * 这样就不会再"莫名其妙连矿石都看不见"。
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public class XrayMixin {

    @Inject(method = "getRenderType()Lnet/minecraft/block/BlockRenderType;", at = @At("HEAD"), cancellable = true)
    private void ghostmenu_getRenderType(CallbackInfoReturnable<BlockRenderType> cir) {
        if (!Cheats.XRAY.on) return;

        AbstractBlock.AbstractBlockState self = (AbstractBlock.AbstractBlockState) (Object) this;
        BlockState state = (BlockState) self;
        Block block = state.getBlock();

        if (isHidden(block)) {
            cir.setReturnValue(BlockRenderType.INVISIBLE);
        }
    }

    /** 黑名单：只藏这些"填充石头"，矿石/容器/植被全部保留 */
    private static boolean isHidden(Block b) {
        return b == Blocks.STONE
                || b == Blocks.COBBLESTONE
                || b == Blocks.DIRT
                || b == Blocks.GRASS_BLOCK
                || b == Blocks.GRAVEL
                || b == Blocks.SAND
                || b == Blocks.SANDSTONE
                || b == Blocks.ANDESITE
                || b == Blocks.DIORITE
                || b == Blocks.GRANITE
                || b == Blocks.NETHERRACK
                || b == Blocks.END_STONE
                || b == Blocks.BASALT
                || b == Blocks.BLACKSTONE
                || b == Blocks.SNOW_BLOCK
                || b == Blocks.CLAY
                || b == Blocks.TERRACOTTA
                || b == Blocks.SOUL_SAND
                || b == Blocks.SOUL_SOIL
                || b == Blocks.MAGMA_BLOCK
                || b == Blocks.BEDROCK
                || b == Blocks.WATER
                || b == Blocks.LAVA
                // 树叶：透视时挡住视线，一并藏掉
                || b == Blocks.OAK_LEAVES
                || b == Blocks.SPRUCE_LEAVES
                || b == Blocks.BIRCH_LEAVES
                || b == Blocks.JUNGLE_LEAVES
                || b == Blocks.ACACIA_LEAVES
                || b == Blocks.DARK_OAK_LEAVES;
    }
}