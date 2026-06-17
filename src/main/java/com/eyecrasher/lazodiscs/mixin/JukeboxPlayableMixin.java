package com.eyecrasher.lazodiscs.mixin;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.access.LazoDiscJukeboxAccess;
import com.eyecrasher.lazodiscs.config.LazoDiscsConfig;
import com.eyecrasher.lazodiscs.data.CustomDiscData;
import com.eyecrasher.lazodiscs.data.DiscDataUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(JukeboxPlayable.class)
public abstract class JukeboxPlayableMixin {
    /**
     * Custom LazoDiscs are still based on vanilla music disc items, but vanilla would normally
     * play the original disc sound and show the original song name. We cancel vanilla insertion
     * for LazoDiscs, insert the item silently, show the LazoDisc title, and let Plasmo Voice play
     * only the custom URL audio.
     */
    @Inject(method = "tryInsertIntoJukebox", at = @At("HEAD"), cancellable = true, require = 0)
    private static void lazodiscs$tryInsertIntoJukebox(Level level, BlockPos pos, ItemStack stack, Player player, CallbackInfoReturnable<ItemInteractionResult> cir) {
        Optional<CustomDiscData> data = DiscDataUtil.read(stack);
        if (data.isEmpty()) {
            return;
        }

        BlockState blockstate = level.getBlockState(pos);
        if (!blockstate.is(Blocks.JUKEBOX) || blockstate.getValue(JukeboxBlock.HAS_RECORD)) {
            return;
        }

        if (!level.isClientSide()) {
            ItemStack record = stack.consumeAndReturn(1, player);
            if (level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox) {
                if (jukebox instanceof LazoDiscJukeboxAccess access) {
                    access.lazodiscs$setLazoDiscItem(record);
                } else {
                    // Fallback for unexpected mixin failure. This may briefly trigger vanilla audio,
                    // but the playback manager will still stop nearby RECORDS sounds.
                    jukebox.setTheItem(record);
                }

                if (level instanceof ServerLevel serverLevel) {
                    LazoDiscs.playback().onJukeboxItemChanged(serverLevel, pos, record, "lazodiscsInsert");
                }
                level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, blockstate));
            }

            player.awardStat(Stats.PLAY_RECORD);
            String title = data.get().title();
            String nowPlayingMessage = LazoDiscsConfig.NOW_PLAYING_MESSAGE.get();
            if (nowPlayingMessage == null || nowPlayingMessage.isBlank()) {
                player.displayClientMessage(Component.translatable("record.nowPlaying", Component.literal(title)), true);
            } else {
                player.displayClientMessage(Component.literal(nowPlayingMessage.replace("%title%", title)), true);
            }
        }

        cir.setReturnValue(ItemInteractionResult.sidedSuccess(level.isClientSide()));
    }
}
