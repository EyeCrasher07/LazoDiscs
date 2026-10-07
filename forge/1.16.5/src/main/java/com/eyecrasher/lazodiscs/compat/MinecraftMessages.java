package com.eyecrasher.lazodiscs.compat;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.ITextComponent;

/** System-chat adapter for Minecraft versions before the signed-chat API. */
public final class MinecraftMessages {
    private MinecraftMessages() {}

    public static void send(ServerPlayerEntity player, ITextComponent message) {
        player.sendMessage(message, ChatType.SYSTEM, Util.NIL_UUID);
    }
}
