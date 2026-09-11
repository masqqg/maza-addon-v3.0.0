package com.maza.addon.mixin;

import com.maza.addon.modules.OreFilter;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockView.class)
public interface OreFilterMixin {

    @Inject(
        method = "getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;",
        at = @At("RETURN"),
        cancellable = true
    )
    default void onGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        try {
            if (pos == null) return;
            
            OreFilter module = Modules.get().get(OreFilter.class);
            if (module == null || !module.isActive()) return;
            
            if (!module.isFakeOre(pos)) return;
            
            BlockState current = cir.getReturnValue();
            if (current != null && !current.isAir()) {
                // Y katmanına göre taş veya deepslate
                BlockState replacement = pos.getY() > 0 
                    ? Blocks.STONE.getDefaultState() 
                    : Blocks.DEEPSLATE.getDefaultState();
                cir.setReturnValue(replacement);
            }
        } catch (Exception ignored) {}
    }
}
