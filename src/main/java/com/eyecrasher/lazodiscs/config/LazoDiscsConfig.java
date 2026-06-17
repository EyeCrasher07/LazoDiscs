package com.eyecrasher.lazodiscs.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class LazoDiscsConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue DEFAULT_RANGE;
    public static final ModConfigSpec.IntValue MAX_RANGE;
    public static final ModConfigSpec.DoubleValue DEFAULT_VOLUME;
    public static final ModConfigSpec.DoubleValue SOURCE_LINE_DEFAULT_VOLUME;
    public static final ModConfigSpec.ConfigValue<String> SOURCE_LINE_NAME;
    public static final ModConfigSpec.ConfigValue<String> NOW_PLAYING_MESSAGE;
    public static final ModConfigSpec.BooleanValue ALLOW_HTTP;
    public static final ModConfigSpec.BooleanValue ALLOW_HTTPS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_DOMAINS;
    public static final ModConfigSpec.BooleanValue REQUIRE_PERMISSION_FOR_BURN_COMMAND;
    public static final ModConfigSpec.IntValue BURN_PERMISSION_LEVEL;
    public static final ModConfigSpec.IntValue LAVAPLAYER_LOAD_TIMEOUT_SECONDS;
    public static final ModConfigSpec.IntValue MAX_TRACK_LENGTH_SECONDS;
    public static final ModConfigSpec.BooleanValue SPOTIFY_SEARCH_VIA_YOUTUBE;
    public static final ModConfigSpec.BooleanValue PRELOAD_ON_BURN;
    public static final ModConfigSpec.IntValue MAX_CACHED_TRACKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("playback");
        DEFAULT_RANGE = builder.comment("Default Plasmo Voice audible range for burned discs.")
                .defineInRange("defaultRange", 64, 1, 512);
        MAX_RANGE = builder.comment("Maximum range players can put on a disc.")
                .defineInRange("maxRange", 128, 1, 1024);
        DEFAULT_VOLUME = builder.comment("Default per-disc volume multiplier stored on burned discs.")
                .defineInRange("defaultVolume", 1.0D, 0.0D, 4.0D);
        SOURCE_LINE_DEFAULT_VOLUME = builder.comment("Default client volume for the separate Plasmo Voice source line named 'Discs'. Players can change it in Plasmo Voice settings.")
                .defineInRange("sourceLineDefaultVolume", 1.0D, 0.0D, 1.0D);
        MAX_TRACK_LENGTH_SECONDS = builder.comment("Safety limit for preloaded tracks. 0 disables the limit. Long tracks use more RAM because Plasmo ArrayAudioFrameProvider needs samples before start.")
                .defineInRange("maxTrackLengthSeconds", 900, 0, 24 * 60 * 60);
        builder.pop();

        builder.push("display");
        SOURCE_LINE_NAME = builder.comment("Name shown in the Plasmo Voice source list. LazoDiscs is server-side only, so put your own language text here.")
                .define("sourceLineName", "Пластинки");
        NOW_PLAYING_MESSAGE = builder.comment("Action-bar message shown when a burned LazoDisc starts. Use %title%. Empty value uses vanilla Minecraft record.nowPlaying translation.")
                .define("nowPlayingMessage", "Сейчас играет: %title%");
        builder.pop();

        builder.push("lavaplayer");
        LAVAPLAYER_LOAD_TIMEOUT_SECONDS = builder.comment("Maximum time to wait while LavaPlayer resolves YouTube/SoundCloud/Spotify-search/direct URLs.")
                .defineInRange("loadTimeoutSeconds", 60, 5, 300);
        SPOTIFY_SEARCH_VIA_YOUTUBE = builder.comment("Spotify links are not direct audio streams. When enabled, LazoDiscs uses Spotify metadata/title as a YouTube Music search query.")
                .define("spotifySearchViaYoutube", true);
        PRELOAD_ON_BURN = builder.comment("Start resolving/decoding audio right after /lazodiscs burn, so inserting the disc later starts faster.")
                .define("preloadOnBurn", true);
        MAX_CACHED_TRACKS = builder.comment("Maximum number of decoded tracks kept in RAM for fast jukebox start. 0 disables the cache. This is RAM-only; decoded audio is not saved to disk.")
                .defineInRange("maxCachedTracks", 64, 0, 256);
        builder.pop();

        builder.push("security");
        ALLOW_HTTP = builder.comment("Allow http:// URLs. Disable this if you only want HTTPS.")
                .define("allowHttp", false);
        ALLOW_HTTPS = builder.comment("Allow https:// URLs.")
                .define("allowHttps", true);
        ALLOWED_DOMAINS = builder.comment("Optional domain allow-list. Empty list means any domain is allowed. For YouTube/SoundCloud/Spotify, include their domains here if you enable a whitelist.")
                .defineList("allowedDomains", List.of(), o -> o instanceof String);
        REQUIRE_PERMISSION_FOR_BURN_COMMAND = builder.comment("If true, /lazodiscs burn requires operator permission.")
                .define("requirePermissionForBurnCommand", true);
        BURN_PERMISSION_LEVEL = builder.comment("Permission level required for /lazodiscs burn when requirePermissionForBurnCommand is true.")
                .defineInRange("burnPermissionLevel", 2, 0, 4);
        builder.pop();

        SPEC = builder.build();
    }

    private LazoDiscsConfig() {
    }
}
