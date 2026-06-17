package com.eyecrasher.lazodiscs.server;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.data.CustomDiscData;
import com.eyecrasher.lazodiscs.data.DiscDataUtil;
import com.eyecrasher.lazodiscs.compat.SablePositionCompat;
import com.eyecrasher.lazodiscs.voice.PlasmoVoiceBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JukeboxPlaybackManager {
    public static final JukeboxPlaybackManager INSTANCE = new JukeboxPlaybackManager();

    private final Map<SourceKey, ActiveJukeboxSource> active = new ConcurrentHashMap<>();

    private JukeboxPlaybackManager() {
    }

    public void onJukeboxItemChanged(ServerLevel level, BlockPos pos, ItemStack newStack, String reason) {
        stopAt(level, pos, reason + ":before-change");

        DiscDataUtil.read(newStack).ifPresent(disc -> {
            if (!isValidJukebox(level, pos)) return;
            start(level, pos, disc, reason);
        });
    }

    public void resyncFromBlockEntity(ServerLevel level, BlockPos pos, String reason) {
        if (!(level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox)) {
            stopAt(level, pos, reason + ":no-be");
            return;
        }

        ItemStack stack = jukebox.getTheItem();
        if (DiscDataUtil.hasCustomDisc(stack)) {
            onJukeboxItemChanged(level, pos, stack, reason);
        } else {
            stopAt(level, pos, reason + ":not-custom-disc");
        }
    }

    public void start(ServerLevel level, BlockPos pos, CustomDiscData disc, String reason) {
        stopAt(level, pos, reason + ":replace-old");

        try {
            var source = PlasmoVoiceBridge.INSTANCE.startStaticSource(level, pos, disc);
            active.put(new SourceKey(level.dimension(), pos.immutable()), new ActiveJukeboxSource(disc, source));
            // Stop vanilla record sound that may have started from the original music disc.
            VanillaRecordStopper.stopVanillaRecordsNear(level, pos, 4.0D);
            LazoDiscs.LOGGER.info("Started LazoDisc '{}' at {} ({})", disc.title(), pos.toShortString(), reason);
        } catch (Exception e) {
            LazoDiscs.LOGGER.warn("Failed to start LazoDisc at {}: {}", pos.toShortString(), e.toString());
        }
    }

    public void stopAt(ServerLevel level, BlockPos pos, String reason) {
        ActiveJukeboxSource old = active.remove(new SourceKey(level.dimension(), pos.immutable()));
        if (old != null) {
            try {
                old.stop();
            } catch (Exception e) {
                LazoDiscs.LOGGER.warn("Failed to stop LazoDisc at {}: {}", pos.toShortString(), e.toString());
            }
            LazoDiscs.LOGGER.info("Stopped LazoDisc at {} ({})", pos.toShortString(), reason);
        }
        VanillaRecordStopper.stopVanillaRecordsNear(level, pos, 4.0D);
    }

    public void stopChunk(ServerLevel level, ChunkPos chunkPos, String reason) {
        Iterator<Map.Entry<SourceKey, ActiveJukeboxSource>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<SourceKey, ActiveJukeboxSource> e = it.next();
            SourceKey key = e.getKey();
            if (key.dimension().equals(level.dimension()) && new ChunkPos(key.pos()).equals(chunkPos)) {
                it.remove();
                try {
                    e.getValue().stop();
                } catch (Exception ex) {
                    LazoDiscs.LOGGER.warn("Failed to stop LazoDisc during chunk unload: {}", ex.toString());
                }
            }
        }
    }

    public void tickLevel(ServerLevel level) {
        // Not a world scan: only already-playing LazoDiscs are updated.
        // Needed for Sable physics platforms, where the sub-level BlockPos is stable
        // but the projected real-world position changes while the platform moves.
        Iterator<Map.Entry<SourceKey, ActiveJukeboxSource>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<SourceKey, ActiveJukeboxSource> e = it.next();
            SourceKey key = e.getKey();
            if (!key.dimension().equals(level.dimension())) continue;

            if (!isValidJukebox(level, key.pos())) {
                it.remove();
                e.getValue().stop();
                continue;
            }

            if (!(level.getBlockEntity(key.pos()) instanceof JukeboxBlockEntity jukebox) || !DiscDataUtil.hasCustomDisc(jukebox.getTheItem())) {
                it.remove();
                e.getValue().stop();
                continue;
            }

            Vec3 projected = SablePositionCompat.projectJukeboxCenter(level, key.pos());
            e.getValue().updatePosition(level, projected);
        }
    }

    public void stopAll(String reason) {
        for (ActiveJukeboxSource source : active.values()) {
            try {
                source.stop();
            } catch (Exception e) {
                LazoDiscs.LOGGER.warn("Failed to stop LazoDisc during stopAll: {}", e.toString());
            }
        }
        active.clear();
        LazoDiscs.LOGGER.info("Stopped all LazoDisc sources ({})", reason);
    }

    public void pruneInvalid(ServerLevel level) {
        // This is NOT a world scan. It only validates already-active sources.
        Iterator<Map.Entry<SourceKey, ActiveJukeboxSource>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<SourceKey, ActiveJukeboxSource> e = it.next();
            SourceKey key = e.getKey();
            if (!key.dimension().equals(level.dimension())) continue;
            if (!isValidJukebox(level, key.pos())) {
                it.remove();
                e.getValue().stop();
                continue;
            }
            if (!(level.getBlockEntity(key.pos()) instanceof JukeboxBlockEntity jukebox) || !DiscDataUtil.hasCustomDisc(jukebox.getTheItem())) {
                it.remove();
                e.getValue().stop();
            }
        }
    }

    private boolean isValidJukebox(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.JUKEBOX) && level.getBlockEntity(pos) instanceof JukeboxBlockEntity;
    }
}
