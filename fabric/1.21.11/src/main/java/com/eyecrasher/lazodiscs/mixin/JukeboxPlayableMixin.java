package com.eyecrasher.lazodiscs.mixin;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.LazoDiscsServerBootstrap;
import com.eyecrasher.lazodiscs.access.LazoDiscJukeboxAccess;
import com.eyecrasher.lazodiscs.data.CustomDiscData;
import com.eyecrasher.lazodiscs.data.DiscDataUtil;
import com.eyecrasher.lazodiscs.server.LazoDiscsPermissions;
import com.eyecrasher.lazodiscs.text.LazoDiscsText;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
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


    @Inject(
            method = "tryInsertIntoJukebox",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void lazodiscs$tryInsertIntoJukebox(
            Level level,
            BlockPos pos,
            ItemStack stack,
            Player player,
            CallbackInfoReturnable<InteractionResult> cir
    ) {

        Optional<CustomDiscData> data = DiscDataUtil.read(stack);

        if (data.isEmpty()) {
            return;
        }


        BlockState state = level.getBlockState(pos);


        if (!state.is(Blocks.JUKEBOX)
                || state.getValue(JukeboxBlock.HAS_RECORD)) {

            return;
        }


        if (!level.isClientSide()) {


            if (!LazoDiscsServerBootstrap.isLoaded()) {

                player.displayClientMessage(
                        LazoDiscsText.plasmoVoiceRequired(),
                        true
                );

                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }


            if (!LazoDiscsPermissions.canPlay(player)) {

                player.displayClientMessage(
                        LazoDiscsText.playNoPermission(),
                        true
                );

                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }


            ItemStack record = stack.consumeAndReturn(1, player);


            if (level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox) {


                if (jukebox instanceof LazoDiscJukeboxAccess access) {

                    access.lazodiscs$setLazoDiscItem(record);

                } else {

                    cir.setReturnValue(InteractionResult.FAIL);
                    return;
                }


                if (level instanceof ServerLevel serverLevel) {

                    LazoDiscs.playback().onJukeboxItemChanged(
                            serverLevel,
                            pos,
                            record,
                            "lazodiscsInsert"
                    );
                }


                level.gameEvent(
                        GameEvent.BLOCK_CHANGE,
                        pos,
                        GameEvent.Context.of(player, state)
                );
            }


            player.awardStat(Stats.PLAY_RECORD);


            player.displayClientMessage(
                    Component.literal(
                            LazoDiscsText.nowPlaying(data.get().title())
                    ),
                    true
            );
        }


        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}