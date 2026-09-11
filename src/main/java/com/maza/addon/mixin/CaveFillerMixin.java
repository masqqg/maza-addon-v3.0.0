package com.maza.addon.mixin;

import com.maza.addon.modules.CaveFiller;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public class CaveFillerMixin {

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void onGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        try {
            if (pos == null) return;
            
            CaveFiller module = Modules.get().get(CaveFiller.class);
            if (module == null || !module.isActive()) return;
            
            if (!module.shouldFill(pos)) return;
            
            BlockState current = cir.getReturnValue();
            if (current != null && current.isAir()) {
                BlockState replacement = pos.getY() > 0 
                    ? Blocks.STONE.getDefaultState() 
                    : Blocks.DEEPSLATE.getDefaultState();
                cir.setReturnValue(replacement);
            }
        } catch (Exception ignored) {}
    }
}
