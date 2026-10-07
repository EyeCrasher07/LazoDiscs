package com.eyecrasher.lazodiscs.server;

import com.eyecrasher.lazodiscs.data.CustomDiscData;
import com.eyecrasher.lazodiscs.voice.PlayingVoiceSource;

import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;

public record ActiveJukeboxSource(
        CustomDiscData disc,
        PlayingVoiceSource source,
        java.util.concurrent.atomic.AtomicBoolean dynamicPosition) {
    public void stop() {
        source.stop();
    }

    public void updatePosition(ServerWorld level, Vector3d projectedPosition) {
        source.updatePosition(level, projectedPosition);
    }

    public boolean isDynamicPosition() {
        return dynamicPosition.get();
    }

    public void markDynamicPosition() {
        dynamicPosition.set(true);
    }
}
