package com.eyecrasher.lazodiscs.server;

import com.eyecrasher.lazodiscs.config.LazoDiscsConfig;

import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerEntity;

public final class LazoDiscsPermissions {
    private LazoDiscsPermissions() {}

    public static boolean canBurn(CommandSource source) {
        return allowed(
                source,
                LazoDiscsConfig.REQUIRE_PERMISSION_FOR_BURN_COMMAND.get(),
                LazoDiscsConfig.BURN_PERMISSION_LEVEL.get());
    }

    public static boolean canErase(CommandSource source) {
        return allowed(
                source,
                LazoDiscsConfig.REQUIRE_PERMISSION_FOR_ERASE_COMMAND.get(),
                LazoDiscsConfig.ERASE_PERMISSION_LEVEL.get());
    }

    public static boolean canSearch(CommandSource source) {
        return allowed(
                source,
                LazoDiscsConfig.REQUIRE_PERMISSION_FOR_SEARCH_COMMAND.get(),
                LazoDiscsConfig.SEARCH_PERMISSION_LEVEL.get());
    }

    public static boolean canPlay(PlayerEntity player) {
        if (!LazoDiscsConfig.REQUIRE_PERMISSION_FOR_PLAY.get()) {
            return true;
        }
        return player != null && player.hasPermissions(LazoDiscsConfig.PLAY_PERMISSION_LEVEL.get());
    }

    private static boolean allowed(CommandSource source, boolean required, int level) {
        return !required || source != null && source.hasPermission(level);
    }
}
