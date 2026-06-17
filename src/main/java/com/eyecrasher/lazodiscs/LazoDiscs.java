package com.eyecrasher.lazodiscs;

import com.eyecrasher.lazodiscs.command.LazoDiscsCommands;
import com.eyecrasher.lazodiscs.config.LazoDiscsConfig;
import com.eyecrasher.lazodiscs.event.JukeboxEvents;
import com.eyecrasher.lazodiscs.event.LazoDiscsLifecycleEvents;
import com.eyecrasher.lazodiscs.server.JukeboxPlaybackManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(LazoDiscs.MOD_ID)
public final class LazoDiscs {
    public static final String MOD_ID = "lazodiscs";
    public static final Logger LOGGER = LoggerFactory.getLogger("LazoDiscs");

    public LazoDiscs(IEventBus modBus) {
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, LazoDiscsConfig.SPEC, "lazodiscs-common.toml");

        NeoForge.EVENT_BUS.register(LazoDiscsCommands.class);
        NeoForge.EVENT_BUS.register(JukeboxEvents.class);
        NeoForge.EVENT_BUS.register(LazoDiscsLifecycleEvents.class);

        // LazoDiscs remains server-side for multiplayer.
        // Dedicated servers load here; integrated singleplayer servers also retry from the lifecycle event.
        LazoDiscsServerBootstrap.loadPlasmoAddon();

        LOGGER.info("LazoDiscs initialized");
    }

    public static JukeboxPlaybackManager playback() {
        return JukeboxPlaybackManager.INSTANCE;
    }
}
