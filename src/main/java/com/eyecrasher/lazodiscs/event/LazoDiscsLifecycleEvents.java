package com.eyecrasher.lazodiscs.event;

import com.eyecrasher.lazodiscs.LazoDiscsServerBootstrap;
import com.eyecrasher.lazodiscs.voice.AudioLoadExecutor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class LazoDiscsLifecycleEvents {
    private LazoDiscsLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        // This fires for the integrated singleplayer server too.
        LazoDiscsServerBootstrap.loadPlasmoAddon();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // Allows starting another singleplayer world in the same client session.
        LazoDiscsServerBootstrap.resetForIntegratedServer();
    }
}
