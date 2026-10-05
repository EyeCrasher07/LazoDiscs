package com.eyecrasher.lazodiscs.event;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.compat.sophisticatedbackpacks.LazoDiscsDiscHandler;
import com.eyecrasher.lazodiscs.data.DiscDataUtil;
import com.eyecrasher.lazodiscs.voice.PlasmoVoiceBridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class JukeboxEvents {
    private static final Set<PendingChunk> pendingResyncs = ConcurrentHashMap.newKeySet();
    private static MinecraftServer resyncServer;

    private JukeboxEvents() {}

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        synchronized (pendingResyncs) {
            pendingResyncs.clear();
            resyncServer = event.getServer();
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        if (event.getState().is(Blocks.JUKEBOX)) {
            LazoDiscs.playback().stopAt(level, pos, "block-break");
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ChunkAccess chunk = event.getChunk();
        if (!(chunk instanceof LevelChunk)) return;
        // Load can fire off-thread before FULL promotion. Only enqueue; never touch the level here.
        synchronized (pendingResyncs) {
            if (level.getServer() == resyncServer) {
                pendingResyncs.add(new PendingChunk(level, chunk.getPos()));
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        pendingResyncs.remove(new PendingChunk(level, event.getChunk().getPos()));
        LazoDiscs.playback().stopChunk(level, event.getChunk().getPos(), "chunk-unload");
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        resyncLoadedJukeboxes();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            LazoDiscs.playback().tickLevel(level);
        }
        LazoDiscsDiscHandler.onServerTick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        synchronized (pendingResyncs) {
            resyncServer = null;
            pendingResyncs.clear();
        }
        LazoDiscs.playback().stopAll("server-stopping");
        LazoDiscsDiscHandler.stopAll("server-stopping");
    }

    private static void resyncLoadedJukeboxes() {
        // Saved chunks can load before Plasmo's source line is ready. Retry only those chunks.
        if (!PlasmoVoiceBridge.INSTANCE.isInitialized()) return;
        for (PendingChunk request : pendingResyncs) {
            ServerLevel level = request.level();
            ChunkPos pos = request.pos();
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null || !pendingResyncs.remove(request)) continue;
            for (var blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof JukeboxBlockEntity jukebox) {
                    // start() preserves an already-active identical disc; an item-change resync
                    // would stop it first and unnecessarily rewind playback.
                    DiscDataUtil.read(jukebox.getTheItem())
                            .ifPresent(
                                    disc ->
                                            LazoDiscs.playback()
                                                    .start(
                                                            level,
                                                            jukebox.getBlockPos(),
                                                            disc,
                                                            "chunk-load"));
                }
            }
        }
    }

    private record PendingChunk(ServerLevel level, ChunkPos pos) {}
}
