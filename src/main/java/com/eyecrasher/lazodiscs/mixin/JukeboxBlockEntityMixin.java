package com.eyecrasher.lazodiscs.mixin;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.access.LazoDiscJukeboxAccess;
import com.eyecrasher.lazodiscs.data.DiscDataUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
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

    /**
     * Minecraft 1.21.1 does not have setTheItemWithoutPlaying().
     * So we let vanilla update the jukebox normally, then JukeboxPlaybackManager
     * immediately sends a RECORDS stop packet near this jukebox and starts the
     * Plasmo Voice source. This removes the original disc sound without relying
     * on a non-existent vanilla helper method.
     */
    @Inject(method = "setTheItem", at = @At("RETURN"), require = 0)
    private void lazodiscs$setTheItem(ItemStack stack, CallbackInfo ci) {
        lazodiscs$changed(stack, "setTheItem");
    }

    @Inject(method = "popOutTheItem", at = @At("HEAD"), require = 0)
    private void lazodiscs$popOutTheItem(CallbackInfo ci) {
        lazodiscs$stop("popOutTheItem");
    }

    @Inject(method = "removeTheItem", at = @At("HEAD"), require = 0)
    private void lazodiscs$removeTheItem(CallbackInfo ci) {
        lazodiscs$stop("removeTheItem");
    }

    @Inject(method = "clearContent", at = @At("HEAD"), require = 0)
    private void lazodiscs$clearContent(CallbackInfo ci) {
        lazodiscs$stop("clearContent");
    }

    @Inject(method = "setRemoved", at = @At("HEAD"), require = 0)
    private void lazodiscs$setRemoved(CallbackInfo ci) {
        lazodiscs$stop("setRemoved");
    }

    @Inject(method = "loadAdditional", at = @At("RETURN"), require = 0)
    private void lazodiscs$loadAdditional(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        lazodiscs$resync("loadAdditional");
    }

    @Inject(method = "onLoad", at = @At("RETURN"), require = 0)
    private void lazodiscs$onLoad(CallbackInfo ci) {
        lazodiscs$resync("onLoad");
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

        this.item = stack.copyWithCount(1);

        if (level != null) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof JukeboxBlock && !state.getValue(JukeboxBlock.HAS_RECORD)) {
                level.setBlock(pos, state.setValue(JukeboxBlock.HAS_RECORD, true), 3);
            }

            self.setChanged();
            this.onSongChanged();
        }
    }

}
