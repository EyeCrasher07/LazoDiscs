package com.eyecrasher.lazodiscs.mixin;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.access.LazoDiscJukeboxAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(JukeboxBlockEntity.class)
public abstract class JukeboxBlockEntityMixin implements LazoDiscJukeboxAccess {
    @Inject(method = "setTheItem", at = @At("RETURN"))
    private void lazodiscs$setTheItem(ItemStack stack, CallbackInfo ci) {
        JukeboxBlockEntity self = (JukeboxBlockEntity) (Object) this;
        lazodiscs$changed(self.getTheItem(), "setTheItem");
    }

    @Inject(method = "popOutRecord", at = @At("HEAD"))
    private void lazodiscs$popOutRecord(CallbackInfo ci) {
        lazodiscs$stop("popOutRecord");
    }

    // ContainerSingleItem's inherited removal helpers all delegate here.
    @Inject(method = "splitTheItem", at = @At("HEAD"))
    private void lazodiscs$splitTheItem(int count, CallbackInfoReturnable<ItemStack> cir) {
        lazodiscs$stop("splitTheItem");
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void lazodiscs$load(CompoundTag tag, CallbackInfo ci) {
        lazodiscs$resync("loadAdditional");
    }

    private void lazodiscs$resync(String reason) {
        JukeboxBlockEntity self = (JukeboxBlockEntity) (Object) this;
        Level level = self.getLevel();
        BlockPos pos = self.getBlockPos();
        if (level instanceof ServerLevel serverLevel) {
            LazoDiscs.playback().resyncFromBlockEntity(serverLevel, pos, reason);
        }
    }

    private void lazodiscs$changed(ItemStack stack, String reason) {
        JukeboxBlockEntity self = (JukeboxBlockEntity) (Object) this;
        Level level = self.getLevel();
        BlockPos pos = self.getBlockPos();
        if (level instanceof ServerLevel serverLevel) {
            LazoDiscs.playback().onJukeboxItemChanged(serverLevel, pos, stack, reason);
        }
    }

    private void lazodiscs$stop(String reason) {
        JukeboxBlockEntity self = (JukeboxBlockEntity) (Object) this;
        Level level = self.getLevel();
        BlockPos pos = self.getBlockPos();
        if (level instanceof ServerLevel serverLevel) {
            LazoDiscs.playback().stopAt(serverLevel, pos, reason);
        }
    }

    @Override
    @Unique
    public void lazodiscs$setLazoDiscItem(ItemStack stack) {
        JukeboxBlockEntity self = (JukeboxBlockEntity) (Object) this;
        Level level = self.getLevel();
        BlockPos pos = self.getBlockPos();

        if (level != null) {
            // This version has a vanilla silent setter; do not invoke startPlaying().
            self.setRecordWithoutPlaying(stack.copyWithCount(1));
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof JukeboxBlock
                    && !state.getValue(JukeboxBlock.HAS_RECORD)) {
                level.setBlock(pos, state.setValue(JukeboxBlock.HAS_RECORD, true), 3);
            }

            self.setChanged();
        }
    }
}
