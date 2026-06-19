package com.eyecrasher.lazodiscs.voice;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.config.LazoDiscsConfig;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import dev.lavalink.youtube.YoutubeAudioSourceManager;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Built-in resolver/player path powered by LavaPlayer + youtube-source.
 *
 * It preloads the track into 48 kHz mono 16-bit PCM samples because Plasmo Voice's
 * ArrayAudioFrameProvider expects samples to be added before AudioSender starts.
 */
public final class LavaPcmFeeder implements AutoCloseable {
    private static final AudioPlayerManager PLAYER_MANAGER = createPlayerManager();

    private final String rawUrl;
    private final String title;
    private final float volume;
    private final Consumer<short[]> onReady;
    private final Runnable onFailure;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private Future<?> task;
    private volatile AudioPlayer activePlayer;

    public LavaPcmFeeder(String rawUrl, String title, float volume, Consumer<short[]> onReady, Runnable onFailure) {
        this.rawUrl = rawUrl;
        this.title = title == null ? rawUrl : title;
        this.volume = Math.max(0.0F, volume);
        this.onReady = onReady;
        this.onFailure = onFailure;
    }

    public static boolean shouldUse(String rawUrl) {
        String lower = rawUrl.toLowerCase(Locale.ROOT);
        return lower.contains("youtube.com/")
                || lower.contains("youtu.be/")
                || lower.contains("music.youtube.com/")
                || lower.contains("soundcloud.com/")
                || lower.contains("bandcamp.com/")
                || lower.contains("vimeo.com/")
                || lower.contains("twitch.tv/")
                || lower.startsWith("spotify:")
                || lower.contains("open.spotify.com/");
    }

    public void start() {
        task = AudioLoadExecutor.submit(this::run);
    }

    private static AudioPlayerManager createPlayerManager() {
        DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
        manager.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_PCM_S16_BE);
        manager.setFrameBufferDuration(500);
        manager.setPlayerCleanupThreshold(30_000L);

        try {
            manager.registerSourceManager(new YoutubeAudioSourceManager());
            LazoDiscs.LOGGER.info("LazoDiscs registered youtube-source for LavaPlayer");
        } catch (Throwable t) {
            LazoDiscs.LOGGER.warn("LazoDiscs could not register youtube-source: {}", t.toString());
        }

