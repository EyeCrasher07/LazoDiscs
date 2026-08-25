package com.eyecrasher.lazodiscs;

import com.eyecrasher.lazodiscs.command.LazoDiscsCommands;
import com.eyecrasher.lazodiscs.config.LazoDiscsConfig;
import com.eyecrasher.lazodiscs.event.JukeboxEvents;
import com.eyecrasher.lazodiscs.event.LazoDiscsLifecycleEvents;
import com.eyecrasher.lazodiscs.server.JukeboxPlaybackManager;
import com.eyecrasher.lazodiscs.text.LazoDiscsText;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LazoDiscs implements ModInitializer {
    public static final String MOD_ID = "lazodiscs";
    public static final Logger LOGGER = LoggerFactory.getLogger("LazoDiscs");

    @Override
    public void onInitialize() {
        LazoDiscsConfig.load();
        LazoDiscsText.reload();

        LazoDiscsCommands.register();
        JukeboxEvents.register();
        LazoDiscsLifecycleEvents.register();

        // Dedicated servers load here; integrated singleplayer servers also retry from lifecycle events.
        LazoDiscsServerBootstrap.loadPlasmoAddon();

        LOGGER.info("LazoDiscs initialized");
    }

    public static JukeboxPlaybackManager playback() {
        return JukeboxPlaybackManager.INSTANCE;
    }
}
