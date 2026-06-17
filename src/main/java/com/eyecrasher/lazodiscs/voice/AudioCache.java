package com.eyecrasher.lazodiscs.voice;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.config.LazoDiscsConfig;
import com.eyecrasher.lazodiscs.data.CustomDiscData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-memory decoded PCM cache.
 *
 * LazoDiscs needs full PCM samples before Plasmo Voice's ArrayAudioFrameProvider starts.
 * Preloading/caching after /lazodiscs burn makes the later jukebox insert start much faster.
 */
public final class AudioCache {
    private static final Object LOCK = new Object();
    private static final LinkedHashMap<String, short[]> CACHE = new LinkedHashMap<>(16, 0.75F, true);
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();

    private AudioCache() {
    }

    public static Optional<short[]> get(CustomDiscData disc) {
        String key = key(disc);
        synchronized (LOCK) {
            return Optional.ofNullable(CACHE.get(key));
        }
    }

    public static void put(CustomDiscData disc, short[] samples) {
        if (disc == null || samples == null || samples.length == 0) return;
        int maxTracks = LazoDiscsConfig.MAX_CACHED_TRACKS.get();
        if (maxTracks <= 0) return;
        String key = key(disc);
        synchronized (LOCK) {
            CACHE.put(key, samples);
            while (CACHE.size() > maxTracks) {
                String eldest = CACHE.keySet().iterator().next();
                CACHE.remove(eldest);
            }
        }
    }

    public static void preload(CustomDiscData disc) {
        if (disc == null || !LazoDiscsConfig.PRELOAD_ON_BURN.get()) return;
        if (LazoDiscsConfig.MAX_CACHED_TRACKS.get() <= 0) return;
        String key = key(disc);
        synchronized (LOCK) {
            if (CACHE.containsKey(key)) return;
        }
        if (!LOADING.add(key)) return;

        LazoDiscs.LOGGER.info("Preloading LazoDisc audio cache for '{}'", disc.title());
        Runnable onFailure = () -> {
            LOADING.remove(key);
            LazoDiscs.LOGGER.debug("LazoDisc preload failed for '{}'", disc.title());
        };
        java.util.function.Consumer<short[]> onReady = samples -> {
            try {
                put(disc, samples);
                LazoDiscs.LOGGER.info("Preloaded LazoDisc '{}' into audio cache ({} samples)", disc.title(), samples.length);
            } finally {
                LOADING.remove(key);
            }
        };

        try {
            if (LavaPcmFeeder.shouldUse(disc.url())) {
                new LavaPcmFeeder(disc.url(), disc.title(), disc.volume(), onReady, onFailure).start();
            } else {
                new HttpPcmFeeder(disc.url(), disc.volume(), onReady, onFailure).start();
            }
        } catch (Throwable t) {
            LOADING.remove(key);
            LazoDiscs.LOGGER.debug("Could not start LazoDisc preload for '{}': {}", disc.title(), t.toString());
        }
    }

    private static String key(CustomDiscData disc) {
        return disc.url() + "\u0000" + Float.toString(disc.volume());
    }
}
