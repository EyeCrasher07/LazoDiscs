package com.eyecrasher.lazodiscs.voice;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.config.LazoDiscsConfig;
import com.eyecrasher.lazodiscs.data.CustomDiscData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * In-memory decoded PCM cache.
 *
 * LazoDiscs needs full PCM samples before Plasmo Voice's ArrayAudioFrameProvider starts.
 * Preloading/caching after /lazodiscs burn makes the later jukebox insert start much faster.
 *
 * This cache is RAM-only. It never writes decoded audio to disk.
 */
public final class AudioCache {
    private static final Object LOCK = new Object();
    private static final LinkedHashMap<String, short[]> CACHE = new LinkedHashMap<>(64, 0.75F, true);
    private static final LinkedHashMap<String, List<CacheCallback>> LOADING = new LinkedHashMap<>();

    private AudioCache() {
    }

    public static Optional<short[]> get(CustomDiscData disc) {
        if (disc == null) return Optional.empty();
        String key = key(disc);
        synchronized (LOCK) {
            return Optional.ofNullable(CACHE.get(key));
        }
    }

    public static boolean isCached(CustomDiscData disc) {
        if (disc == null) return false;
        synchronized (LOCK) {
            return CACHE.containsKey(key(disc));
        }
    }

    public static boolean isLoading(CustomDiscData disc) {
        if (disc == null) return false;
        synchronized (LOCK) {
            return LOADING.containsKey(key(disc));
        }
    }

    public static void put(CustomDiscData disc, short[] samples) {
        if (disc == null || samples == null || samples.length == 0) return;
        synchronized (LOCK) {
            putUnlocked(disc, samples);
        }
    }

    /**
     * Returns true when samples were already cached and onReady was called immediately.
     * Returns false when audio is loading in the background.
     *
     * If the same disc URL is already being preloaded, this method attaches the caller
     * to the existing loader instead of starting a second YouTube/LavaPlayer request.
     */
    public static boolean getOrLoad(CustomDiscData disc, Consumer<short[]> onReady, Runnable onFailure) {
        if (disc == null) {
            safeFailure(onFailure);
            return false;
        }

        String key = key(disc);
        short[] cached;
        boolean shouldStartLoader = false;

        synchronized (LOCK) {
            cached = CACHE.get(key);
            if (cached != null) {
                // access-order LinkedHashMap already moves this entry to most-recent
            } else {
                List<CacheCallback> callbacks = LOADING.get(key);
                if (callbacks == null) {
                    callbacks = new ArrayList<>();
                    LOADING.put(key, callbacks);
                    shouldStartLoader = true;
                }
                callbacks.add(new CacheCallback(onReady, onFailure));
            }
        }

        if (cached != null) {
            safeReady(onReady, cached);
            return true;
        }

        if (shouldStartLoader) {
            startLoader(disc, key);
        }

        return false;
    }

    public static void preload(CustomDiscData disc) {
        if (disc == null || !LazoDiscsConfig.PRELOAD_ON_BURN.get()) return;

        if (isCached(disc)) {
            LazoDiscs.LOGGER.debug("LazoDisc '{}' is already in RAM cache", disc.title());
            return;
        }

        if (isLoading(disc)) {
            LazoDiscs.LOGGER.debug("LazoDisc '{}' is already preloading into RAM cache", disc.title());
            return;
        }

        LazoDiscs.LOGGER.info("Preloading LazoDisc '{}' into RAM cache", disc.title());
        getOrLoad(
                disc,
                samples -> LazoDiscs.LOGGER.info("Preloaded LazoDisc '{}' into RAM cache ({} samples)", disc.title(), samples.length),
                () -> LazoDiscs.LOGGER.debug("LazoDisc preload failed for '{}'", disc.title())
        );
    }

    private static void startLoader(CustomDiscData disc, String key) {
        try {
            Consumer<short[]> onReady = samples -> finishSuccess(disc, key, samples);
            Runnable onFailure = () -> finishFailure(key);

            if (LavaPcmFeeder.shouldUse(disc.url())) {
                new LavaPcmFeeder(disc.url(), disc.title(), disc.volume(), onReady, onFailure).start();
            } else {
                new HttpPcmFeeder(disc.url(), disc.volume(), onReady, onFailure).start();
            }
        } catch (Throwable t) {
            LazoDiscs.LOGGER.debug("Could not start LazoDisc RAM preload/load for '{}': {}", disc.title(), t.toString());
            finishFailure(key);
        }
    }

    private static void finishSuccess(CustomDiscData disc, String key, short[] samples) {
        List<CacheCallback> callbacks;
        synchronized (LOCK) {
            if (samples != null && samples.length > 0) {
                putUnlocked(disc, samples);
            }
            callbacks = LOADING.remove(key);
        }

        if (callbacks == null || callbacks.isEmpty()) return;
        if (samples == null || samples.length == 0) {
            for (CacheCallback callback : callbacks) safeFailure(callback.onFailure());
            return;
        }

        for (CacheCallback callback : callbacks) {
            safeReady(callback.onReady(), samples);
        }
    }

    private static void finishFailure(String key) {
        List<CacheCallback> callbacks;
        synchronized (LOCK) {
            callbacks = LOADING.remove(key);
        }

        if (callbacks == null) return;
        for (CacheCallback callback : callbacks) {
            safeFailure(callback.onFailure());
        }
    }

    private static void putUnlocked(CustomDiscData disc, short[] samples) {
        int maxTracks = LazoDiscsConfig.MAX_CACHED_TRACKS.get();
        if (maxTracks <= 0) return;

        CACHE.put(key(disc), samples);
        while (CACHE.size() > maxTracks) {
            String eldest = CACHE.keySet().iterator().next();
            CACHE.remove(eldest);
        }
    }

    private static void safeReady(Consumer<short[]> callback, short[] samples) {
        if (callback == null) return;
        try {
            callback.accept(samples);
        } catch (Throwable t) {
            LazoDiscs.LOGGER.debug("LazoDisc RAM cache callback failed: {}", t.toString());
        }
    }

    private static void safeFailure(Runnable callback) {
        if (callback == null) return;
        try {
            callback.run();
        } catch (Throwable t) {
            LazoDiscs.LOGGER.debug("LazoDisc RAM cache failure callback failed: {}", t.toString());
        }
    }

    private static String key(CustomDiscData disc) {
        return disc.url() + "\u0000" + Float.toString(disc.volume());
    }

    private record CacheCallback(Consumer<short[]> onReady, Runnable onFailure) {
    }
}
