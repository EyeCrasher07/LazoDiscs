package com.eyecrasher.lazodiscs.server;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.config.LazoDiscsConfig;
import com.eyecrasher.lazodiscs.data.CustomDiscData;
import com.eyecrasher.lazodiscs.data.DiscDataUtil;
import com.eyecrasher.lazodiscs.compat.SablePositionCompat;
import com.eyecrasher.lazodiscs.voice.PlasmoVoiceBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JukeboxPlaybackManager {
    public static final JukeboxPlaybackManager INSTANCE = new JukeboxPlaybackManager();

    private final Map<SourceKey, ActiveJukeboxSource> active = new ConcurrentHashMap<>();
    private final Map<SourceKey, Long> restartBlockedUntilTick = new ConcurrentHashMap<>();

    private JukeboxPlaybackManager() {
    }

    public void onJukeboxItemChanged(ServerLevel level, BlockPos pos, ItemStack newStack, String reason) {
        // Do not spam StopSoundPacket several times for one insertion. The new start() call
        // will stop vanilla RECORDS once after it knows whether a custom disc is actually present.
        stopAt(level, pos, reason + ":before-change", false);

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
        SourceKey sourceKey = new SourceKey(level.dimension(), pos.immutable());
        ActiveJukeboxSource existing = active.get(sourceKey);
        if (existing != null && existing.disc().equals(disc)) {
            LazoDiscs.LOGGER.debug("Ignoring duplicate LazoDisc start for '{}' at {} ({})", disc.title(), pos.toShortString(), reason);
            VanillaRecordStopper.stopVanillaRecordsNear(level, pos, 4.0D);
            return;
        }

        long now = level.getGameTime();
        long blockedUntil = restartBlockedUntilTick.getOrDefault(sourceKey, 0L);
        if (blockedUntil > now) {
            LazoDiscs.LOGGER.warn("Rejected LazoDisc '{}' at {}: jukebox restart cooldown for {} more ticks",
                    disc.title(), pos.toShortString(), blockedUntil - now);
            VanillaRecordStopper.stopVanillaRecordsNear(level, pos, 4.0D);
            return;
        }

        ActiveJukeboxSource old = active.remove(sourceKey);
        if (old != null) {
            try {
                old.stop();
            } catch (Exception e) {
                LazoDiscs.LOGGER.warn("Failed to stop old LazoDisc at {}: {}", pos.toShortString(), e.toString());
            }
        }

        rememberRestart(level, sourceKey);

        try {
            Vec3 center = Vec3.atCenterOf(pos);
            Vec3 projected = SablePositionCompat.projectJukeboxCenter(level, pos);
            boolean dynamicPosition = SablePositionCompat.isProbablySubLevel(pos) || projected.distanceToSqr(center) > 0.0001D;

            var source = PlasmoVoiceBridge.INSTANCE.startStaticSource(level, pos, disc);
            active.put(sourceKey, new ActiveJukeboxSource(disc, source, dynamicPosition));
            // Stop vanilla record sound that may have started from the original music disc.
            VanillaRecordStopper.stopVanillaRecordsNear(level, pos, 4.0D);
            LazoDiscs.LOGGER.info("Started LazoDisc '{}' at {} ({}, dynamicPosition={})", disc.title(), pos.toShortString(), reason, dynamicPosition);
        } catch (Exception e) {
            LazoDiscs.LOGGER.warn("Failed to start LazoDisc at {}: {}", pos.toShortString(), e.toString());
        }
    }

    public void stopAt(ServerLevel level, BlockPos pos, String reason) {
        stopAt(level, pos, reason, true);
    }

    private void stopAt(ServerLevel level, BlockPos pos, String reason, boolean stopVanillaRecords) {
        ActiveJukeboxSource old = active.remove(new SourceKey(level.dimension(), pos.immutable()));
        if (old != null) {
            try {
                old.stop();
            } catch (Exception e) {
                LazoDiscs.LOGGER.warn("Failed to stop LazoDisc at {}: {}", pos.toShortString(), e.toString());
            }
            LazoDiscs.LOGGER.info("Stopped LazoDisc at {} ({})", pos.toShortString(), reason);
        }
        if (stopVanillaRecords) {
            VanillaRecordStopper.stopVanillaRecordsNear(level, pos, 4.0D);
        }
    }

    private void rememberRestart(ServerLevel level, SourceKey sourceKey) {
        int cooldownTicks = LazoDiscsConfig.JUKEBOX_RESTART_COOLDOWN_TICKS.get();
        if (cooldownTicks <= 0) {
            restartBlockedUntilTick.remove(sourceKey);
            return;
        }
        restartBlockedUntilTick.put(sourceKey, level.getGameTime() + cooldownTicks);
    }

    public void stopChunk(ServerLevel level, ChunkPos chunkPos, String reason) {
        Iterator<Map.Entry<SourceKey, ActiveJukeboxSource>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<SourceKey, ActiveJukeboxSource> e = it.next();
            SourceKey key = e.getKey();
            if (key.dimension().equals(level.dimension()) && new ChunkPos(key.pos()).equals(chunkPos)) {
                it.remove();
                restartBlockedUntilTick.remove(key);
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
        // Normal world jukeboxes are static; only Sable/sub-level sources need repeated position updates.
        long gameTime = level.getGameTime();
        int validationInterval = Math.max(1, LazoDiscsConfig.VALIDATION_INTERVAL_TICKS.get());
        boolean validate = gameTime % validationInterval == 0;
        if (validate) {
            restartBlockedUntilTick.entrySet().removeIf(e -> e.getKey().dimension().equals(level.dimension()) && e.getValue() <= gameTime);
        }

        Iterator<Map.Entry<SourceKey, ActiveJukeboxSource>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<SourceKey, ActiveJukeboxSource> e = it.next();
            SourceKey key = e.getKey();
            if (!key.dimension().equals(level.dimension())) continue;

            if (validate && !isStillValidCustomJukebox(level, key.pos())) {
                it.remove();
                e.getValue().stop();
                continue;
            }

            ActiveJukeboxSource activeSource = e.getValue();
            if (activeSource.dynamicPosition()) {
                // Moving Sable / Create Aeronautics assemblies must update every tick, otherwise
                // the Plasmo source audibly lags behind the flying platform.
                Vec3 projected = SablePositionCompat.projectJukeboxCenter(level, key.pos());
                activeSource.updatePosition(level, projected);
            }
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
        restartBlockedUntilTick.clear();
        LazoDiscs.LOGGER.info("Stopped all LazoDisc sources ({})", reason);
    }

    public void pruneInvalid(ServerLevel level) {
        // This is NOT a world scan. It only validates already-active sources.
        Iterator<Map.Entry<SourceKey, ActiveJukeboxSource>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<SourceKey, ActiveJukeboxSource> e = it.next();
            SourceKey key = e.getKey();
            if (!key.dimension().equals(level.dimension())) continue;
            if (!isStillValidCustomJukebox(level, key.pos())) {
                it.remove();
                restartBlockedUntilTick.remove(key);
                e.getValue().stop();
            }
        }
    }

    private boolean isStillValidCustomJukebox(ServerLevel level, BlockPos pos) {
        if (!isValidJukebox(level, pos)) return false;
        return level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox
                && DiscDataUtil.hasCustomDisc(jukebox.getTheItem());
    }

    private boolean isValidJukebox(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.JUKEBOX) && level.getBlockEntity(pos) instanceof JukeboxBlockEntity;
    }
}
