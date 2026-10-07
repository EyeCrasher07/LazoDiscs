package com.eyecrasher.lazodiscs.voice;

import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;

public interface PlayingVoiceSource {
    void stop();

    /**
     * Moves an already-created Plasmo Voice source. This is important for Sable sub-level blocks:
     * the original BlockPos stays in the technical plot, while the projected real-world position
     * changes as the platform moves.
     */
    default void updatePosition(ServerWorld level, Vector3d projectedPosition) {}
}
