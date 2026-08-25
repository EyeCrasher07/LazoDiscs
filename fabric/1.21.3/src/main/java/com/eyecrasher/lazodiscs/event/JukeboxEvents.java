package com.eyecrasher.lazodiscs.event;

import com.eyecrasher.lazodiscs.LazoDiscs;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

public final class JukeboxEvents {
    private JukeboxEvents() {
    }

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerLevel level && state.is(Blocks.JUKEBOX)) {
                LazoDiscs.playback().stopAt(level, pos, "block-break");
            }
        });

        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
                LazoDiscs.playback().stopChunk(level, chunk.getPos(), "chunk-unload"));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                LazoDiscs.playback().tickLevel(level);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                LazoDiscs.playback().stopAll("server-stopping"));
    }
}
