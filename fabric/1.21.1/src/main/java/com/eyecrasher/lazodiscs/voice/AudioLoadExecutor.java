package com.eyecrasher.lazodiscs.voice;

import com.eyecrasher.lazodiscs.config.LazoDiscsConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class AudioLoadExecutor {
    private static final Object LOCK = new Object();
    private static ExecutorService executor;
    private static int configuredThreads;

    private AudioLoadExecutor() {
    }

    public static Future<?> submit(Runnable task) {
        return executor().submit(task);
    }

    public static void shutdownNow() {
        synchronized (LOCK) {
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
                configuredThreads = 0;
            }
        }
    }

    private static ExecutorService executor() {
        int threads = Math.max(1, LazoDiscsConfig.MAX_CONCURRENT_AUDIO_LOADS.get());
        synchronized (LOCK) {
            if (executor == null || configuredThreads != threads) {
                if (executor != null) executor.shutdownNow();
                configuredThreads = threads;
                executor = Executors.newFixedThreadPool(threads, new LoaderThreadFactory());
            }
            return executor;
        }
    }

    private static final class LoaderThreadFactory implements ThreadFactory {
        private final AtomicInteger nextId = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "LazoDiscs-Audio-Loader-" + nextId.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