        try {
            AudioSourceManagers.registerRemoteSources(manager);
            AudioSourceManagers.registerLocalSource(manager);
        } catch (Throwable t) {
            LazoDiscs.LOGGER.warn("LazoDiscs could not register default LavaPlayer source managers: {}", t.toString());
        }
        return manager;
    }


    public static List<SearchResult> searchYoutubeMusic(String query, int maxResults) throws InterruptedException {
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.isBlank()) return List.of();

        CountDownLatch latch = new CountDownLatch(1);
        List<SearchResult> results = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        String identifier = "ytmsearch:" + cleanQuery;

        PLAYER_MANAGER.loadItemOrdered("lazodiscs-search:" + cleanQuery, identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                addSearchResult(results, track);
                latch.countDown();
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                for (AudioTrack track : playlist.getTracks()) {
                    addSearchResult(results, track);
                    if (results.size() >= maxResults) break;
                }
                latch.countDown();
            }

            @Override
            public void noMatches() {
                latch.countDown();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                failure.set(exception);
                latch.countDown();
            }
        });

        int timeout = LazoDiscsConfig.LAVAPLAYER_LOAD_TIMEOUT_SECONDS.get();
        if (!latch.await(timeout, TimeUnit.SECONDS)) {
            throw new RuntimeException("Search timed out after " + timeout + " seconds");
        }
        if (failure.get() != null) {
            throw new RuntimeException(failure.get());
        }
        return List.copyOf(results);
    }

    private static void addSearchResult(List<SearchResult> results, AudioTrack track) {
        if (track == null || track.getInfo() == null) return;
        AudioTrackInfo info = track.getInfo();
        String url = info.uri;
        if (url == null || url.isBlank()) {
            url = info.identifier == null ? "" : info.identifier;
        }
        if (url.isBlank()) return;
        results.add(new SearchResult(
                nullToUnknown(info.title),
                nullToUnknown(info.author),
                url,
                info.length
        ));
    }

    private static String nullToUnknown(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }

    private void run() {
        AudioPlayer player = null;
        try {
            ResolveRequest request = resolveIdentifier(rawUrl, title);
            LazoDiscs.LOGGER.info("LazoDiscs resolving audio with LavaPlayer: '{}' -> '{}'", rawUrl, request.identifier());
            if (request.spotifyMetadata() != null) {
                LazoDiscs.LOGGER.info("LazoDiscs Spotify match hints: title='{}', artist='{}', duration={}ms",
                        request.spotifyMetadata().title(), request.spotifyMetadata().primaryArtist(), request.spotifyMetadata().durationMs());
            }

            AudioTrack track = loadTrack(request.identifier(), request.spotifyMetadata());
            if (closed.get()) return;
            if (track == null) {
                onFailure.run();
                return;
            }

            int maxSeconds = LazoDiscsConfig.MAX_TRACK_LENGTH_SECONDS.get();
            if (maxSeconds > 0 && track.getDuration() > 0 && track.getDuration() > maxSeconds * 1000L) {
                throw new IllegalArgumentException("Track is longer than maxTrackLengthSeconds: " + (track.getDuration() / 1000L) + "s");
            }

            player = PLAYER_MANAGER.createPlayer();
            activePlayer = player;
            AtomicBoolean ended = new AtomicBoolean(false);
            AtomicReference<Throwable> playbackFailure = new AtomicReference<>();
            player.addListener(new AudioEventAdapter() {
                @Override
                public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
                    ended.set(true);
                }

                @Override
                public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
                    playbackFailure.compareAndSet(null, exception);
                    ended.set(true);
                }

                @Override
                public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
                    playbackFailure.compareAndSet(null, new RuntimeException("Track stuck for " + thresholdMs + "ms"));
                    ended.set(true);
                }
            });

            ShortBuilder out = new ShortBuilder(48_000 * 30);
            MutableAudioFrame frame = new MutableAudioFrame();
            ByteBuffer buffer = ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_PCM_S16_BE.maximumChunkSize());
            frame.setBuffer(buffer);

            player.playTrack(track);
            long lastFrameAt = System.currentTimeMillis();
            while (!closed.get()) {
                buffer.clear();
                boolean provided = player.provide(frame);
                if (provided) {
                    buffer.flip();
                    consumePcmFrame(buffer, out);
                    lastFrameAt = System.currentTimeMillis();
                    continue;
                }

                Throwable failure = playbackFailure.get();
                if (failure != null) throw failure;
                if (ended.get() || player.getPlayingTrack() == null) break;
                if (System.currentTimeMillis() - lastFrameAt > 15_000L) {
                    throw new RuntimeException("No audio frames received from LavaPlayer for 15 seconds");
                }
                Thread.sleep(10L);
            }

            if (closed.get()) return;
            short[] samples = out.toArray();
            if (samples.length == 0) throw new RuntimeException("LavaPlayer decoded zero PCM samples");
            LazoDiscs.LOGGER.info("LazoDiscs LavaPlayer decoded {} PCM samples from '{}'", samples.length, rawUrl);
            onReady.accept(samples);
        } catch (Throwable t) {
            if (!closed.get()) {
                LazoDiscs.LOGGER.warn("LazoDiscs LavaPlayer could not load audio '{}': {}", rawUrl, t.toString());
                onFailure.run();
            }
        } finally {
            if (player != null) {
                try {
                    player.destroy();
                } catch (Exception ignored) {
                }
            }
            activePlayer = null;
        }
    }

    private AudioTrack loadTrack(String identifier, SpotifyTitleResolver.SpotifyMetadata spotifyMetadata) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AudioTrack> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        PLAYER_MANAGER.loadItemOrdered(this, identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                result.set(track);
                latch.countDown();
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.getSelectedTrack() != null) {
                    result.set(playlist.getSelectedTrack());
                } else if (!playlist.getTracks().isEmpty()) {
                    result.set(selectBestTrack(playlist.getTracks(), spotifyMetadata));
                }
                latch.countDown();
            }

            @Override
            public void noMatches() {
                failure.set(new RuntimeException("No matches"));
                latch.countDown();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                failure.set(exception);
                latch.countDown();
            }
        });

        int timeout = LazoDiscsConfig.LAVAPLAYER_LOAD_TIMEOUT_SECONDS.get();
        if (!latch.await(timeout, TimeUnit.SECONDS)) {
            throw new RuntimeException("Audio resolve timed out after " + timeout + " seconds");
        }
        if (failure.get() != null) {
            throw new RuntimeException(failure.get());
        }
        return result.get();
    }

    private static AudioTrack selectBestTrack(List<AudioTrack> tracks, SpotifyTitleResolver.SpotifyMetadata metadata) {
        if (tracks.isEmpty()) return null;
        if (metadata == null) return tracks.get(0);

        AudioTrack best = tracks.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (AudioTrack track : tracks) {
            int score = scoreTrack(track, metadata);
            AudioTrackInfo info = track.getInfo();
            LazoDiscs.LOGGER.info("LazoDiscs Spotify candidate score {}: '{}' by '{}' ({} ms)",
                    score, info.title, info.author, info.length);
            if (score > bestScore) {
                bestScore = score;
                best = track;
            }
        }
        AudioTrackInfo info = best.getInfo();
        LazoDiscs.LOGGER.info("LazoDiscs selected Spotify candidate: '{}' by '{}' with score {}", info.title, info.author, bestScore);
        return best;
    }

    private static int scoreTrack(AudioTrack track, SpotifyTitleResolver.SpotifyMetadata metadata) {
        AudioTrackInfo info = track.getInfo();
        String hay = normalize(info.title + " " + info.author);
        String titleNorm = normalize(metadata.title());
        int score = 0;

        if (!titleNorm.isBlank()) {
            if (hay.contains(titleNorm)) score += 120;
            List<String> words = meaningfulWords(titleNorm);
            for (String word : words) {
                if (hay.contains(word)) score += 16;
                else score -= 18;
            }
        }

        for (String artist : metadata.artists()) {
            String artistNorm = normalize(artist);
            if (artistNorm.isBlank()) continue;
            if (hay.contains(artistNorm)) score += 95;
            for (String word : meaningfulWords(artistNorm)) {
                if (hay.contains(word)) score += 12;
                else score -= 10;
            }
        }

        Long expectedDuration = metadata.durationMs();
        if (expectedDuration != null && expectedDuration > 0 && info.length > 0) {
            long diff = Math.abs(info.length - expectedDuration);
            if (diff <= 3000) score += 110;
            else if (diff <= 10_000) score += 80;
            else if (diff <= 25_000) score += 35;
            else score -= (int) Math.min(120, diff / 1000L);
        }

        String bad = hay;
        List<String> penalties = List.of("cover", "remix", "sped up", "slowed", "nightcore", "karaoke", "instrumental", "8d", "loop", "extended", "live", "reaction");
        String expectedTitle = titleNorm;
        for (String penalty : penalties) {
            if (bad.contains(penalty) && !expectedTitle.contains(penalty)) score -= 45;
        }

        if (bad.contains("official audio") || bad.contains("topic") || bad.contains("provided to youtube")) score += 15;
        if (bad.contains("lyrics") && !expectedTitle.contains("lyrics")) score -= 10;
        return score;
    }

    private static List<String> meaningfulWords(String normalized) {
        if (normalized == null || normalized.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String word : normalized.split(" ")) {
            if (word.length() < 3) continue;
            if (word.equals("the") || word.equals("and") || word.equals("feat") || word.equals("ft") || word.equals("official") || word.equals("audio")) continue;
            out.add(word);
        }
        return out;
    }

    private static ResolveRequest resolveIdentifier(String raw, String fallbackTitle) {
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("spotify:") || lower.contains("open.spotify.com/")) {
            if (!LazoDiscsConfig.SPOTIFY_SEARCH_VIA_YOUTUBE.get()) {
                throw new IllegalArgumentException("Spotify search is disabled in config");
            }
            SpotifyTitleResolver.SpotifyMetadata metadata = SpotifyTitleResolver.resolveMetadata(raw).orElse(null);
            String query;
            if (metadata != null && !metadata.searchQuery().isBlank()) {
                query = metadata.searchQuery();
            } else {
                query = Optional.ofNullable(fallbackTitle).filter(s -> !s.isBlank() && !s.equals(raw)).orElse(raw);
            }
            return new ResolveRequest("ytmsearch:" + query, metadata);
        }

        // Helpful shortcut: allow people to burn plain search text later if needed.
        try {
            URI uri = URI.create(raw);
            if (uri.getScheme() == null || uri.getScheme().isBlank()) return new ResolveRequest("ytmsearch:" + raw, null);
        } catch (Exception ignored) {
            return new ResolveRequest("ytmsearch:" + raw, null);
        }
        return new ResolveRequest(raw, null);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("\\([^)]*\\)", " ")
                .replaceAll("\\[[^]]*]", " ")
                .replaceAll("[^\\p{L}\\p{Nd}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void consumePcmFrame(ByteBuffer buffer, ShortBuilder out) {
        // StandardAudioDataFormats.DISCORD_PCM_S16_BE is 48 kHz, stereo, signed 16-bit, big-endian.
        while (buffer.remaining() >= 4) {
            int left = readSigned16BE(buffer);
            int right = readSigned16BE(buffer);
            out.add(scale((left + right) / 2));
        }
    }

    private static int readSigned16BE(ByteBuffer buffer) {
        int hi = buffer.get();
        int lo = buffer.get() & 0xFF;
        return (short) ((hi << 8) | lo);
    }

    private short scale(int value) {
        int scaled = Math.round(value * volume);
        if (scaled > Short.MAX_VALUE) scaled = Short.MAX_VALUE;
        if (scaled < Short.MIN_VALUE) scaled = Short.MIN_VALUE;
        return (short) scaled;
    }

    @Override
    public void close() {
        closed.set(true);
        AudioPlayer player = activePlayer;
        if (player != null) {
            try {
                player.stopTrack();
                player.destroy();
            } catch (Exception ignored) {
            }
        }
        if (task != null) task.cancel(true);
    }

    public record SearchResult(String title, String author, String url, long lengthMs) {
    }

    private record ResolveRequest(String identifier, SpotifyTitleResolver.SpotifyMetadata spotifyMetadata) {
    }

    private static final class ShortBuilder {
        private short[] data;
        private int size;

        private ShortBuilder(int initialCapacity) {
            this.data = new short[Math.max(1024, initialCapacity)];
        }

        private void add(short value) {
            if (size >= data.length) data = Arrays.copyOf(data, data.length * 2);
            data[size++] = value;
        }

        private short[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }
}
