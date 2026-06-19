package com.eyecrasher.lazodiscs.voice;

import com.eyecrasher.lazodiscs.LazoDiscs;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.server.PlasmoVoiceServer;

@Addon(
        id = "lazodiscs",
        name = "LazoDiscs",
        version = "1.0.3+mc1.21.11",
        authors = {"EyeCrasher"}
)
public final class LazoDiscsVoiceAddon implements AddonInitializer {
    @InjectPlasmoVoice
    private PlasmoVoiceServer voiceServer;

    @Override
    public void onAddonInitialize() {
        PlasmoVoiceBridge.INSTANCE.initialize(voiceServer, this);
        LazoDiscs.LOGGER.info("LazoDiscs Plasmo Voice addon initialized");
    }

    @Override
    public void onAddonShutdown() {
        PlasmoVoiceBridge.INSTANCE.shutdown();
        LazoDiscs.playback().stopAll("pv-addon-shutdown");
        LazoDiscs.LOGGER.info("LazoDiscs Plasmo Voice addon shut down");
    }
}
