package com.eyecrasher.lazodiscs.mixin;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.access.LazoDiscJukeboxAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(JukeboxBlockEntity.class)
public abstract class JukeboxBlockEntityMixin implements LazoDiscJukeboxAccess {

    @Shadow
    private ItemStack item;

    @Shadow
    public abstract void onSongChanged();


    @Override
    @Unique
    public void lazodiscs$setLazoDiscItem(ItemStack stack) {

        JukeboxBlockEntity self = (JukeboxBlockEntity) (Object) this;

        this.item = stack.copyWithCount(1);

        Level level = self.getLevel();

        if (level != null) {

            BlockPos pos = self.getBlockPos();

            BlockState state = level.getBlockState(pos);

            if (state.getBlock() instanceof JukeboxBlock
                    && !state.getValue(JukeboxBlock.HAS_RECORD)) {

                level.setBlock(
                        pos,
                        state.setValue(JukeboxBlock.HAS_RECORD, true),
                        3
                );
            }

            self.setChanged();
        }
    }


    @Inject(
            method = "popOutTheItem",
            at = @At("HEAD")
    )
    private void lazodiscs$stopPlayback(CallbackInfo ci) {

        JukeboxBlockEntity self = (JukeboxBlockEntity) (Object) this;

        if (self.getLevel() instanceof ServerLevel serverLevel) {

            LazoDiscs.playback().stopAt(
                    serverLevel,
                    self.getBlockPos(),
                    "discRemoved"
            );
        }
    }
}