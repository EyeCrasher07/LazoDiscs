package com.eyecrasher.lazodiscs.event;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.LazoDiscsServerBootstrap;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fmlserverevents.FMLServerAboutToStartEvent;
import net.minecraftforge.fmlserverevents.FMLServerStoppedEvent;

public final class LazoDiscsLifecycleEvents {
    private LazoDiscsLifecycleEvents() {}

    @SubscribeEvent
    public static void onServerAboutToStart(FMLServerAboutToStartEvent event) {
        // This fires for the integrated singleplayer server too.
        LazoDiscs.setCurrentServer(event.getServer());
        LazoDiscsServerBootstrap.loadPlasmoAddon();
    }

    @SubscribeEvent
    public static void onServerStopped(FMLServerStoppedEvent event) {
        // Allows starting another singleplayer world in the same client session.
        LazoDiscsServerBootstrap.resetForIntegratedServer();
        LazoDiscs.setCurrentServer(null);
    }
}
