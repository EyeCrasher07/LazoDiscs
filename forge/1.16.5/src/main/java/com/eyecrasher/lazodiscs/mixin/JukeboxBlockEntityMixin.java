package com.eyecrasher.lazodiscs.mixin;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.access.LazoDiscJukeboxAccess;

import net.minecraft.block.BlockState;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.JukeboxTileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JukeboxTileEntity.class)
public abstract class JukeboxBlockEntityMixin implements LazoDiscJukeboxAccess {
    @Inject(method = "setRecord", at = @At("RETURN"))
    private void lazodiscs$setRecord(ItemStack stack, CallbackInfo ci) {
        lazodiscs$changed(stack, "setRecord");
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void lazodiscs$load(BlockState state, CompoundNBT tag, CallbackInfo ci) {
        lazodiscs$resync("load");
    }

    private void lazodiscs$resync(String reason) {
        JukeboxTileEntity self = (JukeboxTileEntity) (Object) this;
        World level = self.getLevel();
        BlockPos pos = self.getBlockPos();
        if (level instanceof ServerWorld serverLevel) {
            LazoDiscs.playback().resyncFromBlockEntity(serverLevel, pos, reason);
        }
    }

    private void lazodiscs$changed(ItemStack stack, String reason) {
        JukeboxTileEntity self = (JukeboxTileEntity) (Object) this;
        World level = self.getLevel();
        BlockPos pos = self.getBlockPos();
        if (level instanceof ServerWorld serverLevel) {
            LazoDiscs.playback().onJukeboxItemChanged(serverLevel, pos, stack, reason);
        }
    }

    private void lazodiscs$stop(String reason) {
        JukeboxTileEntity self = (JukeboxTileEntity) (Object) this;
        World level = self.getLevel();
        BlockPos pos = self.getBlockPos();
        if (level instanceof ServerWorld serverLevel) {
            LazoDiscs.playback().stopAt(serverLevel, pos, reason);
        }
    }

    @Override
    @Unique
    public void lazodiscs$setLazoDiscItem(ItemStack stack) {
        JukeboxTileEntity self = (JukeboxTileEntity) (Object) this;
        World level = self.getLevel();
        BlockPos pos = self.getBlockPos();

        ItemStack record = stack.copy();
        record.setCount(1);
        self.setRecord(record);

        if (level != null) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof JukeboxBlock
                    && !state.getValue(JukeboxBlock.HAS_RECORD)) {
                level.setBlock(pos, state.setValue(JukeboxBlock.HAS_RECORD, true), 3);
            }

            self.setChanged();
        }
    }
}
