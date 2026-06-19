package com.eyecrasher.lazodiscs.voice;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.config.LazoDiscsConfig;
import com.eyecrasher.lazodiscs.text.LazoDiscsText;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
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
    private static final AudioPlayerManager PLAYER_MANAGER = createPlayerManager(StandardAudioDataFormats.DISCORD_PCM_S16_BE, "PCM");
    private static final AudioPlayerManager STREAM_PLAYER_MANAGER = createPlayerManager(StandardAudioDataFormats.DISCORD_OPUS, "streaming");

    private final String rawUrl;
    private final String title;
    private final float volume;
    private final Consumer<short[]> onReady;
    private final Consumer<String> onFailure;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private Future<?> task;
    private volatile AudioPlayer activePlayer;

    public LavaPcmFeeder(String rawUrl, String title, float volume, Consumer<short[]> onReady, Consumer<String> onFailure) {
        this.rawUrl = rawUrl;
        this.title = title == null ? rawUrl : title;
        this.volume = Math.max(0.0F, volume);
        this.onReady = onReady;
        this.onFailure = onFailure;
    }

    public static boolean shouldUse(String rawUrl) {
        if (rawUrl == null) return false;
        String lower = rawUrl.toLowerCase(Locale.ROOT);
        return lower.contains("youtube.com/")
                || lower.contains("youtu.be/")
                || lower.contains("music.youtube.com/")
                || lower.contains("soundcloud.com/")
                || lower.contains("bandcamp.com/")
                || lower.contains("vimeo.com/")
                || lower.contains("twitch.tv/")
                || SpotifyTitleResolver.looksLikeSpotify(rawUrl);
    }

    public void start() {
        task = AudioLoadExecutor.submit(this::run);
    }

    private static AudioPlayerManager createPlayerManager(AudioDataFormat outputFormat, String label) {
        DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
        manager.getConfiguration().setOutputFormat(outputFormat);
        manager.setFrameBufferDuration(500);
        manager.setPlayerCleanupThreshold(30_000L);

        try {
            manager.registerSourceManager(new YoutubeAudioSourceManager());
            LazoDiscs.LOGGER.info("LazoDiscs registered youtube-source for LavaPlayer ({})", label);
        } catch (Throwable t) {
            LazoDiscs.LOGGER.warn("LazoDiscs could not register youtube-source ({}): {}", label, t.toString());
        }

        try {
            AudioSourceManagers.registerRemoteSources(manager);
            AudioSourceManagers.registerLocalSource(manager);
        } catch (Throwable t) {
            LazoDiscs.LOGGER.warn("LazoDiscs could not register default LavaPlayer source managers ({}): {}", label, t.toString());
        }
        return manager;
    }

    public static StreamingPlayback openStream(String rawUrl, String title, float volume) throws InterruptedException {
        ResolveRequest request = resolveIdentifier(rawUrl, title);
        LazoDiscs.LOGGER.info("LazoDiscs resolving streaming audio with LavaPlayer: '{}' -> '{}'", rawUrl, request.identifier());
        AudioTrack track = loadTrack(STREAM_PLAYER_MANAGER, request.identifier(), request.spotifyMetadata());
        validateStreamingTrackLength(track);

        AudioPlayer player = STREAM_PLAYER_MANAGER.createPlayer();
        player.setVolume(Math.max(0, Math.round(Math.max(0.0F, volume) * 100.0F)));
        player.playTrack(track);
        return new StreamingPlayback(player, track);
    }

    public static void checkPlayable(String rawUrl, String title, Consumer<String> onFailure) {
        AudioLoadExecutor.submit(() -> {
            try {
                ResolveRequest request = resolveIdentifier(rawUrl, title);
                AudioTrack track = loadTrack(STREAM_PLAYER_MANAGER, request.identifier(), request.spotifyMetadata());
                validateStreamingTrackLength(track);
            } catch (Throwable t) {
                if (onFailure != null) {
                    try {
                        onFailure.accept(messageOf(t));
                    } catch (Throwable callbackError) {
                        LazoDiscs.LOGGER.debug("LazoDiscs streaming check failure callback failed: {}", callbackError.toString());
                    }
                }
            }
        });
    }


    public static List<SearchResult> searchYoutubeMusic(String query, int maxResults) throws InterruptedException {
        return searchYoutubeMusic(query, maxResults, null);
    }

    public static List<SearchResult> searchYoutubeMusic(String query, int maxResults, SpotifyTitleResolver.SpotifyMetadata spotifyMetadata) throws InterruptedException {
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
            throw new RuntimeException(LazoDiscsText.searchTimedOut(timeout));
        }
        if (failure.get() != null) {
            throw new RuntimeException(messageOf(failure.get()));
        }
        if (spotifyMetadata != null) {
            results.sort((a, b) -> Integer.compare(scoreSearchResult(b, spotifyMetadata), scoreSearchResult(a, spotifyMetadata)));
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

            AudioTrack track = loadTrack(PLAYER_MANAGER, request.identifier(), request.spotifyMetadata());
            if (closed.get()) return;
            if (track == null) {
                fail(LazoDiscsText.audioNoMatches());
                return;
            }
            validatePreloadedTrackLength(track);

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
                    playbackFailure.compareAndSet(null, new RuntimeException(LazoDiscsText.trackStuck(thresholdMs)));
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
                    throw new RuntimeException(LazoDiscsText.audioNoFrames());
                }
                Thread.sleep(10L);
            }

            if (closed.get()) return;
            short[] samples = out.toArray();
            if (samples.length == 0) throw new RuntimeException(LazoDiscsText.audioDecodedZeroSamples());
            LazoDiscs.LOGGER.info("LazoDiscs LavaPlayer decoded {} PCM samples from '{}'", samples.length, rawUrl);
            onReady.accept(samples);
        } catch (Throwable t) {
            if (!closed.get()) {
                LazoDiscs.LOGGER.warn("LazoDiscs LavaPlayer could not load audio '{}': {}", rawUrl, t.toString());
                fail(messageOf(t));
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

    private static AudioTrack loadTrack(AudioPlayerManager manager, String identifier, SpotifyTitleResolver.SpotifyMetadata spotifyMetadata) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AudioTrack> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        manager.loadItemOrdered("lazodiscs-load:" + identifier, identifier, new AudioLoadResultHandler() {
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
                failure.set(new RuntimeException(LazoDiscsText.audioNoMatches()));
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
            throw new RuntimeException(LazoDiscsText.audioResolveTimedOut(timeout));
        }
        if (failure.get() != null) {
            throw new RuntimeException(messageOf(failure.get()));
        }
        return result.get();
    }

    private static void validatePreloadedTrackLength(AudioTrack track) {
        validateTrackLength(track, LazoDiscsConfig.MAX_TRACK_LENGTH_SECONDS.get());
    }

    private static void validateStreamingTrackLength(AudioTrack track) {
        validateTrackLength(track, LazoDiscsConfig.MAX_STREAMING_TRACK_LENGTH_SECONDS.get());
    }

    private static void validateTrackLength(AudioTrack track, int maxSeconds) {
        if (maxSeconds > 0 && track != null && track.getDuration() > 0 && track.getDuration() > maxSeconds * 1000L) {
            throw new IllegalArgumentException(LazoDiscsText.trackTooLong(maxSeconds));
        }
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
        return scoreInfo(info.title, info.author, info.length, metadata);
    }

    private static int scoreSearchResult(SearchResult result, SpotifyTitleResolver.SpotifyMetadata metadata) {
        return scoreInfo(result.title(), result.author(), result.lengthMs(), metadata);
    }

    private static int scoreInfo(String rawTitle, String rawAuthor, long lengthMs, SpotifyTitleResolver.SpotifyMetadata metadata) {
        String hay = normalize(rawTitle + " " + rawAuthor);
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
        if (expectedDuration != null && expectedDuration > 0 && lengthMs > 0) {
            long diff = Math.abs(lengthMs - expectedDuration);
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

    private void fail(String reason) {
        if (onFailure == null) return;
        try {
            onFailure.accept(reason == null || reason.isBlank() ? LazoDiscsText.unknown() : reason);
        } catch (Throwable t) {
            LazoDiscs.LOGGER.debug("LazoDiscs LavaPlayer failure callback failed: {}", t.toString());
        }
    }

    private static String messageOf(Throwable t) {
        if (t == null) return LazoDiscsText.unknown();
        String message = t.getMessage();
        Throwable cause = t.getCause();
        if ((message == null || message.isBlank()) && cause != null) return messageOf(cause);
        if (cause != null && message != null && message.equals(cause.toString())) return messageOf(cause);
        return message == null || message.isBlank() ? t.getClass().getSimpleName() : message;
    }

    private static ResolveRequest resolveIdentifier(String raw, String fallbackTitle) {
        if (SpotifyTitleResolver.looksLikeSpotify(raw)) {
            if (!LazoDiscsConfig.SPOTIFY_SEARCH_VIA_YOUTUBE.get()) {
                throw new IllegalArgumentException(LazoDiscsText.spotifyDisabled());
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

    public record StreamingPlayback(AudioPlayer player, AudioTrack track) implements AutoCloseable {
        @Override
        public void close() {
            try {
                player.stopTrack();
            } catch (Exception ignored) {
            }
            try {
                player.destroy();
            } catch (Exception ignored) {
            }
        }
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
