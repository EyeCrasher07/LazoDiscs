package com.eyecrasher.lazodiscs.server;

import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public record SourceKey(RegistryKey<World> dimension, BlockPos pos) {}
