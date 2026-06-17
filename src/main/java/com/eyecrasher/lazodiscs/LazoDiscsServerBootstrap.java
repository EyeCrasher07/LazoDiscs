package com.eyecrasher.lazodiscs;

import com.eyecrasher.lazodiscs.voice.LazoDiscsVoiceAddon;
import su.plo.voice.api.server.PlasmoVoiceServer;

public final class LazoDiscsServerBootstrap {
    private LazoDiscsServerBootstrap() {
    }

    public static void loadPlasmoAddon() {
        PlasmoVoiceServer.getAddonsLoader().load(new LazoDiscsVoiceAddon());
    }
}
