package com.eyecrasher.lazodiscs.server;

import com.eyecrasher.lazodiscs.config.LazoDiscsConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

public final class LazoDiscsPermissions {
    private LazoDiscsPermissions() {
    }

    public static boolean canBurn(CommandSourceStack source) {
        return allowed(source, LazoDiscsConfig.REQUIRE_PERMISSION_FOR_BURN_COMMAND.get(), LazoDiscsConfig.BURN_PERMISSION_LEVEL.get());
    }

    public static boolean canErase(CommandSourceStack source) {
        return allowed(source, LazoDiscsConfig.REQUIRE_PERMISSION_FOR_ERASE_COMMAND.get(), LazoDiscsConfig.ERASE_PERMISSION_LEVEL.get());
    }

    public static boolean canSearch(CommandSourceStack source) {
        return allowed(source, LazoDiscsConfig.REQUIRE_PERMISSION_FOR_SEARCH_COMMAND.get(), LazoDiscsConfig.SEARCH_PERMISSION_LEVEL.get());
    }

    public static boolean canStopAll(CommandSourceStack source) {
        return allowed(source, LazoDiscsConfig.REQUIRE_PERMISSION_FOR_STOPALL_COMMAND.get(), LazoDiscsConfig.STOPALL_PERMISSION_LEVEL.get());
    }

    public static boolean canPlay(Player player) {
        if (!LazoDiscsConfig.REQUIRE_PERMISSION_FOR_PLAY.get()) {
            return true;
        }
        return hasPermission(player, LazoDiscsConfig.PLAY_PERMISSION_LEVEL.get());
    }

    private static boolean allowed(CommandSourceStack source, boolean required, int level) {
        if (!required) {
            return true;
        }
        return hasPermission(source, level);
    }

    private static boolean hasPermission(Object target, int level) {
        if (target == null) return true;

        String[] methodNames = {"hasPermission", "hasPermissionLevel", "hasPermissions"};
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName, int.class);
                Object result = method.invoke(target, level);
                if (result instanceof Boolean allowed) {
                    return allowed;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next possible method name.
            }
        }

        if (target instanceof CommandSourceStack source) {
            try {
                return hasPermission(source.getPlayer(), level);
            } catch (Exception ignored) {
                return true;
            }
        }

        // Fallback: do not lock everyone out if the permission API changes again.
        return true;
    }
}
