package com.eyecrasher.lazodiscs.server;

import com.eyecrasher.lazodiscs.compat.SablePositionCompat;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.server.SStopSoundPacket;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;

public final class VanillaRecordStopper {
    private VanillaRecordStopper() {}

    public static void stopVanillaRecordsNear(ServerWorld level, BlockPos pos, double radius) {
        double max = radius * radius;
        SStopSoundPacket packet = new SStopSoundPacket(null, SoundCategory.RECORDS);
        Vector3d projected = SablePositionCompat.projectJukeboxCenter(level, pos);
        for (ServerPlayerEntity player : level.players()) {
            if (player.distanceToSqr(projected.x, projected.y, projected.z) <= max) {
                player.connection.send(packet);
            }
        }
    }
}
