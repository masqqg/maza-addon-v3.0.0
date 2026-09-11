package com.maza.addon.mixin;

import com.maza.addon.modules.CaveFiller;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.world.chunk.WorldChunk.class)
public class CaveFillerMixin {

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void onGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        try {
            CaveFiller module = getModule();
            if (module == null || !module.isActive()) return;
            if (!module.shouldFill(pos)) return;

            BlockState current = cir.getReturnValue();
            if (current.isAir()) {
                // Y katmanına göre taş veya deepslate
                BlockState replacement = pos.getY() > 0 ? Blocks.STONE.getDefaultState() : Blocks.DEEPSLATE.getDefaultState();
                cir.setReturnValue(replacement);
            }
        } catch (Exception ignored) {}
    }

    private CaveFiller getModule() {
        try {
            return meteordevelopment.meteorclient.systems.modules.Modules.get().get(CaveFiller.class);
        } catch (Exception e) {
            return null;
        }
    }
}
