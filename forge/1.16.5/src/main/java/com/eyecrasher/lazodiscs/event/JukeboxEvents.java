package com.eyecrasher.lazodiscs.event;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.compat.sophisticatedbackpacks.LazoDiscsDiscHandler;
import com.eyecrasher.lazodiscs.data.DiscDataUtil;
import com.eyecrasher.lazodiscs.voice.PlasmoVoiceBridge;

import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.JukeboxTileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class JukeboxEvents {
    private static final Set<PendingChunk> pendingResyncs = ConcurrentHashMap.newKeySet();
    private static MinecraftServer resyncServer;

    private JukeboxEvents() {}

    @SubscribeEvent
    public static void onServerAboutToStart(FMLServerAboutToStartEvent event) {
        synchronized (pendingResyncs) {
            pendingResyncs.clear();
            resyncServer = event.getServer();
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getWorld() instanceof ServerWorld level)) return;
        BlockPos pos = event.getPos();
        if (event.getState().is(Blocks.JUKEBOX)) {
            LazoDiscs.playback().stopAt(level, pos, "block-break");
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getWorld() instanceof ServerWorld level)) return;
        IChunk chunk = event.getChunk();
        if (!(chunk instanceof Chunk)) return;
        // Load can fire off-thread before FULL promotion. Only enqueue; never touch the level here.
        synchronized (pendingResyncs) {
            if (level.getServer() == resyncServer) {
                pendingResyncs.add(new PendingChunk(level, chunk.getPos()));
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getWorld() instanceof ServerWorld level)) return;
        pendingResyncs.remove(new PendingChunk(level, event.getChunk().getPos()));
        LazoDiscs.playback().stopChunk(level, event.getChunk().getPos(), "chunk-unload");
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        MinecraftServer server = LazoDiscs.getCurrentServer();
        if (server == null) return;
        resyncLoadedJukeboxes();
        for (ServerWorld level : server.getAllLevels()) {
            LazoDiscs.playback().tickLevel(level);
        }
        LazoDiscsDiscHandler.onServerTick(server);
    }

    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
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
            ServerWorld level = request.level();
            ChunkPos pos = request.pos();
            Chunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null || !pendingResyncs.remove(request)) continue;
            for (var blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof JukeboxTileEntity jukebox) {
                    // start() preserves an already-active identical disc; an item-change resync
                    // would stop it first and unnecessarily rewind playback.
                    DiscDataUtil.read(jukebox.getRecord())
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

    private record PendingChunk(ServerWorld level, ChunkPos pos) {}
}
